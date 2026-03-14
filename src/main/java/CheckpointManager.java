import java.io.*;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 高性能检查点管理器
 *
 * 核心优化点：
 * 1. 二进制序列化 - 比 Properties 文本格式更高效
 * 2. GZIP 压缩 - 减少磁盘占用（大整数压缩率高）
 * 3. 原子写入 - 使用临时文件避免损坏
 * 4. 快速加载 - 直接读取二进制数据
 * 5. 频率控制 - 智能控制检查点保存频率
 *
 * 文件格式：
 * [magic: 4 bytes][version: 4 bytes][iteration: 4 bytes]
 * [P_length: 4 bytes][P_bytes: variable]
 * [Q_length: 4 bytes][Q_bytes: variable]
 * [T_length: 4 bytes][T_bytes: variable]
 * [checksum: 4 bytes]
 */
public class CheckpointManager {
    
    // ==================== 常量配置 ====================
    
    /** 检查点文件路径 */
    private static final String CHECKPOINT_FILE = "checkpoint.dat";
    
    /** 临时文件路径（用于原子写入） */
    private static final String CHECKPOINT_TEMP_FILE = "checkpoint.dat.tmp";
    
    /** 文件魔数 */
    private static final int MAGIC = 0x50494350;  // "PICP" - Pi CheckPoint
    
    /** 文件格式版本 */
    private static final int VERSION = 1;
    
    /** 检查点日志输出间隔 */
    private static final int CHECKPOINT_LOG_INTERVAL = 100;
    
    // ==================== 状态变量 ====================
    
    private static final AtomicInteger checkpointCounter = new AtomicInteger(0);
    private static volatile long lastCheckpointTime = 0;
    
    // ==================== 保存检查点 ====================
    
    /**
     * 保存检查点（默认不输出日志）
     */
    public static void saveCheckpoint(int iteration, Result result) {
        saveCheckpoint(iteration, result, false);
    }
    
    /**
     * 保存检查点
     * 
     * @param iteration 当前迭代次数
     * @param result 计算结果
     * @param forceLog 是否强制输出日志
     */
    public static void saveCheckpoint(int iteration, Result result, boolean forceLog) {
        long start = System.nanoTime();
        
        try {
            // 使用临时文件进行原子写入
            File tempFile = new File(CHECKPOINT_TEMP_FILE);
            File targetFile = new File(CHECKPOINT_FILE);
            
            try (DataOutputStream out = new DataOutputStream(
                    new GZIPOutputStream(new FileOutputStream(tempFile)))) {
                
                // 写入文件头
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeInt(iteration);
                
                // 写入 P, Q, T
                writeBigInteger(out, result.P);
                writeBigInteger(out, result.Q);
                writeBigInteger(out, result.T);
                
                // 写入时间戳
                out.writeLong(System.currentTimeMillis());
                
                // 计算并写入简单校验和
                int checksum = computeChecksum(iteration, result);
                out.writeInt(checksum);
            }
            
            // 原子替换：先删除目标文件，再重命名临时文件
            if (targetFile.exists()) {
                targetFile.delete();
            }
            if (!tempFile.renameTo(targetFile)) {
                // 重命名失败，尝试复制
                try (InputStream in = new FileInputStream(tempFile);
                     OutputStream out = new FileOutputStream(targetFile)) {
                    in.transferTo(out);
                }
                tempFile.delete();
            }
            
            long duration = System.nanoTime() - start;
            lastCheckpointTime = System.currentTimeMillis();
            
            // 控制日志输出频率
            int count = checkpointCounter.incrementAndGet();
            if (forceLog || count % CHECKPOINT_LOG_INTERVAL == 0) {
                long size = targetFile.length();
                System.out.printf("      [检查点] 迭代 %,d, 大小 %,d bytes, 耗时 %.2f ms%n",
                    iteration, size, duration / 1_000_000.0);
            }
            
        } catch (IOException e) {
            System.err.println("[检查点] 保存失败：" + e.getMessage());
            // 清理临时文件
            new File(CHECKPOINT_TEMP_FILE).delete();
        }
    }
    
    /**
     * 写入 BigInteger（二进制格式）
     */
    private static void writeBigInteger(DataOutputStream out, BigInteger value) throws IOException {
        byte[] bytes = value.toByteArray();
        out.writeInt(bytes.length);
        out.write(bytes);
    }
    
