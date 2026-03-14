import java.math.BigInteger;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

/**
 * 工业级 π 计算引擎
 * 支持万亿位 π 计算，带实时进度显示
 */
public class PiEngine {

    // ==================== 分段计算配置 ====================

    private static final int SEGMENT_SIZE_100M = 500_000;
    private static final int SEGMENT_SIZE_500M = 200_000;
    private static final int SEGMENT_SIZE_1B = 100_000;

    // ==================== 实例变量 ====================

    private final ForkJoinPool forkJoinPool;
    private final int parallelism;

    // ==================== 构造函数 ====================

    public PiEngine() {
        this.parallelism = Runtime.getRuntime().availableProcessors();
        int optimalParallelism = Math.max(1, parallelism);
        this.forkJoinPool = new ForkJoinPool(optimalParallelism);
        System.out.printf("[引擎] 初始化完成，并行度：%d%n", optimalParallelism);
    }

    public PiEngine(int customParallelism) {
        this.parallelism = customParallelism;
        this.forkJoinPool = new ForkJoinPool(customParallelism);
    }

    /**
     * 计算 π 到指定精度并流式输出
     */
    public void calculatePiStream(int digits, String outputFile) throws Exception {
        int iterations = digits / 14 + 1;

        printTaskInfo(digits, iterations);

        CheckpointManager.resetCounter();

        Result result = computeResult(digits, iterations);

        streamPiToFile(result, digits, outputFile);

        CheckpointManager.removeCheckpoint();
    }

    /**
     * 打印任务信息
     */
    private void printTaskInfo(int digits, int iterations) {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();

        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════");
        System.out.println("║                      π 值计算任务                         ");
        System.out.println("╠═══════════════════════════════════════════════════════════");
        System.out.printf("║  目标精度：%,d 位 (%.1f MB 输出)%n", digits, digits / 1_000_000.0);
        System.out.printf("║  迭代次数：%,d 次 (Chudnovsky 算法，每项~14.18 位)%n", iterations);
        System.out.printf("║  CPU 核心数：%d (使用 %d 线程)%n", runtime.availableProcessors(), parallelism);
        System.out.printf("║  最大堆内存：%,d MB%n", maxMemory / (1024 * 1024));
        System.out.printf("║  计算模式：%s%n", getCalculationMode(digits));
        System.out.println("╚═══════════════════════════════════════════════════════════");
        System.out.println();
    }

    private String getCalculationMode(int digits) {
        if (digits >= 1_000_000_000) {
            return "超大规模分段 (每段 " + SEGMENT_SIZE_1B + " 迭代)";
        } else if (digits >= 500_000_000) {
            return "大规模分段 (每段 " + SEGMENT_SIZE_500M + " 迭代)";
        } else if (digits >= 100_000_000) {
            return "中等规模分段 (每段 " + SEGMENT_SIZE_100M + " 迭代)";
        } else {
            return "单次计算";
        }
    }

    /**
     * 计算最终结果
     */
    private Result computeResult(int digits, int iterations) throws InterruptedException {
        CheckpointManager.CheckpointData checkpoint = CheckpointManager.loadCheckpoint();

        if (checkpoint != null && checkpoint.iteration > 0) {
            System.out.println("[检查点] 从迭代 " + checkpoint.iteration + " 恢复计算");
            return resumeFromCheckpoint(checkpoint, iterations);
        }

        if (digits >= 1_000_000_000) {
            return computeSegmented(iterations, SEGMENT_SIZE_1B);
        } else if (digits >= 500_000_000) {
            return computeSegmented(iterations, SEGMENT_SIZE_500M);
        } else if (digits >= 100_000_000) {
            return computeSegmented(iterations, SEGMENT_SIZE_100M);
        } else {
            return computeSingle(iterations);
        }
    }

