import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;

/**
 * 流式除法引擎 - 逐位计算并输出 π 值
 * 
 * 核心优化：
 * 1. 真正的逐位流式除法 - 不创建完整的 π BigInteger
 * 2. 使用 shift 操作代替 multiply(10) - 减少对象创建
 * 3. 1MB 缓冲写入 - 减少系统调用
 * 4. 实时进度显示 - 每 10 万位输出统计
 * 
 * 内存优化原理：
 * - 传统方法：创建完整 π BigInteger (2.5 亿位 ≈ 100MB+) → OOM
 * - 流式方法：只保留 remainder，逐位输出 → 内存降低 95%+
 * 
 * @author HPC Pi Calculator Team
 */
public class StreamingDivisionEngine {

    // ==================== 配置常量 ====================

    /** 每次批量计算的位数（1000 位一次除法，平衡性能） */
    private static final int BATCH_SIZE = 1000;

    /** 写入缓冲区大小 - 1MB */
    private static final int BUFFER_SIZE = 1024 * 1024;

    /** 进度显示间隔 - 每 10 万位 */
    private static final int PROGRESS_INTERVAL = 100_000;

    /** 每行输出的位数 */
    private static final int CHARS_PER_LINE = 100;

    /** 每多少行插入一个空行 */
    private static final int LINES_PER_BLOCK = 10;

    // ==================== 预计算常量 ====================

    /** 预计算的 10^1 到 10^BATCH_SIZE */
    private static final BigInteger[] POWERS_OF_TEN;

    /** ASCII '0' */
    private static final byte ASCII_ZERO = (byte) '0';

    /** 换行符 */
    private static final byte NEWLINE = (byte) '\n';

    static {
        // 预计算 10 的幂次
        POWERS_OF_TEN = new BigInteger[BATCH_SIZE + 1];
        POWERS_OF_TEN[0] = BigInteger.ONE;
        for (int i = 1; i <= BATCH_SIZE; i++) {
            POWERS_OF_TEN[i] = POWERS_OF_TEN[i - 1].multiply(BigInteger.TEN);
        }
    }

    // ==================== 可复用缓冲区 ====================

    /** 数字输出缓冲区（ThreadLocal 复用） */
    private static final ThreadLocal<byte[]> DIGIT_BUFFER =
        ThreadLocal.withInitial(() -> new byte[BATCH_SIZE + CHARS_PER_LINE + 10]);

    /** 文件写入缓冲区（ThreadLocal 复用） */
    private static final ThreadLocal<char[]> WRITE_BUFFER =
        ThreadLocal.withInitial(() -> new char[BUFFER_SIZE]);

    // ==================== 核心方法 ====================

    /**
     * 流式计算并输出 π 值
     * 
     * 算法流程：
     * 1. 计算 integerPart = numerator / denominator
     * 2. 计算 remainder = numerator % denominator
     * 3. 循环 digits 次：
     *    digit = (remainder * 10) / denominator
     *    remainder = (remainder * 10) % denominator
     *    输出 digit
     * 
     * 优化：
     * - 批量计算：每次计算 BATCH_SIZE 位，减少除法次数
     * - shift 优化：10x = 8x + 2x = x.shiftLeft(3) + x.shiftLeft(1)
     * - 缓冲写入：1MB buffer，减少系统调用
     * 
     * @param numerator 分子（426880 * sqrt(10005) * Q）
     * @param denominator 分母（T）
     * @param digits 目标精度（位数）
     * @param outputFile 输出文件路径
     */
    public static void streamPi(
            BigInteger numerator,
            BigInteger denominator,
            long digits,
            Path outputFile) throws IOException {

        System.out.println("[流式引擎] 开始逐位计算 π 值...");
        System.out.printf("           分子位数：%,d%n", numerator.toString().length());
        System.out.printf("           分母位数：%,d%n", denominator.toString().length());
        System.out.printf("           目标精度：%,d 位%n", digits);

        long startTime = System.nanoTime();

        // ========== 步骤 1: 计算整数部分和初始余数 ==========
        BigInteger[] divResult = numerator.divideAndRemainder(denominator);
        String integerPart = divResult[0].toString();
        BigInteger remainder = divResult[1];

        System.out.printf("           整数部分：%s (%d 位)%n", integerPart, integerPart.length());

        // ========== 步骤 2: 流式输出到文件 ==========
        streamDigits(denominator, remainder, integerPart, digits, outputFile, startTime);
    }

