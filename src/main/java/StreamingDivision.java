import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * HPC 优化版流式除法计算
 * 
 * 优化点：
 * 1. FileChannel + ByteBuffer - 使用 NIO 提高大文件写入性能
 * 2. 批量输出 - 每次输出 100000 位，减少系统调用次数
 * 3. 内存优化 - 不生成完整π字符串，边计算边输出
 * 4. 进度显示 - 每 10 万位显示一次进度
 * 5. 预分配缓冲区 - 减少内存分配开销
 * 6. 可读性优化 - 每行 100 位数字，每 10 行一个大段，提高可读性
 */
public class StreamingDivision {
    // 输出配置 - 优化可读性
    private static final int CHUNK_SIZE = 100000;      // 每次计算 100000 位
    private static final int WRITE_BUFFER_SIZE = 1024 * 1024;  // 1MB 写入缓冲区
    private static final int PROGRESS_INTERVAL = 100000;  // 每 10 万位显示进度
    private static final int CHARS_PER_LINE = 100;        // 每行 100 位（提高可读性）
    private static final int LINES_PER_BLOCK = 10;        // 每 10 行一个空行分隔
    
    // 常量
    private static final BigInteger MULTIPLIER = new BigInteger("426880");
    private static final int SQRT_CONSTANT = 10005;

    /**
     * 流式计算π值并输出到文件（HPC 优化版）
     * 
     * @param q Q 值
     * @param t T 值
     * @param totalDigits 总位数
     * @param outputFile 输出文件路径
     */
    public static void streamPi(BigInteger q, BigInteger t, int totalDigits, String outputFile) throws IOException {
        // 计算额外精度（防止舍入误差）
        int extraPrecision = totalDigits + 100;
        MathContext mc = new MathContext(extraPrecision);

        // 计算常数：426880 * sqrt(10005)
        BigDecimal sqrt10005 = new BigDecimal("10005").sqrt(mc);
        BigDecimal multiplier = new BigDecimal("426880");
        BigDecimal constant = multiplier.multiply(sqrt10005, mc);

        // 计算分子：constant * Q
        BigDecimal bigQ = new BigDecimal(q);
        BigDecimal numeratorBd = constant.multiply(bigQ, mc);

        // 转换为 BigInteger 用于整数除法
        // 放大 10^extraPrecision 倍以保持精度
        BigInteger scale = BigInteger.TEN.pow(extraPrecision);
        BigInteger scaledNumerator = numeratorBd.multiply(new BigDecimal(scale)).toBigInteger();
        BigInteger scaledDenominator = t.multiply(scale);

        // 使用 FileChannel 和 ByteBuffer 进行高效写入
        try (RandomAccessFile raf = new RandomAccessFile(outputFile, "rw");
             FileChannel channel = raf.getChannel();
             BufferedOutputStream bos = new BufferedOutputStream(
                 new FileOutputStream(raf.getFD()), WRITE_BUFFER_SIZE)) {

            // 写入文件头
            String header = "# π值计算结果\n# 精度：" + totalDigits + " 位\n\n";
            bos.write(header.getBytes());

            // 计算整数部分
            BigInteger[] divAndRemainder = scaledNumerator.divideAndRemainder(scaledDenominator);
            String integerPart = divAndRemainder[0].toString();
            bos.write(integerPart.getBytes());
            bos.write('.');

            // 计算小数部分
            BigInteger remainder = divAndRemainder[1];
            int digitsWritten = 0;
            int lineCount = 0;
            int blockLineCount = 0;  // 当前块内的行数

            // 预分配 ByteBuffer（复用）
            ByteBuffer byteBuffer = ByteBuffer.allocate(CHUNK_SIZE + 1000);

            while (digitsWritten < totalDigits) {
                // 计算当前块大小
                int chunkSize = Math.min(CHUNK_SIZE, totalDigits - digitsWritten);
                BigInteger powerOfTen = BigInteger.TEN.pow(chunkSize);
                remainder = remainder.multiply(powerOfTen);

                // 执行除法
                divAndRemainder = remainder.divideAndRemainder(scaledDenominator);
                BigInteger quotient = divAndRemainder[0];
                remainder = divAndRemainder[1];

                // 格式化商（前面补零）
                String quotientStr = formatQuotient(quotient, chunkSize);

                // 截取需要的位数
                if (quotientStr.length() > (totalDigits - digitsWritten)) {
                    quotientStr = quotientStr.substring(0, totalDigits - digitsWritten);
                }

                // 按行写入，每行 CHARS_PER_LINE 个字符
                int pos = 0;
                while (pos < quotientStr.length()) {
                    int charsToWrite = Math.min(CHARS_PER_LINE, quotientStr.length() - pos);
                    byteBuffer.put(quotientStr.substring(pos, pos + charsToWrite).getBytes());
                    byteBuffer.put((byte) '\n');
                    
                    pos += charsToWrite;
                    digitsWritten += charsToWrite;
                    lineCount++;
                    blockLineCount++;
                    
                    // 每 LINES_PER_BLOCK 行插入一个空行
                    if (blockLineCount >= LINES_PER_BLOCK) {
                        byteBuffer.put((byte) '\n');
                        blockLineCount = 0;
                    }
                }
                
                // 缓冲区满或达到进度间隔时刷新
                if (byteBuffer.position() > WRITE_BUFFER_SIZE - CHUNK_SIZE || 
                    digitsWritten % PROGRESS_INTERVAL == 0) {
                    byteBuffer.flip();
                    bos.write(byteBuffer.array(), 0, byteBuffer.limit());
                    byteBuffer.clear();
                    
                    // 显示进度
                    if (digitsWritten % PROGRESS_INTERVAL == 0) {
                        int progress = digitsWritten * 100 / totalDigits;
                        System.out.printf("      ✓ 已输出 %d 位 (%d%%)%n", digitsWritten, progress);
                    }
                }
            }

            // 刷新剩余数据
            if (byteBuffer.position() > 0) {
                byteBuffer.flip();
                bos.write(byteBuffer.array(), 0, byteBuffer.limit());
            }

            // 最后换行
            if (lineCount > 0) {
                bos.write('\n');
            }
        }
    }

