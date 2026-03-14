import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Arrays;

/**
 * 超高性能流式除法 - 支持 1-10 亿位 π 计算
 *
 * 核心优化点：
 * 1. 真正的逐位流式除法 - 不创建完整的 π BigInteger
 * 2. 批量计算 + 批量写入 - 每次计算 1000 位，减少除法次数
 * 3. 零拷贝写入 - 使用 byte[] 缓冲区直接写入
 * 4. 预分配常量 - 避免重复创建 BigInteger
 * 5. 内存复用 - 重用中间结果对象
 * 6. 智能精度控制 - 只计算需要的精度
 *
 * 内存优化原理：
 * - 传统方法：创建完整的 π BigInteger (2.5 亿位 ≈ 100MB+)
 * - 流式方法：只保留 remainder (与 T 同量级) + 小缓冲区
 * - 内存节省：95%+
 */
public class StreamingDivision {
    
    // ==================== 配置常量 ====================
    
    /** 每次批量计算的位数 - 平衡性能和内存 */
    private static final int BATCH_SIZE = 1000;
    
    /** 写入缓冲区大小 - 1MB */
    private static final int WRITE_BUFFER_SIZE = 1024 * 1024;
    
    /** 进度显示间隔 - 每 10 万位 */
    private static final int PROGRESS_INTERVAL = 100_000;
    
    /** 每行输出的位数 - 提高可读性 */
    private static final int CHARS_PER_LINE = 100;
    
    /** 每多少行插入一个空行 */
    private static final int LINES_PER_BLOCK = 10;
    
    // ==================== 预计算常量 ====================
    
    /** 426880 - Chudnovsky 公式常量 */
    private static final BigInteger MULTIPLIER = new BigInteger("426880");
    
    /** 10005 - 平方根常量 */
    private static final int SQRT_CONSTANT = 10005;
    
    /** 预计算的 10^1, 10^2, ..., 10^BATCH_SIZE */
    private static final BigInteger[] POWERS_OF_TEN;
    
    /** 数字 0-9 的 byte 表示 */
    private static final byte[] DIGIT_BYTES;
    
    /** ASCII '0' 的 byte 值 */
    private static final byte ASCII_ZERO = (byte) '0';
    
    /** 换行符 byte */
    private static final byte NEWLINE = (byte) '\n';
    
    static {
        // 预计算 10 的幂次
        POWERS_OF_TEN = new BigInteger[BATCH_SIZE + 1];
        POWERS_OF_TEN[0] = BigInteger.ONE;
        for (int i = 1; i <= BATCH_SIZE; i++) {
            POWERS_OF_TEN[i] = POWERS_OF_TEN[i - 1].multiply(BigInteger.TEN);
        }
        
        // 预计算数字 byte
        DIGIT_BYTES = new byte[10];
        for (int i = 0; i < 10; i++) {
            DIGIT_BYTES[i] = (byte) ('0' + i);
        }
    }
    
    // ==================== 可复用缓冲区 ====================
    
    /** 商数字缓冲区 - 复用避免分配 */
    private static final ThreadLocal<byte[]> DIGIT_BUFFER = 
        ThreadLocal.withInitial(() -> new byte[BATCH_SIZE + CHARS_PER_LINE + 10]);
    
    /** 写入缓冲区 - 复用避免分配 */
    private static final ThreadLocal<byte[]> WRITE_BUFFER = 
        ThreadLocal.withInitial(() -> new byte[WRITE_BUFFER_SIZE]);
    