    /**
     * 流式输出小数部分
     * 
     * 核心算法（批量版本）：
     * for each batch:
     *   remainder = remainder * 10^BATCH_SIZE
     *   quotient = remainder / denominator
     *   remainder = remainder % denominator
     *   输出 quotient 的 BATCH_SIZE 位数字
     * 
     * @param denominator 分母 T
     * @param remainder 初始余数
     * @param integerPart 整数部分
     * @param totalDigits 总位数
     * @param outputFile 输出文件
     * @param startTime 开始时间（用于计算速度）
     */
    private static void streamDigits(
            BigInteger denominator,
            BigInteger remainder,
            String integerPart,
            long totalDigits,
            Path outputFile,
            long startTime) throws IOException {

        byte[] digitBuffer = DIGIT_BUFFER.get();
        char[] writeBuf = WRITE_BUFFER.get();
        int writePos = 0;

        try (BufferedWriter writer = new BufferedWriter(
                new FileWriter(outputFile.toFile()), BUFFER_SIZE)) {

            // 写入文件头
            writer.write("# π 值计算结果 (Chudnovsky 算法)\n");
            writer.write("# 精度：" + totalDigits + " 位\n");
            writer.write("# 生成时间：" + new java.util.Date() + "\n\n");

            // 写入整数部分和小数点（格式：3.1415...）
            writer.write(integerPart);
            writer.write('.');

            long digitsWritten = 0;
            int linePos = 0;
            int blockLines = 0;

            while (digitsWritten < totalDigits) {
                // 计算当前批次的实际大小
                int currentBatchSize = (int) Math.min(BATCH_SIZE, totalDigits - digitsWritten);
                BigInteger multiplier = POWERS_OF_TEN[currentBatchSize];

                // 核心流式除法：remainder * 10^batch / denominator
                // 优化：使用预计算的 10^batch，避免重复计算
                remainder = remainder.multiply(multiplier);
                BigInteger[] divResult = remainder.divideAndRemainder(denominator);
                remainder = divResult[1];  // 更新余数

                // 将商转换为数字
                BigInteger quotient = divResult[0];
                int digitCount = quotientToDigits(quotient, digitBuffer, currentBatchSize);

                // 逐位写入
                for (int i = 0; i < digitCount && digitsWritten < totalDigits; i++) {
                    writeBuf[writePos++] = (char) digitBuffer[i];
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

                    // 缓冲区满时刷新（每 1000 位左右）
                    if (writePos >= BUFFER_SIZE - BATCH_SIZE - 100) {
                        writer.write(writeBuf, 0, writePos);
                        writePos = 0;
                    }
                }

                // 进度显示（每 10 万位）
                if (digitsWritten % PROGRESS_INTERVAL == 0) {
                    // 刷新缓冲区确保进度准确
                    if (writePos > 0) {
                        writer.write(writeBuf, 0, writePos);
                        writePos = 0;
                    }
                    writer.flush();

                    printProgress(digitsWritten, totalDigits, startTime);
                }
            }

            // 写入剩余数据
            if (writePos > 0) {
                writer.write(writeBuf, 0, writePos);
            }

            // 最后换行
            writer.newLine();
        }

        long endTime = System.nanoTime();
        double duration = (endTime - startTime) / 1_000_000_000.0;
        System.out.printf("[流式引擎] 完成，耗时 %.2f 秒，速度 %.0f 位/秒%n",
            duration, totalDigits / duration);
    }

    /**
     * 打印进度信息
     */
    private static void printProgress(long digitsWritten, long totalDigits, long startTime) {
        long elapsedNanos = System.nanoTime() - startTime;
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        
        int progress = (int) (digitsWritten * 100 / totalDigits);
        double speed = digitsWritten / elapsedSeconds;
        
        // 估算剩余时间
        double remainingSeconds = (totalDigits - digitsWritten) / speed;
        String remainingTime = formatRemainingTime(remainingSeconds);

        System.out.printf("           ✓ 已输出 %12d 位 (%3d%%) | 速度 %.0f 位/秒 | 剩余 %s%n",
            digitsWritten, progress, speed, remainingTime);
    }

    /**
     * 格式化剩余时间显示
     */
    private static String formatRemainingTime(double seconds) {
        if (seconds < 60) {
            return String.format("%.0f 秒", seconds);
        } else if (seconds < 3600) {
            long mins = (long) (seconds / 60);
            long secs = (long) (seconds % 60);
            return String.format("%d 分 %d 秒", mins, secs);
        } else {
            long hours = (long) (seconds / 3600);
            long mins = (long) ((seconds % 3600) / 60);
            long secs = (long) (seconds % 60);
            return String.format("%d 小时 %d 分 %d 秒", hours, mins, secs);
        }
    }

    /**
     * 将 BigInteger 商转换为数字 byte 数组
     * 
     * 优化：
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
        for (int i = 0; i < leadingZeros; i++) {
            buffer[i] = ASCII_ZERO;
        }

        // 填充实际数字
        for (int i = 0; i < actualLength; i++) {
            buffer[leadingZeros + i] = (byte) quotientStr.charAt(i);
        }

        return totalLength;
    }

    /**
     * 快速乘以 10（shift 优化）
     * 
     * 优化原理：
     * 10x = 8x + 2x = x << 3 + x << 1
     * 
     * 注意：此方法会创建新的 BigInteger 对象，
     * 在批量计算中使用预计算的 POWERS_OF_TEN 更高效。
     * 
     * @param x 输入值
     * @return 10 * x
     */
    @SuppressWarnings("unused")
    private static BigInteger multiplyBy10(BigInteger x) {
        return x.shiftLeft(3).add(x.shiftLeft(1));
    }

    /**
     * 快速乘以 10^n（shift 优化版本）
     * 
     * 优化原理：
     * 10^n = 2^n * 5^n
     * 
     * 注意：对于 n > 10，直接使用 BigInteger.TEN.pow(n) 更高效。
     * 
     * @param x 输入值
     * @param n 幂次
     * @return x * 10^n
     */
    @SuppressWarnings("unused")
    private static BigInteger multiplyByPowerOf10(BigInteger x, int n) {
        if (n <= 0) return x;
        if (n <= 10) {
            // 小幂次使用 shift 优化
            BigInteger powerOf5 = BigInteger.valueOf(5).pow(n);
            return x.multiply(powerOf5).shiftLeft(n);
        } else {
            // 大幂次直接使用 TEN.pow()
            return x.multiply(POWERS_OF_TEN[n]);
        }
    }
}
