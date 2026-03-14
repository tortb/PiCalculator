import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;

/**
 * 工业级流式除法引擎 - Block Division 优化版
 * 
 * 核心优化：
 * 1. Block Division - 每次除法输出 50000 位（10^50000 基数）
 * 2. Newton-Raphson 除法 - O(n^1.58) 复杂度
 * 3. 分治 toString - O(n log n) 转换
 * 4. 1MB 缓冲写入 - 减少系统调用
 * 5. 实时进度显示 - 每 10 万位输出统计
 * 
 * 性能目标：
 * - 100 万位：< 10 秒
 * - 1000 万位：< 1 分钟
 * - 1 亿位：< 5 分钟
 */
public class StreamingDivisionEngine {

    // ==================== 配置常量 ====================

    /** 块大小：10^50000，每次除法输出 50000 位 */
    private static final int BLOCK_DIGITS = 50_000;

    /** 写入缓冲区大小 - 2MB */
    private static final int BUFFER_SIZE = 2 * 1024 * 1024;

    /** 进度显示间隔 - 每 10 万位 */
    private static final int PROGRESS_INTERVAL = 100_000;

    /** 每行输出的位数 */
    private static final int CHARS_PER_LINE = 100;

    /** 每多少行插入一个空行 */
    private static final int LINES_PER_BLOCK = 10;

    // ==================== 预计算常量 ====================

    /** 10^BLOCK_DIGITS */
    private static final BigInteger BLOCK_MULTIPLIER = BigInteger.TEN.pow(BLOCK_DIGITS);

    // ==================== 可复用缓冲区 ====================

    /** 数字字符缓冲区 */
    private static final char[] CHAR_BUFFER = new char[BLOCK_DIGITS + 10];

    /** 写入缓冲区 */
    private static final char[] WRITE_BUFFER = new char[BUFFER_SIZE];

    // ==================== 核心方法 ====================

    /**
     * 流式计算并输出 π 值（Newton-Raphson + Block Division 优化版）
     * 
     * 算法流程：
     * 1. 计算 integerPart = numerator / denominator
     * 2. 计算 remainder = numerator % denominator
     * 3. 分块循环：
     *    remainder = remainder * 10^BLOCK_DIGITS
     *    block = NewtonDivide(remainder, denominator)
     *    remainder = remainder % denominator
     *    输出 block（50000 位）
     */
    public static void streamPi(
            BigInteger numerator,
            BigInteger denominator,
            long digits,
            Path outputFile) throws IOException {

        System.out.println("[流式引擎] 开始 Newton-Raphson + Block Division 计算 π 值...");
        System.out.printf("           分子位数：%,d%n", numerator.toString().length());
        System.out.printf("           分母位数：%,d%n", denominator.toString().length());
        System.out.printf("           目标精度：%,d 位%n", digits);
        System.out.printf("           块大小：%,d 位 (10^50000)%n", BLOCK_DIGITS);

        long startTime = System.nanoTime();

        // ========== 步骤 1: 计算整数部分和初始余数 ==========
        BigInteger[] divResult = NewtonDivision.divideAndRemainder(numerator, denominator);
        String integerPart = BigIntegerToString.toString(divResult[0]);
        BigInteger remainder = divResult[1];

        System.out.printf("           整数部分：%s (%d 位)%n", integerPart, integerPart.length());

        // ========== 步骤 2: Newton-Raphson 分块输出 ==========
        newtonBlockDivide(denominator, remainder, integerPart, digits, outputFile, startTime);
    }

