import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/**
 * 流式除法计算，用于处理大数除法而不产生整个字符串
 */
public class StreamingDivision {
    private static final int CHUNK_SIZE = 10000; // 每次输出的数字块大小
    private static final BigInteger MULTIPLIER = new BigInteger("426880");
    private static final int SQRT_CONSTANT = 10005;

    /**
     * 流式计算并输出π值到文件
     * @param numerator 分子
     * @param denominator 分母
     * @param totalDigits 总位数
     * @param outputFile 输出文件路径
     * @throws IOException 文件操作异常
     */
    public static void streamDivide(BigInteger numerator, BigInteger denominator,
                                   int totalDigits, String outputFile) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            // 写入文件头
            writer.write("# π值计算结果\n");
            writer.write("# 精度：" + totalDigits + " 位\n\n");

            // 先输出整数部分
            BigInteger[] divAndRemainder = numerator.divideAndRemainder(denominator);
            String integerPart = divAndRemainder[0].toString();
            writer.write(integerPart);
            if (integerPart.length() >= 1) {
                writer.write("."); // 添加小数点
            }

            // 计算剩余的小数位
            BigInteger remainder = divAndRemainder[1];
            int digitsWritten = integerPart.length();
            int lineCount = 0; // 用于换行

            while (digitsWritten < totalDigits) {
                // 将余数乘以 10^CHUNK_SIZE 来获取下一批数字
                int chunkSize = Math.min(CHUNK_SIZE, totalDigits - digitsWritten);
                BigInteger powerOfTen = BigInteger.TEN.pow(chunkSize);
                remainder = remainder.multiply(powerOfTen);

                divAndRemainder = remainder.divideAndRemainder(denominator);
                BigInteger quotient = divAndRemainder[0];
                remainder = divAndRemainder[1];

                // 格式化商，确保有正确的位数（前面补零）
                String quotientStr = quotient.toString();
                if (quotientStr.length() < chunkSize) {
                    // 如果商的位数不足，前面补零
                    quotientStr = String.format("%0" + chunkSize + "d", Long.parseLong(quotientStr));
                }

                // 截取我们需要的位数
                if (quotientStr.length() > (totalDigits - digitsWritten)) {
                    quotientStr = quotientStr.substring(0, totalDigits - digitsWritten);
                }

                // 写入当前块
                writer.write(quotientStr);
                digitsWritten += quotientStr.length();
                lineCount += quotientStr.length();

                // 每 10000 位换行
                if (lineCount >= 10000) {
                    writer.newLine();
                    lineCount = 0;
                }

                // 定期刷新以确保数据写入磁盘
                if (digitsWritten % 100000 == 0) {
                    writer.flush();
                    System.out.println("已计算 " + digitsWritten + " 位");
                }
            }

            // 如果最后没有换行，添加换行
            if (lineCount > 0) {
                writer.newLine();
            }
        }
    }

    /**
     * 流式计算π值并输出到文件
     * @param q Q 值 (对应分母部分)
     * @param t T 值 (对应分子部分)
     * @param totalDigits 总位数
     * @param outputFile 输出文件路径
     * @throws IOException 文件操作异常
     */
    public static void streamPi(BigInteger q, BigInteger t, int totalDigits, String outputFile) throws IOException {
        // 根据 Chudnovsky 算法，π = (426880 * sqrt(10005) * Q) / T
        // 为了获得足够的精度，我们需要在计算平方根时增加额外的精度位
        int extraPrecision = totalDigits + 100;
        MathContext mc = new MathContext(extraPrecision);

        // 计算常数：426880 * sqrt(10005)
        BigDecimal sqrt10005 = new BigDecimal("10005").sqrt(mc);
        BigDecimal multiplier = new BigDecimal("426880");
        BigDecimal constant = multiplier.multiply(sqrt10005, mc);

        // 计算分子：constant * Q
        BigDecimal bigQ = new BigDecimal(q);
        BigDecimal numeratorBd = constant.multiply(bigQ, mc);

        // 将分子和分母转换为 BigInteger，放大 10^extraPrecision 倍
        BigInteger scale = BigInteger.TEN.pow(extraPrecision);
        BigInteger scaledNumerator = numeratorBd.multiply(new BigDecimal(scale)).toBigInteger();

        // 计算 π = scaledNumerator / (t * scale)
        // 这样结果就是正确的比例
        BigInteger scaledDenominator = t.multiply(scale);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            // 写入文件头
            writer.write("# π值计算结果\n");
            writer.write("# 精度：" + totalDigits + " 位\n\n");

            // 先输出整数部分
            BigInteger[] divAndRemainder = scaledNumerator.divideAndRemainder(scaledDenominator);
            String integerPart = divAndRemainder[0].toString();
            writer.write(integerPart);
            writer.write(".");

            // 计算剩余的小数位
            BigInteger remainder = divAndRemainder[1];
            int digitsWritten = 0;
            int lineCount = 0; // 用于换行

            while (digitsWritten < totalDigits) {
                // 将余数乘以 10^CHUNK_SIZE 来获取下一批数字
                int chunkSize = Math.min(CHUNK_SIZE, totalDigits - digitsWritten);
                BigInteger powerOfTen = BigInteger.TEN.pow(chunkSize);
                remainder = remainder.multiply(powerOfTen);

                divAndRemainder = remainder.divideAndRemainder(scaledDenominator);
                BigInteger quotient = divAndRemainder[0];
                remainder = divAndRemainder[1];

                // 格式化商，确保有正确的位数（前面补零）
                String quotientStr = quotient.toString();
                if (quotientStr.length() < chunkSize) {
                    // 如果商的位数不足，前面补零
                    quotientStr = String.format("%0" + chunkSize + "d", Long.parseLong(quotientStr));
                }

                // 截取我们需要的位数
                if (quotientStr.length() > (totalDigits - digitsWritten)) {
                    quotientStr = quotientStr.substring(0, totalDigits - digitsWritten);
                }

                // 写入当前块
                writer.write(quotientStr);
                digitsWritten += quotientStr.length();
                lineCount += quotientStr.length();

                // 每 10000 位换行
                if (lineCount >= 10000) {
                    writer.newLine();
                    lineCount = 0;
                }

                // 定期刷新以确保数据写入磁盘
                if (digitsWritten % 100000 == 0) {
                    writer.flush();
                    System.out.println("已计算 " + digitsWritten + " 位");
                }
            }

            // 如果最后没有换行，添加换行
            if (lineCount > 0) {
                writer.newLine();
            }
        }
    }

    /**
     * 计算带指定位数精度的平方根
     * @param n 要计算平方根的数
     * @param precision 精度
     * @return 带精度的平方根
     */
    private static BigInteger calculateSqrtWithPrecision(int n, int precision) {
        // 使用 BigDecimal 进行高精度平方根计算，然后转换为 BigInteger
        BigDecimal bigN = new BigDecimal(n);
        // 将数值放大以获得所需精度
        BigDecimal scaleFactor = BigDecimal.TEN.pow(precision);
        bigN = bigN.multiply(scaleFactor.pow(2)); // 因为我们要开平方根

        // 使用 BigDecimal 的 sqrt 方法
        BigDecimal sqrtResult = bigN.sqrt(new MathContext(precision + 10));

        // 返回整数部分
        return sqrtResult.toBigInteger();
    }
}
