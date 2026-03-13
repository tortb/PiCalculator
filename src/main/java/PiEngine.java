import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

/**
 * HPC 优化版 π计算引擎
 * 
 * 优化点：
 * 1. 分段计算 - 对 1 亿位以上拆分迭代区间，降低单次计算内存压力
 * 2. 动态并行度 - 根据计算规模自动调整 ForkJoinPool 大小
 * 3. 内存管理 - 及时释放不再需要的中间结果
 * 4. Checkpoint 恢复 - 支持从检查点恢复计算
 * 5. NUMA 优化 - 线程亲和性设置
 */
public class PiEngine {
    // Chudnovsky 算法常量
    private static final BigInteger MULTIPLIER = new BigInteger("426880");
    private static final BigDecimal SQRT_10005_PRECISION = new BigDecimal("100.02499875626060594479218787635777800159502436869631465713551156965096785386430429231118794849997329775519389");
    
    // 分段计算配置
    private static final int SEGMENT_SIZE_100M = 1000000;  // 1 亿位时每段 100 万次迭代
    private static final int SEGMENT_SIZE_10M = 100000;    // 1000 万位时每段 10 万次迭代
    
    private final ForkJoinPool forkJoinPool;
    private final int parallelism;
    private final boolean isLargeCalculation;  // 是否为大规模计算

    /**
     * 构造函数 - 自动优化配置
     */
    public PiEngine() {
        this.parallelism = Runtime.getRuntime().availableProcessors();
        
        // 优化 1: 对于大规模计算，使用略少的线程数以减少上下文切换
        int optimalParallelism = Math.max(1, parallelism - 1);
        this.forkJoinPool = new ForkJoinPool(optimalParallelism);
        
        // 优化 2: NUMA 优化
        NUMAThreadManager.initializeNUMA();
        
        this.isLargeCalculation = false;
    }

    /**
     * 构造函数 - 指定并行度
     */
    public PiEngine(int customParallelism) {
        this.parallelism = customParallelism;
        this.forkJoinPool = new ForkJoinPool(customParallelism);
        NUMAThreadManager.initializeNUMA();
        this.isLargeCalculation = false;
    }

    /**
     * 计算π到指定精度（返回字符串，适用于中小规模计算）
     */
    public String calculatePi(int digits) throws InterruptedException {
        int iterations = digits / 14 + 1;
        Result result = computeResult(digits, iterations);
        return computePiFromString(result, digits);
    }

    /**
     * 计算π到指定精度并流式输出（适用于大规模计算）
     * 优化：不生成完整字符串，直接流式写入文件
     */
    public void calculatePiStream(int digits, String outputFile) throws Exception {
        int iterations = digits / 14 + 1;
        
        System.out.println("╔════════════════════════════════════════════════════════");
        System.out.println("║                    π 值计算任务                         ");
        System.out.println("╠════════════════════════════════════════════════════════");
        System.out.printf("║  目标精度：%d 位%n", digits);
        System.out.printf("║  迭代次数：%d 次 (Chudnovsky 算法，每项~14.18 位)%n", iterations);
        System.out.printf("║  并行线程：%d (CPU 核心数)%n", parallelism);
        System.out.printf("║  计算模式：%s%n", isLargeCalculation ? "分段计算" : "单次计算");
        System.out.println("╚════════════════════════════════════════════════════════");
        System.out.println();

        // 重置检查点计数器
        CheckpointManager.resetCounter();

        // 加载检查点或从头计算
        Result result = computeResult(digits, iterations);

        // 流式输出到文件
        streamPiToFile(result, digits, outputFile);
    }

    /**
     * 计算最终结果（支持分段计算）
     */
    private Result computeResult(int digits, int iterations) throws InterruptedException {
        // 检查是否有检查点
        CheckpointManager.CheckpointData checkpoint = CheckpointManager.loadCheckpoint();
        
        if (checkpoint != null) {
            System.out.println("[检查点] 从迭代 " + checkpoint.iteration + " 恢复计算");
            return resumeFromCheckpoint(checkpoint, iterations);
        }
        
        // 根据位数决定是否分段计算
        if (digits >= 100_000_000) {
            // 1 亿位以上：分段计算
            return computeSegmented(iterations, SEGMENT_SIZE_100M);
        } else if (digits >= 10_000_000) {
            // 1000 万位以上：中等分段
            return computeSegmented(iterations, SEGMENT_SIZE_10M);
        } else {
            // 1000 万位以下：单次计算
            return binarySplit(0, iterations);
        }
    }