    /**
     * Newton-Raphson 分块输出
     */
    private static void newtonBlockDivide(
            BigInteger denominator,
            BigInteger remainder,
            String integerPart,
            long totalDigits,
            Path outputFile,
            long startTime) throws IOException {

        char[] charBuffer = CHAR_BUFFER;
        char[] writeBuf = WRITE_BUFFER;
        int writePos = 0;

        try (BufferedWriter writer = new BufferedWriter(
                new FileWriter(outputFile.toFile()), BUFFER_SIZE)) {

            // 写入文件头
            writer.write("# π 值计算结果 (Chudnovsky 算法)\n");
            writer.write("# 精度：" + totalDigits + " 位\n");
            writer.write("# 生成时间：" + new java.util.Date() + "\n\n");

            // 写入整数部分和小数点
            writer.write(integerPart);
            writer.write('.');

            long digitsWritten = 0;
            int linePos = 0;
            int blockLines = 0;
            long lastProgressTime = System.currentTimeMillis();

            while (digitsWritten < totalDigits) {
                // 计算当前块的实际大小
                int currentBlockSize = (int) Math.min(BLOCK_DIGITS, totalDigits - digitsWritten);
                BigInteger multiplier = BigInteger.TEN.pow(currentBlockSize);

                // Newton-Raphson 核心算法
                remainder = remainder.multiply(multiplier);
                BigInteger[] divResult = NewtonDivision.divideAndRemainder(remainder, denominator);
                BigInteger block = divResult[0];
                remainder = divResult[1];

                // 将块转换为数字字符（分治 toString 优化）
                int charCount = blockToChars(block, charBuffer, currentBlockSize);

                // 逐位写入
                for (int i = 0; i < charCount; i++) {
                    writeBuf[writePos++] = charBuffer[i];
                    linePos++;
                    digitsWritten++;

                    // 换行处理
                    if (linePos >= CHARS_PER_LINE) {
                        writeBuf[writePos++] = '\n';
                        linePos = 0;
                        blockLines++;

                        if (blockLines >= LINES_PER_BLOCK) {
                            writeBuf[writePos++] = '\n';
                            blockLines = 0;
                        }
                    }

                    // 缓冲区满时刷新
                    if (writePos >= BUFFER_SIZE - BLOCK_DIGITS - 100) {
                        writer.write(writeBuf, 0, writePos);
                        writePos = 0;
                    }
                }

                // 进度显示（每 10 万位 或至少 1 秒一次）
                long currentTime = System.currentTimeMillis();
                if (digitsWritten % PROGRESS_INTERVAL == 0 || 
                    (currentTime - lastProgressTime) >= 1000) {
                    if (writePos > 0) {
                        writer.write(writeBuf, 0, writePos);
                        writePos = 0;
                    }
                    writer.flush();

                    printProgress(digitsWritten, totalDigits, startTime, lastProgressTime);
                    lastProgressTime = currentTime;
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
     * 将 BigInteger 块转换为字符数组（分治 toString 优化）
     */
    private static int blockToChars(BigInteger block, char[] buffer, int expectedLength) {
        String blockStr = BigIntegerToString.toString(block);
        int actualLength = blockStr.length();

        // 计算需要补的零
        int leadingZeros = expectedLength - actualLength;
        
        // 填充前导零
        for (int i = 0; i < leadingZeros; i++) {
            buffer[i] = '0';
        }

        // 填充实际数字
        for (int i = 0; i < actualLength; i++) {
            buffer[leadingZeros + i] = blockStr.charAt(i);
        }

        return expectedLength;
    }

    /**
     * 打印进度信息
     */
    private static void printProgress(long digitsWritten, long totalDigits, 
                                      long startTimeNanos, long lastProgressTime) {
        long currentTimeNanos = System.nanoTime();
        double elapsedSeconds = (currentTimeNanos - startTimeNanos) / 1_000_000_000.0;
        
        double intervalSeconds = (System.currentTimeMillis() - lastProgressTime) / 1000.0;

        int progress = (int) (digitsWritten * 100 / totalDigits);
        double speed = elapsedSeconds > 0.1 ? digitsWritten / elapsedSeconds : 0;

        double remainingSeconds = speed > 0 ? (totalDigits - digitsWritten) / speed : 999999;
        String remainingTime = formatRemainingTime(remainingSeconds);

        System.out.printf("           ✓ 已输出 %12d 位 (%3d%%) | 速度 %.0f 位/秒 | 剩余 %s | 间隔 %.1f 秒%n",
            digitsWritten, progress, speed, remainingTime, intervalSeconds);
    }

    /**
     * 格式化剩余时间
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
}
