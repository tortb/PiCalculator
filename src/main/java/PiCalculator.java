
/**
 * 高性能 π 计算器主类 - 支持 1 亿 -10 亿位计算
 *
 * 使用方法：
 *   java -jar PiCalculator.jar <位数> [输出文件]
 *
 * 示例：
 *   java -jar PiCalculator.jar 1000           # 快速测试
 *   java -jar PiCalculator.jar 1000000        # 100 万位
 *   java -jar PiCalculator.jar 100000000      # 1 亿位
 *
 * JVM 参数建议：
 *   -Xms8G -Xmx16G     # 根据位数调整内存
 *   -XX:+UseG1GC       # 使用 G1 垃圾收集器
 *   -XX:MaxGCPauseMillis=100  # 控制 GC 暂停时间
 *   -XX:+AlwaysPreTouch     # 预触内存页
 *   -XX:+UseNUMA            # 启用 NUMA 优化（多路 CPU）
 */
public class PiCalculator {
    
    // ==================== 内存配置建议 ====================
    
    /** 100 万位推荐内存 (MB) */
    private static final int RECOMMENDED_MEM_1M = 512;
    
    /** 1000 万位推荐内存 (MB) */
    private static final int RECOMMENDED_MEM_10M = 2048;
    
    /** 1 亿位推荐内存 (MB) */
    private static final int RECOMMENDED_MEM_100M = 8192;
    
    /** 10 亿位推荐内存 (MB) */
    private static final int RECOMMENDED_MEM_1B = 32768;
    
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
            
            // 检查内存配置
            checkMemoryConfiguration(digits);

            // 创建 π 计算引擎
            PiEngine engine = new PiEngine();

            // 计算并显示进度和时间
            calculateWithProgress(engine, digits, outputFilename);

            // 清理资源
            engine.shutdown();

        } catch (NumberFormatException e) {
            System.out.println("错误：请输入有效的数字作为位数");
        } catch (Exception e) {
            System.err.println("计算过程中发生错误：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 检查内存配置是否足够
     */
    private static void checkMemoryConfiguration(int digits) {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);  // MB
        long recommendedMem = getRecommendedMemory(digits);
        
        if (maxMemory < recommendedMem * 0.5) {
            System.out.println("⚠️  警告：当前内存配置可能不足");
            System.out.printf("   当前最大堆：%,d MB%n", maxMemory);
            System.out.printf("   推荐最小：%,d MB%n", recommendedMem);
            System.out.println("   建议使用：-Xms" + recommendedMem + "m -Xmx" + (recommendedMem * 2) + "m");
            System.out.println();
        }
    }
    
    /**
     * 获取推荐内存大小
     */
    private static long getRecommendedMemory(int digits) {
        if (digits >= 1_000_000_000) {
            return RECOMMENDED_MEM_1B;
        } else if (digits >= 100_000_000) {
            return RECOMMENDED_MEM_100M;
        } else if (digits >= 10_000_000) {
            return RECOMMENDED_MEM_10M;
        } else {
            return RECOMMENDED_MEM_1M;
        }
    }

    /**
     * 打印使用说明
     */
    private static void printUsage() {
        System.out.println("╔═══════════════════════════════════════════════════════════");
        System.out.println("║              HPC π 计算器 v2.0 - 使用说明                 ");
        System.out.println("╠═══════════════════════════════════════════════════════════");
        System.out.println("║  用法：java -jar PiCalculator.jar <位数> [输出文件]      ");
        System.out.println("║                                                           ");
        System.out.println("║  示例：                                                   ");
        System.out.println("║    java -jar PiCalculator.jar 1000         # 快速测试    ");
        System.out.println("║    java -jar PiCalculator.jar 1000000      # 100 万位     ");
        System.out.println("║    java -jar PiCalculator.jar 100000000    # 1 亿位       ");
        System.out.println("║                                                           ");
        System.out.println("║  JVM 参数建议：                                           ");
        System.out.println("║    100 万位：  -Xms512m -Xmx1g -XX:+UseG1GC               ");
        System.out.println("║    1000 万位： -Xms2g -Xmx4g -XX:+UseG1GC                 ");
        System.out.println("║    1 亿位：    -Xms8g -Xmx16g -XX:+UseG1GC                ");
        System.out.println("║    10 亿位：   -Xms32g -Xmx64g -XX:+UseG1GC               ");
        System.out.println("╚═══════════════════════════════════════════════════════════");
    }

    /**
     * 计算 π 值并显示进度和时间
     */
    private static void calculateWithProgress(PiEngine engine, int digits, String outputFile) throws Exception {
        long startTime = System.currentTimeMillis();
        int iterations = digits / 14 + 1;

        // 显示系统信息
        printSystemInfo(digits, iterations, engine.getParallelism());

        // 开始计算（流式输出）
        engine.calculatePiStream(digits, outputFile);

        // 总耗时
        long totalTime = System.currentTimeMillis() - startTime;

        // 显示最终统计
        printFinalStatistics(digits, totalTime, outputFile);
    }
    
    /**
     * 打印系统信息
     */
    private static void printSystemInfo(int digits, int iterations, int parallelism) {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();

        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════");
        System.out.println("║                      系统信息                            ");
        System.out.println("╠═══════════════════════════════════════════════════════════");
        System.out.printf("║  CPU 核心数：%,d (使用 %d 线程)%n", runtime.availableProcessors(), parallelism);
        System.out.printf("║  最大堆内存：%,d MB%n", maxMemory / (1024 * 1024));
        System.out.printf("║  已分配内存：%,d MB%n", totalMemory / (1024 * 1024));
        System.out.printf("║  空闲内存：%,d MB%n", freeMemory / (1024 * 1024));
        System.out.printf("║  Java 版本：%s%n", System.getProperty("java.version"));
        System.out.printf("║  JVM 名称：%s%n", System.getProperty("java.vm.name"));
        System.out.println("╚═══════════════════════════════════════════════════════════");
        System.out.println();
    }
    
    /**
     * 打印最终统计信息
     */
    private static void printFinalStatistics(int digits, long totalTime, String outputFile) {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════");
        System.out.println("║                    计算完成 ✓                            ");
        System.out.println("╠═══════════════════════════════════════════════════════════");
        System.out.printf("║  目标精度：%,d 位 (%.2f MB)%n", digits, digits / 1_000_000.0);
        System.out.printf("║  总耗时：%s%n", formatDuration(totalTime));
        System.out.printf("║  计算速度：%,.2f 位/秒%n", digits * 1000.0 / totalTime);
        System.out.printf("║  输出文件：%s%n", outputFile);
        System.out.println("╚═══════════════════════════════════════════════════════════");
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
        } else if (millis < 86400000) {
            long hours = millis / 3600000;
            long minutes = (millis % 3600000) / 60000;
            long seconds = (millis % 60000) / 1000;
            return String.format("%d 小时 %d 分 %d 秒", hours, minutes, seconds);
        } else {
            long days = millis / 86400000;
            long hours = (millis % 86400000) / 3600000;
            long minutes = (millis % 3600000) / 60000;
            return String.format("%d 天 %d 小时 %d 分", days, hours, minutes);
        }
    }

    /**
     * 计算 π 值并输出到文件（兼容旧接口）
     */
    public static void calculateAndSavePi(int digits, String outputFile) throws Exception {
        PiEngine engine = new PiEngine();
        calculateWithProgress(engine, digits, outputFile);
        engine.shutdown();
        CheckpointManager.removeCheckpoint();
    }
}