    /**
     * 格式化商（前面补零）
     * 优化：使用 StringBuilder 而不是 String.format
     */
    private static String formatQuotient(BigInteger quotient, int expectedLength) {
        String quotientStr = quotient.toString();
        int actualLength = quotientStr.length();
        
        if (actualLength >= expectedLength) {
            return quotientStr;
        }
        
        // 前面补零
        StringBuilder sb = new StringBuilder(expectedLength);
        for (int i = 0; i < expectedLength - actualLength; i++) {
            sb.append('0');
        }
        sb.append(quotientStr);
        return sb.toString();
    }

    /**
     * 流式除法（通用版本）
     */
    public static void streamDivide(BigInteger numerator, BigInteger denominator,
                                   int totalDigits, String outputFile) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile), WRITE_BUFFER_SIZE)) {
            // 写入文件头
            writer.write("# π值计算结果\n");
            writer.write("# 精度：" + totalDigits + " 位\n\n");

            // 计算整数部分
            BigInteger[] divAndRemainder = numerator.divideAndRemainder(denominator);
            String integerPart = divAndRemainder[0].toString();
            writer.write(integerPart);
            writer.write('.');

            // 计算小数部分
            BigInteger remainder = divAndRemainder[1];
            int digitsWritten = 0;
            int lineCount = 0;
            int blockLineCount = 0;

            while (digitsWritten < totalDigits) {
                int chunkSize = Math.min(CHUNK_SIZE, totalDigits - digitsWritten);
                BigInteger powerOfTen = BigInteger.TEN.pow(chunkSize);
                remainder = remainder.multiply(powerOfTen);

                divAndRemainder = remainder.divideAndRemainder(denominator);
                BigInteger quotient = divAndRemainder[0];
                remainder = divAndRemainder[1];

                String quotientStr = formatQuotient(quotient, chunkSize);

                // 按行写入
                int pos = 0;
                while (pos < quotientStr.length()) {
                    int charsToWrite = Math.min(CHARS_PER_LINE, quotientStr.length() - pos);
                    writer.write(quotientStr.substring(pos, pos + charsToWrite));
                    writer.newLine();
                    
                    pos += charsToWrite;
                    digitsWritten += charsToWrite;
                    lineCount++;
                    blockLineCount++;
                    
                    // 每 LINES_PER_BLOCK 行插入一个空行
                    if (blockLineCount >= LINES_PER_BLOCK) {
                        writer.newLine();
                        blockLineCount = 0;
                    }
                }

                if (digitsWritten % PROGRESS_INTERVAL == 0) {
                    writer.flush();
                    int progress = digitsWritten * 100 / totalDigits;
                    System.out.printf("      ✓ 已输出 %d 位 (%d%%)%n", digitsWritten, progress);
                }
            }
        }
    }

    /**
     * 计算带精度的平方根（Newton 迭代法）
     */
    private static BigInteger calculateSqrtWithPrecision(int n, int precision) {
        BigDecimal bigN = new BigDecimal(n);
        BigDecimal scaleFactor = BigDecimal.TEN.pow(precision);
        bigN = bigN.multiply(scaleFactor.pow(2));

        BigDecimal sqrtResult = bigN.sqrt(new MathContext(precision + 10));
        return sqrtResult.toBigInteger();
    }
}