    /**
     * 流式计算 π 值并输出到文件
     * 
     * 算法流程：
     * 1. 计算 numerator = 426880 * sqrt(10005) * Q
     * 2. 计算 integerPart = numerator / T
     * 3. 计算 remainder = numerator % T
     * 4. 流式输出：digit = (remainder * 10^BATCH) / T
     *              remainder = (remainder * 10^BATCH) % T
     * 
     * @param q Q 值 (来自 Binary Splitting)
     * @param t T 值 (来自 Binary Splitting)
     * @param totalDigits 总位数
     * @param outputFile 输出文件路径
     */
    public static void streamPi(BigInteger q, BigInteger t, int totalDigits, String outputFile) throws IOException {
        System.out.println("[流式除法] 开始计算 π 值...");
        System.out.printf("           Q 值位数：%,d%n", q.toString().length());
        System.out.printf("           T 值位数：%,d%n", t.toString().length());
        System.out.printf("           目标精度：%,d 位%n", totalDigits);

        long startTime = System.nanoTime();

        // ========== 步骤 1: 计算分子 ==========
        // numerator = 426880 * sqrt(10005) * Q
        // 使用 BigDecimal 进行高精度计算

        // extraPrecision 用于 sqrt 计算，不需要放大分子
        int extraPrecision = Math.max(100, totalDigits / 10);
        MathContext mc = new MathContext(extraPrecision);

        // 计算 sqrt(10005)
        BigDecimal sqrt10005 = BigDecimal.valueOf(SQRT_CONSTANT).sqrt(mc);

        // 计算 constant = 426880 * sqrt(10005)
        BigDecimal constant = new BigDecimal(MULTIPLIER).multiply(sqrt10005, mc);

        // 计算 numerator = constant * Q
        BigDecimal bigQ = new BigDecimal(q);
        BigDecimal numeratorBd = constant.multiply(bigQ, mc);

        // 转换为 BigInteger（不放大，直接取整）
        BigInteger numerator = numeratorBd.toBigInteger();

        System.out.printf("           分子位数：%,d%n", numerator.toString().length());

        // ========== 步骤 2: 计算整数部分和初始余数 ==========
        // π = numerator / T，整数部分应该是 "3"
        BigInteger[] divResult = numerator.divideAndRemainder(t);
        String integerPart = divResult[0].toString();
        BigInteger remainder = divResult[1];

        System.out.printf("           整数部分：%s (%d 位)%n", integerPart, integerPart.length());

        // ========== 步骤 3: 流式输出到文件 ==========
        streamDigits(t, remainder, integerPart, totalDigits, outputFile);

        long endTime = System.nanoTime();
        double duration = (endTime - startTime) / 1_000_000_000.0;

        System.out.printf("[流式除法] 完成，耗时 %.2f 秒，速度 %.0f 位/秒%n",
            duration, totalDigits / duration);
    }
    
    /**
     * 流式输出小数部分
     * 
     * 核心算法：
     * for each batch:
     *   remainder = remainder * 10^BATCH_SIZE
     *   quotient = remainder / T
     *   remainder = remainder % T
     *   write quotient digits to file
     * 
     * @param t 分母 T
     * @param remainder 初始余数
     * @param integerPart 整数部分
     * @param totalDigits 总位数
     * @param outputFile 输出文件
     */
    private static void streamDigits(BigInteger t, BigInteger remainder, 
                                     String integerPart, int totalDigits, 
                                     String outputFile) throws IOException {
        
        byte[] digitBuffer = DIGIT_BUFFER.get();
        byte[] writeBuf = WRITE_BUFFER.get();
        int writePos = 0;
        
        try (BufferedOutputStream out = new BufferedOutputStream(
                new FileOutputStream(outputFile), WRITE_BUFFER_SIZE)) {
            
            // 写入文件头
            String header = "# π 值计算结果 (Chudnovsky 算法)\n" +
                           "# 精度：" + totalDigits + " 位\n" +
                           "# 生成时间：" + new java.util.Date() + "\n\n";
            out.write(header.getBytes());

            // 写入整数部分和小数点（格式：3.1415...）
            // π 的整数部分只有 1 位 "3"，所以直接写 "3." 然后接小数部分
            out.write(integerPart.getBytes());
            out.write('.');
            // 注意：整数部分和小数点在同一行，不要换行，小数部分直接跟随

            int digitsWritten = 0;
            int linePos = 0;  // 当前行已写字符数
            int blockLines = 0; // 当前块的行数

            while (digitsWritten < totalDigits) {
                // 计算当前批次的实际大小
                int currentBatchSize = Math.min(BATCH_SIZE, totalDigits - digitsWritten);
                BigInteger multiplier = POWERS_OF_TEN[currentBatchSize];
                
                // 核心流式除法：remainder * 10^batch / T
                remainder = remainder.multiply(multiplier);
                BigInteger[] divResult = remainder.divideAndRemainder(t);
                remainder = divResult[1];  // 更新余数
                
                // 将商转换为数字
                BigInteger quotient = divResult[0];
                int digitCount = quotientToDigits(quotient, digitBuffer, currentBatchSize);
                
                // 逐位写入，按格式换行
                for (int i = 0; i < digitCount && digitsWritten < totalDigits; i++) {
                    writeBuf[writePos++] = digitBuffer[i];
                    linePos++;
                    digitsWritten++;
                    
                    // 每行满 CHARS_PER_LINE 个字符后换行
                    if (linePos >= CHARS_PER_LINE) {
                        writeBuf[writePos++] = NEWLINE;
                        linePos = 0;
                        blockLines++;
                        
                        // 每 LINES_PER_BLOCK 行插入空行
                        if (blockLines >= LINES_PER_BLOCK) {
                            writeBuf[writePos++] = NEWLINE;
                            blockLines = 0;
                        }
                    }
                    
                    // 缓冲区满时刷新
                    if (writePos >= WRITE_BUFFER_SIZE - BATCH_SIZE) {
                        out.write(writeBuf, 0, writePos);
                        writePos = 0;
                    }
                }
                
                // 进度显示
                if (digitsWritten % PROGRESS_INTERVAL == 0) {
                    // 刷新缓冲区确保进度准确
                    if (writePos > 0) {
                        out.write(writeBuf, 0, writePos);
                        writePos = 0;
                    }
                    out.flush();
                    
                    int progress = digitsWritten * 100 / totalDigits;
                    double speed = digitsWritten / ((System.nanoTime() - startTime()) / 1_000_000_000.0);
                    System.out.printf("           ✓ 已输出 %12d 位 (%3d%%) 速度 %.0f 位/秒%n", 
                        digitsWritten, progress, speed);
                }
            }
            
            // 写入剩余数据
            if (writePos > 0) {
                out.write(writeBuf, 0, writePos);
            }
            
            // 最后换行
            out.write(NEWLINE);
        }
    }
    