    /**
     * 分段计算（适用于 1 亿位以上）
     * 优化：将大区间拆分为多个小区间，分别计算后合并
     */
    private Result computeSegmented(int totalIterations, int segmentSize) throws InterruptedException {
        System.out.println("[分段计算] 将 " + totalIterations + " 次迭代拆分为多个段，每段约 " + segmentSize + " 次");
        
        Result accumulatedResult = null;
        int segmentStart = 0;
        int segmentCount = 0;
        
        while (segmentStart < totalIterations) {
            int segmentEnd = Math.min(segmentStart + segmentSize, totalIterations);
            int currentSegmentSize = segmentEnd - segmentStart;
            
            System.out.printf("[分段 %d] 计算迭代 [%d, %d)，共 %d 次...%n", 
                ++segmentCount, segmentStart, segmentEnd, currentSegmentSize);
            
            long segStart = System.nanoTime();
            Result segmentResult = binarySplit(segmentStart, segmentEnd);
            long segTime = System.nanoTime() - segStart;
            
            System.out.printf("          ✓ 分段 %d 完成，耗时 %.2f 秒%n", 
                segmentCount, segTime / 1_000_000_000.0);
            
            // 合并结果
            if (accumulatedResult == null) {
                accumulatedResult = segmentResult;
            } else {
                accumulatedResult = mergeSegmentResults(accumulatedResult, segmentResult);
                // 优化：释放大对象，帮助 GC
                segmentResult = null;
                System.gc();  // 在大规模计算中，适时触发 GC 释放内存
            }
            
            segmentStart = segmentEnd;
        }
        
        System.out.println("[分段计算] 所有段计算完成，共 " + segmentCount + " 个段");
        return accumulatedResult;
    }

    /**
     * 合并两个分段的结果
     */
    private Result mergeSegmentResults(Result left, Result right) {
        // Binary Splitting 合并公式
        BigInteger P = left.P.multiply(right.P);
        BigInteger Q = left.Q.multiply(right.Q);
        BigInteger T = left.T.multiply(right.Q).add(left.P.multiply(right.T));
        return new Result(P, Q, T);
    }

    /**
     * 从检查点恢复计算
     */
    private Result resumeFromCheckpoint(CheckpointManager.CheckpointData checkpoint, int totalIterations) {
        System.out.println("[恢复] 从迭代 " + checkpoint.iteration + " 继续计算到 " + totalIterations);
        
        long resumeStart = System.nanoTime();
        Result remainingResult = binarySplit(checkpoint.iteration, totalIterations);
        long resumeTime = System.nanoTime() - resumeStart;
        
        System.out.printf("[恢复] 剩余部分计算完成，耗时 %.2f 秒%n", resumeTime / 1_000_000_000.0);
        
        // 合并检查点结果和剩余结果
        return mergeSegmentResults(checkpoint.result, remainingResult);
    }

    /**
     * 使用 Binary Splitting 算法计算 P, Q, T
     * 优化：动态调整 threshold 和最大深度
     */
    public Result binarySplit(int start, int end) {
        int range = end - start;
        
        // 优化 1: 动态计算 threshold
        // threshold 太小会导致过多 fork/join 开销，太大会降低并行度
        int baseThreshold = Math.max(10, range / (parallelism * 8));
        int threshold = Math.min(Math.max(baseThreshold, 50), 500);
        
        // 优化 2: 根据范围计算最大深度
        // 深度太深会导致过多小任务，增加调度开销
        int maxDepth = Math.max(3, 20 - (range / 10000));
        maxDepth = Math.min(maxDepth, 15);
        
        // 创建任务
        BinarySplitTask task = new BinarySplitTask(start, end, threshold, maxDepth, 0);
        
        return forkJoinPool.invoke(task);
    }

    /**
     * 从 Result 计算π值（字符串形式）
     */
    private String computePiFromString(Result result, int digits) {
        int extraPrecision = digits + 100;
        MathContext mc = new MathContext(extraPrecision);

        BigDecimal sqrt10005 = new BigDecimal("10005").sqrt(mc);
        BigDecimal bigQ = new BigDecimal(result.Q);
        BigDecimal bigT = new BigDecimal(result.T);
        BigDecimal bigMultiplier = new BigDecimal(MULTIPLIER);

        // π = (426880 * sqrt(10005) * Q) / T
        BigDecimal numerator = bigMultiplier.multiply(sqrt10005, mc).multiply(bigQ, mc);
        BigDecimal piDecimal = numerator.divide(bigT, mc);

        String piStr = piDecimal.toPlainString().replace(".", "");
        
        if (piStr.length() > digits + 1) {
            piStr = piStr.substring(0, digits + 1);
        }

        if (piStr.length() > 1) {
            return piStr.charAt(0) + "." + piStr.substring(1);
        }
        return piStr;
    }

    /**
     * 流式输出π值到文件
     * 优化：使用 StreamingDivision 的流式除法，不生成完整字符串
     */
    private void streamPiToFile(Result result, int digits, String outputFile) throws Exception {
        System.out.println("[输出] 开始流式输出到文件：" + outputFile);
        
        long streamStart = System.nanoTime();
        StreamingDivision.streamPi(result.Q, result.T, digits, outputFile);
        long streamTime = System.nanoTime() - streamStart;
        
        System.out.printf("[输出] 流式输出完成，耗时 %.2f 秒%n", streamTime / 1_000_000_000.0);
        System.out.println("π值已计算完成，结果保存到：" + outputFile);
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

    /**
     * 获取当前并行度
     */
    public int getParallelism() {
        return parallelism;
    }
}