    /**
     * 单次计算
     */
    private Result computeSingle(int iterations) {
        System.out.println("[计算] 执行单次 Binary Splitting...");
        System.out.printf("       迭代范围：[0, %,d)%n", iterations);
        System.out.printf("       [并行配置] 线程数：%d%n", parallelism);

        // 重置进度计数器
        BinarySplitTask.resetProgress();

        // 启动进度监控线程
        BinarySplitTask.ProgressMonitor monitor = new BinarySplitTask.ProgressMonitor(iterations);
        Thread monitorThread = new Thread(monitor);
        monitorThread.start();

        long startTime = System.nanoTime();
        Result result = binarySplit(0, iterations);
        long splitTime = System.nanoTime() - startTime;

        // 停止监控线程
        monitor.stop();
        try {
            monitorThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.printf("       ✓ Binary Splitting 完成，耗时 %.2f 秒%n", splitTime / 1_000_000_000.0);
        System.out.printf("       ✓ T 值位数：%,d%n", result.T.toString().length());
        System.out.printf("       ✓ Q 值位数：%,d%n", result.Q.toString().length());

        return result;
    }

    /**
     * 分段计算
     */
    private Result computeSegmented(int totalIterations, int segmentSize) throws InterruptedException {
        int segmentCount = (totalIterations + segmentSize - 1) / segmentSize;

        System.out.println("[分段计算] 开始大规模计算...");
        System.out.printf("            总迭代数：%,d%n", totalIterations);
        System.out.printf("            分段大小：%,d 次迭代/段%n", segmentSize);
        System.out.printf("            预计分段：%,d 段%n", segmentCount);
        System.out.println();

        Result accumulatedResult = null;
        int segmentStart = 0;
        int completedSegments = 0;

        CheckpointManager.CheckpointData checkpoint = CheckpointManager.loadCheckpoint();
        if (checkpoint != null && checkpoint.iteration > 0) {
            segmentStart = (checkpoint.iteration / segmentSize) * segmentSize;
            accumulatedResult = checkpoint.result;
            completedSegments = segmentStart / segmentSize;
            System.out.printf("[恢复] 已从段 %d (迭代 %d) 恢复%n", completedSegments, segmentStart);
        }

        long binarySplitStartTime = System.nanoTime();

        while (segmentStart < totalIterations) {
            int segmentEnd = Math.min(segmentStart + segmentSize, totalIterations);

            long segStart = System.nanoTime();
            Result segmentResult = binarySplit(segmentStart, segmentEnd);
            long segTime = System.nanoTime() - segStart;

            completedSegments++;

            if (accumulatedResult == null) {
                accumulatedResult = segmentResult;
            } else {
                accumulatedResult = mergeSegmentResults(accumulatedResult, segmentResult);
                segmentResult = null;

                if (completedSegments % 5 == 0) {
                    long usedMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
                    long maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024);
                    if (usedMemory > maxMemory * 0.8) {
                        System.out.printf("            [GC] 内存使用 %.0f%%，触发 GC...%n", usedMemory * 100.0 / maxMemory);
                        System.gc();
                    }
                }
            }

            double progress = segmentEnd * 100.0 / totalIterations;
            double elapsedSec = (System.nanoTime() - binarySplitStartTime) / 1_000_000_000.0;
            double speed = segmentEnd / elapsedSec;
            double remainingSec = (totalIterations - segmentEnd) / speed;
            String remainingTime = formatRemainingTime(remainingSec);

            System.out.printf("            ✓ 段 %d/%d 完成 [%d-%d)，耗时 %.2f 秒 (总进度 %.1f%%, 剩余 %s)%n",
                completedSegments, segmentCount, segmentStart, segmentEnd,
                segTime / 1_000_000_000.0, progress, remainingTime);

            CheckpointManager.saveCheckpoint(segmentEnd, accumulatedResult);

            segmentStart = segmentEnd;
        }

        long totalTime = System.nanoTime() - binarySplitStartTime;
        System.out.println();
        System.out.printf("[分段计算] 所有 %d 段完成，总耗时 %.2f 秒%n", segmentCount, totalTime / 1_000_000_000.0);

        return accumulatedResult;
    }

    /**
     * 从检查点恢复
     */
    private Result resumeFromCheckpoint(CheckpointManager.CheckpointData checkpoint, int totalIterations) {
        System.out.printf("[恢复] 从迭代 %,d 继续计算到 %,d%n", checkpoint.iteration, totalIterations);

        long startTime = System.nanoTime();
        Result remainingResult = binarySplit(checkpoint.iteration, totalIterations);
        long resumeTime = System.nanoTime() - startTime;

        System.out.printf("[恢复] 剩余部分计算完成，耗时 %.2f 秒%n", resumeTime / 1_000_000_000.0);

        return mergeSegmentResults(checkpoint.result, remainingResult);
    }

    /**
     * 合并两个分段的结果
     */
    private Result mergeSegmentResults(Result left, Result right) {
        BigInteger P = left.P.multiply(right.P);
        BigInteger Q = left.Q.multiply(right.Q);
        BigInteger T = left.T.multiply(right.Q).add(left.P.multiply(right.T));
        return new Result(P, Q, T);
    }

    /**
     * 使用 Binary Spliting 算法计算 P, Q, T
     */
    public Result binarySplit(int start, int end) {
        int range = end - start;

        // 更激进的并行策略
        int baseThreshold = Math.max(5, range / (parallelism * 16));
        int threshold = Math.min(Math.max(baseThreshold, 20), 200);

        int maxDepth = Math.max(5, 25 - (range / 50000));
        maxDepth = Math.min(maxDepth, 20);

        BinarySplitTask task = new BinarySplitTask(start, end, threshold, maxDepth, 0);
        return forkJoinPool.invoke(task);
    }

    /**
     * 流式输出 π 值到文件
     */
    private void streamPiToFile(Result result, int digits, String outputFile) throws Exception {
        System.out.println();
        System.out.println("[π 值计算] 计算分子 = 426880 × √10005 × Q...");

        int extraPrecision = Math.max(100, digits / 10);
        java.math.MathContext mc = new java.math.MathContext(extraPrecision);

        java.math.BigDecimal sqrt10005 = java.math.BigDecimal.valueOf(10005).sqrt(mc);
        java.math.BigDecimal constant = new java.math.BigDecimal("426880").multiply(sqrt10005, mc);
        java.math.BigDecimal bigQ = new java.math.BigDecimal(result.Q);
        java.math.BigDecimal numeratorBd = constant.multiply(bigQ, mc);

        BigInteger numerator = numeratorBd.toBigInteger();

        System.out.printf("[π 值计算] 完成，分子位数：%,d%n", numerator.toString().length());

        System.out.println();
        System.out.println("[输出] 开始流式输出到文件：" + outputFile);

        StreamingDivisionEngine.streamPi(
            numerator,
            result.T,
            digits,
            java.nio.file.Paths.get(outputFile)
        );

        System.out.println("π 值已保存到：" + outputFile);
    }

    /**
     * 关闭线程池
     */
    public void shutdown() {
        try {
            forkJoinPool.shutdown();
            if (!forkJoinPool.awaitTermination(60, TimeUnit.SECONDS)) {
                forkJoinPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            forkJoinPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public int getParallelism() {
        return parallelism;
    }

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