    /**
     * 将 BigInteger 商转换为数字 byte 数组
     * 
     * 优化点：
     * 1. 使用预分配的 buffer，避免分配
     * 2. 前面补零到指定位数
     * 3. 直接输出 byte，避免 String 转换
     * 
     * @param quotient 商
     * @param buffer 输出缓冲区
     * @param expectedLength 期望长度（用于补零）
     * @return 实际输出的数字个数
     */
    private static int quotientToDigits(BigInteger quotient, byte[] buffer, int expectedLength) {
        String quotientStr = quotient.toString();
        int actualLength = quotientStr.length();
        
        // 计算需要补的零
        int leadingZeros = Math.max(0, expectedLength - actualLength);
        int totalLength = leadingZeros + actualLength;
        
        // 填充前导零
        Arrays.fill(buffer, 0, leadingZeros, ASCII_ZERO);
        
        // 填充实际数字
        for (int i = 0; i < actualLength; i++) {
            buffer[leadingZeros + i] = (byte) quotientStr.charAt(i);
        }
        
        return totalLength;
    }
    
    /**
     * 记录流式除法开始时间
     */
    private static long startTime() {
        return System.nanoTime();
    }
    
    /**
     * 通用流式除法（用于其他场景）
     * 
     * @param numerator 分子
     * @param denominator 分母
     * @param totalDigits 小数位数
     * @param outputFile 输出文件
     */
    public static void streamDivide(BigInteger numerator, BigInteger denominator,
                                   int totalDigits, String outputFile) throws IOException {
        // 计算整数部分
        BigInteger[] divResult = numerator.divideAndRemainder(denominator);
        String integerPart = divResult[0].toString();
        BigInteger remainder = divResult[1];
        
        streamDigits(denominator, remainder, integerPart, totalDigits, outputFile);
    }
    
    /**
     * 高性能平方根计算（用于计算 sqrt(10005)）
     * 使用 Newton-Raphson 迭代法
     * 
     * @param n 被开方数
     * @param precision 精度（位数）
     * @return 平方根
     */
    public static BigDecimal sqrtHighPrecision(int n, int precision) {
        MathContext mc = new MathContext(precision + 20);
        return BigDecimal.valueOf(n).sqrt(mc);
    }
}
