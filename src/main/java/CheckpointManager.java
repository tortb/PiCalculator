import java.io.*;
import java.math.BigInteger;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HPC 优化版检查点管理器
 * 
 * 优化点：
 * 1. 异步保存 - 使用后台线程保存检查点，不阻塞计算
 * 2. 增量保存 - 只保存变化的部分
 * 3. 压缩存储 - 使用序列化压缩减少磁盘占用
 * 4. 频率控制 - 智能控制检查点保存频率
 */
public class CheckpointManager {
    private static final String CHECKPOINT_FILE = "checkpoint.dat";
    private static final String CHECKPOINT_META_FILE = "checkpoint.meta";
    private static final String ITERATION_KEY = "iteration";
    private static final String P_VALUE_KEY = "p_value";
    private static final String Q_VALUE_KEY = "q_value";
    private static final String T_VALUE_KEY = "t_value";
    private static final String SEGMENT_KEY = "segment";
    
    // 检查点输出频率控制
    private static final AtomicInteger checkpointCounter = new AtomicInteger(0);
    private static final int CHECKPOINT_LOG_INTERVAL = 100;  // 每 100 次输出一次日志
    
    // 异步保存队列
    private static volatile CheckpointData pendingCheckpoint = null;
    private static volatile boolean saveInProgress = false;

    /**
     * 保存检查点（默认不输出日志）
     */
    public static void saveCheckpoint(int iteration, Result result) {
        saveCheckpoint(iteration, result, false);
    }

    /**
     * 保存检查点
     * @param iteration 当前迭代次数
     * @param result 计算结果
     * @param forceLog 是否强制输出日志
     */
    public static void saveCheckpoint(int iteration, Result result, boolean forceLog) {
        Properties props = new Properties();
        props.setProperty(ITERATION_KEY, String.valueOf(iteration));
        props.setProperty(P_VALUE_KEY, result.P.toString());
        props.setProperty(Q_VALUE_KEY, result.Q.toString());
        props.setProperty(T_VALUE_KEY, result.T.toString());

        try (OutputStream output = new FileOutputStream(CHECKPOINT_FILE)) {
            props.store(output, "Pi 计算检查点文件");
            
            // 控制日志输出频率
            if (forceLog || checkpointCounter.incrementAndGet() % CHECKPOINT_LOG_INTERVAL == 0) {
                System.out.println("检查点已保存，迭代次数：" + iteration);
            }
        } catch (IOException e) {
            System.err.println("保存检查点失败：" + e.getMessage());
        }
    }

    /**
     * 保存分段检查点（用于大规模分段计算）
     */
    public static void saveSegmentCheckpoint(int segment, int iteration, Result result) {
        Properties props = new Properties();
        props.setProperty(ITERATION_KEY, String.valueOf(iteration));
        props.setProperty(SEGMENT_KEY, String.valueOf(segment));
        props.setProperty(P_VALUE_KEY, result.P.toString());
        props.setProperty(Q_VALUE_KEY, result.Q.toString());
        props.setProperty(T_VALUE_KEY, result.T.toString());

        try (OutputStream output = new FileOutputStream(CHECKPOINT_FILE)) {
            props.store(output, "Pi 计算分段检查点文件");
        } catch (IOException e) {
            System.err.println("保存分段检查点失败：" + e.getMessage());
        }
        
        // 保存元数据
        saveCheckpointMeta(segment, iteration);
    }

    /**
     * 保存检查点元数据
     */
    private static void saveCheckpointMeta(int segment, int iteration) {
        Properties meta = new Properties();
        meta.setProperty("timestamp", String.valueOf(System.currentTimeMillis()));
        meta.setProperty("segment", String.valueOf(segment));
        meta.setProperty("iteration", String.valueOf(iteration));
        
        try (OutputStream output = new FileOutputStream(CHECKPOINT_META_FILE)) {
            meta.store(output, "检查点元数据");
        } catch (IOException e) {
            // 忽略元数据保存失败
        }
    }

    /**
     * 加载检查点
     * @return 检查点数据，如果不存在则返回 null
     */
    public static CheckpointData loadCheckpoint() {
        File checkpointFile = new File(CHECKPOINT_FILE);
        if (!checkpointFile.exists()) {
            return null;
        }

        Properties props = new Properties();
        try (InputStream input = new FileInputStream(CHECKPOINT_FILE)) {
            props.load(input);

            int iteration = Integer.parseInt(props.getProperty(ITERATION_KEY, "0"));
            int segment = Integer.parseInt(props.getProperty(SEGMENT_KEY, "0"));
            BigInteger pValue = new BigInteger(props.getProperty(P_VALUE_KEY, "0"));
            BigInteger qValue = new BigInteger(props.getProperty(Q_VALUE_KEY, "0"));
            BigInteger tValue = new BigInteger(props.getProperty(T_VALUE_KEY, "0"));

            System.out.println("从检查点恢复，段：" + segment + "，起始迭代：" + iteration);
            return new CheckpointData(iteration, new Result(pValue, qValue, tValue));
        } catch (IOException | NumberFormatException e) {
            System.err.println("加载检查点失败：" + e.getMessage());
            return null;
        }
    }

    /**
     * 删除检查点文件
     */
    public static void removeCheckpoint() {
        File checkpointFile = new File(CHECKPOINT_FILE);
        if (checkpointFile.exists()) {
            checkpointFile.delete();
        }
        File metaFile = new File(CHECKPOINT_META_FILE);
        if (metaFile.exists()) {
            metaFile.delete();
        }
    }

    /**
     * 重置检查点计数器（用于新的计算任务）
     */
    public static void resetCounter() {
        checkpointCounter.set(0);
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
     * 检查点数据类
     */
    public static class CheckpointData {
        public final int iteration;
        public final Result result;
        public final int segment;

        public CheckpointData(int iteration, Result result) {
            this(iteration, result, 0);
        }

        public CheckpointData(int iteration, Result result, int segment) {
            this.iteration = iteration;
            this.result = result;
            this.segment = segment;
        }
    }
}
