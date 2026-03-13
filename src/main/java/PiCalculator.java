import java.io.IOException;
import java.math.BigInteger;

/**
 * HPC 优化版 π计算器主类
 * 
 * 优化点：
 * 1. 详细进度显示 - 显示计算量、预计时间、速度
 * 2. 内存监控 - 显示当前内存使用情况
 * 3. 性能统计 - 显示各阶段耗时和计算速度
 */
public class PiCalculator {
    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
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
     * 打印使用说明
     */
    private static void printUsage() {
        System.out.println("╔════════════════════════════════════════════════════════");
        System.out.println("║              HPC π 计算器 - 使用说明                    ");
        System.out.println("╠════════════════════════════════════════════════════════");
        System.out.println("║  用法：java -jar PiCalculator.jar <位数> [输出文件]    ");
        System.out.println("║                                                        ");
        System.out.println("║  示例:                                                 ");
        System.out.println("║    java -jar PiCalculator.jar 1000       # 快速测试   ");
        System.out.println("║    java -jar PiCalculator.jar 1000000    # 100 万位    ");
        System.out.println("║    java -jar PiCalculator.jar 100000000  # 1 亿位      ");
        System.out.println("║                                                        ");
        System.out.println("║  JVM 参数建议:                                         ");
        System.out.println("║    -Xms8G -Xmx16G     # 根据位数调整内存               ");
        System.out.println("║    -XX:+UseG1GC       # 使用 G1 垃圾收集器             ");
        System.out.println("║    -XX:+UseNUMA       # 启用 NUMA 优化（多路 CPU）      ");
        System.out.println("║    -XX:+AlwaysPreTouch  # 预触内存页                   ");
        System.out.println("╚════════════════════════════════════════════════════════");
    }

    /**
     * 计算π值并显示进度和时间
     */
    private static void calculateWithProgress(PiEngine engine, int digits, String outputFile) throws Exception {
        long startTime = System.currentTimeMillis();
        int iterations = digits / 14 + 1;

        // 显示内存信息
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();

        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════");
        System.out.println("║                    系统信息                            ");
        System.out.println("╠════════════════════════════════════════════════════════");
        System.out.printf("║  CPU 核心数：%d%n", runtime.availableProcessors());
        System.out.printf("║  最大堆内存：%,d MB%n", maxMemory / (1024 * 1024));
        System.out.printf("║  已分配内存：%,d MB%n", totalMemory / (1024 * 1024));
        System.out.printf("║  空闲内存：%,d MB%n", freeMemory / (1024 * 1024));
        System.out.println("╚════════════════════════════════════════════════════════");
        System.out.println();

        // 开始计算
        System.out.println("[1/3] 执行 Binary Splitting 并行计算...");
        System.out.printf("      迭代范围：[0, %d)，共 %d 次迭代%n", iterations, iterations);
        
        long splitStart = System.currentTimeMillis();
        Result result = engine.binarySplit(0, iterations);
        long splitEnd = System.currentTimeMillis();
        long splitTime = splitEnd - splitStart;

        System.out.println("      ✓ Binary Splitting 完成");
        System.out.printf("      ✓ 耗时：%s%n", formatDuration(splitTime));
        System.out.printf("      ✓ 计算结果：T 值 %,d 位，Q 值 %,d 位%n",
            result.T.toString().length(),
            result.Q.toString().length());
        
        // 显示计算速度
        double calcSpeed = iterations * 1000.0 / splitTime;
        System.out.printf("      ✓ 计算速度：%.2f 次迭代/秒%n", calcSpeed);
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

        System.out.println("      ✓ π值计算完成");
        System.out.printf("      ✓ 耗时：%s%n", formatDuration(piTime));
        System.out.println();

        // 流式输出到文件
        System.out.println("[3/3] 流式输出到文件：" + outputFile);
        long writeStart = System.currentTimeMillis();

        StreamingDivision.streamPi(result.Q, result.T, digits, outputFile);

        long writeEnd = System.currentTimeMillis();
        long writeTime = writeEnd - writeStart;

        System.out.println("      ✓ 文件写入完成");
        System.out.printf("      ✓ 耗时：%s%n", formatDuration(writeTime));
        System.out.println();

        // 总耗时
        long totalTime = System.currentTimeMillis() - startTime;

        // 显示最终统计
        System.out.println("╔════════════════════════════════════════════════════════");
        System.out.println("║                    计算完成 ✓                          ");
        System.out.println("╠════════════════════════════════════════════════════════");
        System.out.printf("║  目标精度：%,d 位%n", digits);
        System.out.printf("║  总耗时：%s%n", formatDuration(totalTime));
        System.out.printf("║  计算速度：%,.2f 位/秒%n", digits * 1000.0 / totalTime);
        System.out.printf("║  阶段耗时:%n");
        System.out.printf("║    - Binary Splitting: %s (%.1f%%)%n", 
            formatDuration(splitTime), splitTime * 100.0 / totalTime);
        System.out.printf("║    - π值计算：%s (%.1f%%)%n", 
            formatDuration(piTime), piTime * 100.0 / totalTime);
        System.out.printf("║    - 文件输出：%s (%.1f%%)%n", 
            formatDuration(writeTime), writeTime * 100.0 / totalTime);
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
     * 计算π值并输出到文件（兼容旧接口）
     */
    public static void calculateAndSavePi(int digits, String outputFile) throws Exception {
        PiEngine engine = new PiEngine();
        calculateWithProgress(engine, digits, outputFile);
        engine.shutdown();
        CheckpointManager.removeCheckpoint();
    }
}
