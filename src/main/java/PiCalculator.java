import java.io.IOException;
import java.math.BigInteger;

/**
 * 工业级π计算器主类
 */
public class PiCalculator {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("用法：java PiCalculator <位数> [输出文件名]");
            System.out.println("示例：java PiCalculator 1000000");
            System.out.println("示例：java PiCalculator 1000000 pi_result.md");
            return;
        }

        try {
            int digits = Integer.parseInt(args[0]);
            if (digits <= 0) {
                System.out.println("错误：位数必须大于 0");
                return;
            }

            String outputFilename = args.length > 1 ? args[1] : "pi_" + digits + "_digits.md";

            // 创建π计算引擎
            PiEngine engine = new PiEngine();

            // 计算并显示进度和时间
            calculateWithProgress(engine, digits, outputFilename);

            // 清理资源
            engine.shutdown();
            CheckpointManager.removeCheckpoint();

        } catch (NumberFormatException e) {
            System.out.println("错误：请输入有效的数字作为位数");
        } catch (Exception e) {
            System.err.println("计算过程中发生错误：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 计算π值并显示进度和时间
     */
    private static void calculateWithProgress(PiEngine engine, int digits, String outputFile) throws Exception {
        long startTime = System.currentTimeMillis();
        int iterations = digits / 14 + 1;

        // 显示计算信息
        System.out.println("╔════════════════════════════════════════════════════════");
        System.out.println("║                    π 值计算任务                         ");
        System.out.println("╠════════════════════════════════════════════════════════");
        System.out.printf("║  目标精度：%d 位%n", digits);
        System.out.printf("║  迭代次数：%d 次 (Chudnovsky 算法，每项~14.18 位)%n", iterations);
        System.out.printf("║  并行线程：%d (CPU 核心数)%n", Runtime.getRuntime().availableProcessors());
        System.out.println("╚════════════════════════════════════════════════════════");
        System.out.println();

        // 开始计算
        System.out.println("[1/3] 执行 Binary Splitting 并行计算...");
        long splitStart = System.currentTimeMillis();
        Result result = engine.binarySplit(0, iterations);
        long splitEnd = System.currentTimeMillis();
        long splitTime = splitEnd - splitStart;

        System.out.println("      ✓ Binary Splitting 完成，耗时：" + formatDuration(splitTime));
        System.out.printf("      ✓ 计算结果：T 值 %d 位，Q 值 %d 位%n",
            result.T.toString().length(),
            result.Q.toString().length());
        System.out.println();

        // 计算π值
        System.out.println("[2/3] 计算π值 (426880 × √10005 × Q / T)...");
        long piStart = System.currentTimeMillis();

        int extraPrecision = digits + 100;
        java.math.MathContext mc = new java.math.MathContext(extraPrecision);

        java.math.BigDecimal sqrt10005 = new java.math.BigDecimal("10005").sqrt(mc);
        java.math.BigDecimal bigQ = new java.math.BigDecimal(result.Q);
        java.math.BigDecimal bigT = new java.math.BigDecimal(result.T);
        java.math.BigDecimal bigMultiplier = new java.math.BigDecimal("426880");

        java.math.BigDecimal numerator = bigMultiplier.multiply(sqrt10005, mc).multiply(bigQ, mc);
        java.math.BigDecimal piDecimal = numerator.divide(bigT, mc);

        long piEnd = System.currentTimeMillis();
        long piTime = piEnd - piStart;

        System.out.println("      ✓ π值计算完成，耗时：" + formatDuration(piTime));
        System.out.println();

        // 流式输出到文件
        System.out.println("[3/3] 流式输出到文件：" + outputFile);
        long writeStart = System.currentTimeMillis();

        try {
            StreamingDivision.streamPi(result.Q, result.T, digits, outputFile);
        } catch (Exception e) {
            System.err.println("      ✗ 文件写入失败：" + e.getMessage());
            e.printStackTrace();
            throw e;
        }

        long writeEnd = System.currentTimeMillis();
        long writeTime = writeEnd - writeStart;

        System.out.println("      ✓ 文件写入完成，耗时：" + formatDuration(writeTime));
        System.out.println();

        // 总耗时
        long totalTime = System.currentTimeMillis() - startTime;

        // 显示最终统计
        System.out.println("╔════════════════════════════════════════════════════════");
        System.out.println("║                    计算完成 ✓                          ");
        System.out.println("╠════════════════════════════════════════════════════════");
        System.out.printf("║  总耗时：%s%n", formatDuration(totalTime));
        System.out.printf("║  计算速度：%.2f 位/秒%n", digits * 1000.0 / totalTime);
        System.out.printf("║  输出文件：%s%n", outputFile);
        System.out.println("╚════════════════════════════════════════════════════════");
    }

    /**
     * 格式化时间显示
     */
    private static String formatDuration(long millis) {
        if (millis < 1000) {
            return millis + " 毫秒";
        } else if (millis < 60000) {
            return String.format("%.2f 秒", millis / 1000.0);
        } else if (millis < 3600000) {
            long minutes = millis / 60000;
            long seconds = (millis % 60000) / 1000;
            return String.format("%d 分 %d 秒", minutes, seconds);
        } else {
            long hours = millis / 3600000;
            long minutes = (millis % 3600000) / 60000;
            long seconds = (millis % 60000) / 1000;
            return String.format("%d 小时 %d 分 %d 秒", hours, minutes, seconds);
        }
    }

    /**
     * 计算π值并输出到文件
     * @param digits 要计算的位数
     * @param outputFile 输出文件名
     * @throws Exception 计算过程中的异常
     */
    public static void calculateAndSavePi(int digits, String outputFile) throws Exception {
        PiEngine engine = new PiEngine();
        calculateWithProgress(engine, digits, outputFile);
        engine.shutdown();
        CheckpointManager.removeCheckpoint();
    }
}