    /**
     * 读取 BigInteger（二进制格式）
     */
    private static BigInteger readBigInteger(DataInputStream in) throws IOException {
        int length = in.readInt();
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new BigInteger(bytes);
    }
    
    /**
     * 计算校验和
     */
    private static int computeChecksum(int iteration, Result result) {
        int hash = 17;
        hash = 31 * hash + iteration;
        hash = 31 * hash + result.P.hashCode();
        hash = 31 * hash + result.Q.hashCode();
        hash = 31 * hash + result.T.hashCode();
        return hash;
    }
    
    // ==================== 加载检查点 ====================
    
    /**
     * 加载检查点
     * 
     * @return 检查点数据，如果不存在或无效则返回 null
     */
    public static CheckpointData loadCheckpoint() {
        File checkpointFile = new File(CHECKPOINT_FILE);
        if (!checkpointFile.exists()) {
            return null;
        }
        
        try (DataInputStream in = new DataInputStream(
                new GZIPInputStream(new FileInputStream(checkpointFile)))) {
            
            // 验证魔数
            int magic = in.readInt();
            if (magic != MAGIC) {
                System.err.println("[检查点] 魔数不匹配，文件可能已损坏");
                return null;
            }
            
            // 验证版本
            int version = in.readInt();
            if (version != VERSION) {
                System.err.println("[检查点] 版本不匹配 (期望 " + VERSION + ", 实际 " + version + ")");
                return null;
            }
            
            // 读取数据
            int iteration = in.readInt();
            BigInteger p = readBigInteger(in);
            BigInteger q = readBigInteger(in);
            BigInteger t = readBigInteger(in);
            
            // 读取时间戳（可选）
            long timestamp = 0;
            try {
                timestamp = in.readLong();
            } catch (EOFException e) {
                // 旧格式可能没有时间戳
            }
            
            // 验证校验和（如果存在）
            try {
                int expectedChecksum = in.readInt();
                int actualChecksum = computeChecksum(iteration, new Result(p, q, t));
                if (expectedChecksum != actualChecksum) {
                    System.err.println("[检查点] 校验和不匹配，文件可能已损坏");
                    return null;
                }
            } catch (EOFException e) {
                // 旧格式可能没有校验和
            }
            
            System.out.printf("[检查点] 已加载，迭代 %,d, 时间 %s%n",
                iteration, new java.util.Date(timestamp));
            
            return new CheckpointData(iteration, new Result(p, q, t));
            
        } catch (IOException e) {
            System.err.println("[检查点] 加载失败：" + e.getMessage());
            return null;
        } catch (Exception e) {
            System.err.println("[检查点] 解析失败：" + e.getMessage());
            return null;
        }
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 删除检查点文件
     */
    public static void removeCheckpoint() {
        File checkpointFile = new File(CHECKPOINT_FILE);
        if (checkpointFile.exists()) {
            checkpointFile.delete();
            System.out.println("[检查点] 已删除");
        }
        File tempFile = new File(CHECKPOINT_TEMP_FILE);
        if (tempFile.exists()) {
            tempFile.delete();
        }
    }
    
    /**
     * 重置检查点计数器
     */
    public static void resetCounter() {
        checkpointCounter.set(0);
        lastCheckpointTime = 0;
    }
    
    /**
     * 获取检查点文件路径
     */
    public static String getCheckpointFile() {
        return CHECKPOINT_FILE;
    }
    
    /**
     * 检查是否存在检查点
     */
    public static boolean hasCheckpoint() {
        return new File(CHECKPOINT_FILE).exists();
    }
    
    /**
     * 获取上次检查点时间
     */
    public static long getLastCheckpointTime() {
        return lastCheckpointTime;
    }
    
    /**
     * 获取检查点计数器值
     */
    public static int getCheckpointCount() {
        return checkpointCounter.get();
    }
    
    // ==================== 检查点数据类 ====================
    
    /**
     * 检查点数据封装类
     */
    public static class CheckpointData {
        public final int iteration;
        public final Result result;
        public final long timestamp;
        
        public CheckpointData(int iteration, Result result) {
            this(iteration, result, System.currentTimeMillis());
        }
        
        public CheckpointData(int iteration, Result result, long timestamp) {
            this.iteration = iteration;
            this.result = result;
            this.timestamp = timestamp;
        }
    }
}
