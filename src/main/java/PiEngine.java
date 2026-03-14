import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 高性能 π 计算引擎 - 支持 1 亿 -10 亿位计算
 *
 * 核心优化点：
 * 1. 分段计算 - 将大区间拆分为小区间，降低内存压力
 * 2. 动态并行度 - 根据计算规模自动调整 ForkJoinPool
 * 3. 内存管理 - 及时释放中间结果，触发 GC
 * 4. Checkpoint 恢复 - 支持从检查点恢复
 * 5. 进度监控 - 实时显示计算进度和速度
 * 6. JVM 优化 - 针对大堆内存优化
 *
 * 内存优化策略：
 * - 1 亿位以下：单次 Binary Splitting
 * - 1 亿 -5 亿位：分段计算，每段 50 万迭代
 * - 5 亿位以上：分段计算，每段 20 万迭代
 */
public class PiEngine {
    
    // ==================== Chudnovsky 算法常量 ====================
    
    private static final BigInteger MULTIPLIER = new BigInteger("426880");
    
    // ==================== 分段计算配置 ====================
    
    /** 1 亿位以上每段迭代数 */
    private static final int SEGMENT_SIZE_100M = 500_000;
    
    /** 5 亿位以上每段迭代数 */
    private static final int SEGMENT_SIZE_500M = 200_000;
    
    /** 10 亿位以上每段迭代数 */
    private static final int SEGMENT_SIZE_1B = 100_000;
    
    /** 检查点保存间隔（迭代次数） */
    private static final int CHECKPOINT_INTERVAL = 100_000;
    
    // ==================== 实例变量 ====================
    
    private final ForkJoinPool forkJoinPool;
    private final int parallelism;
    private final AtomicLong totalIterationsCompleted = new AtomicLong(0);
    
    // 性能统计
    private long binarySplitStartTime;
    private long piCalcStartTime;
    private long streamStartTime;
    
    /**
     * 构造函数 - 自动优化配置
     */
    public PiEngine() {
        this.parallelism = Runtime.getRuntime().availableProcessors();
        
        // 优化：对于大规模计算，使用略少的线程数减少上下文切换
        // 但保留足够的并行度
        int optimalParallelism = Math.max(1, parallelism);
        
        // 创建 ForkJoinPool，配置优化参数
        this.forkJoinPool = new ForkJoinPool(
            optimalParallelism,
            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            null,
            true  // asyncMode = true，使用 LIFO 策略，减少任务队列压力
        );
        
        System.out.printf("[引擎] 初始化完成，并行度：%d%n", optimalParallelism);
    }
    
    /**
     * 构造函数 - 指定并行度
     */
    public PiEngine(int customParallelism) {
        this.parallelism = customParallelism;
        this.forkJoinPool = new ForkJoinPool(customParallelism);
    }
    
    /**
     * 计算 π 到指定精度并流式输出
     * 
     * @param digits 目标精度（位数）
     * @param outputFile 输出文件路径
     */
    public void calculatePiStream(int digits, String outputFile) throws Exception {
        int iterations = digits / 14 + 1;
        
        printTaskInfo(digits, iterations);
        
        // 重置检查点计数器
        CheckpointManager.resetCounter();
        
        // ========== 阶段 1: Binary Splitting 计算 ==========
        Result result = computeResult(digits, iterations);
        
        // ========== 阶段 2: 流式输出 ==========
        streamPiToFile(result, digits, outputFile);
        
        // 清理检查点
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
    
    /**
     * 获取计算模式描述
     */
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
     * 计算最终结果（支持分段计算和检查点恢复）
     */
    private Result computeResult(int digits, int iterations) throws InterruptedException {
        // 检查是否有检查点
        CheckpointManager.CheckpointData checkpoint = CheckpointManager.loadCheckpoint();
        
        if (checkpoint != null && checkpoint.iteration > 0) {
            System.out.println("[检查点] 从迭代 " + checkpoint.iteration + " 恢复计算");
            return resumeFromCheckpoint(checkpoint, iterations);
        }
        
        // 根据位数决定计算策略
        if (digits >= 1_000_000_000) {
            return computeSegmented(iterations, SEGMENT_SIZE_1B, digits);
        } else if (digits >= 500_000_000) {
            return computeSegmented(iterations, SEGMENT_SIZE_500M, digits);
        } else if (digits >= 100_000_000) {
            return computeSegmented(iterations, SEGMENT_SIZE_100M, digits);
        } else {
            return computeSingle(iterations, digits);
        }
    }
    
    /**
     * 单次计算（适用于 1 亿位以下）
     */
    private Result computeSingle(int iterations, int digits) {
        System.out.println("[计算] 执行单次 Binary Splitting...");
        System.out.printf("       迭代范围：[0, %,d)%n", iterations);
        
        binarySplitStartTime = System.nanoTime();
        Result result = binarySplit(0, iterations);
        long splitTime = System.nanoTime() - binarySplitStartTime;
        
        System.out.printf("       ✓ Binary Splitting 完成，耗时 %.2f 秒%n", splitTime / 1_000_000_000.0);
        System.out.printf("       ✓ T 值位数：%,d%n", result.T.toString().length());
        System.out.printf("       ✓ Q 值位数：%,d%n", result.Q.toString().length());
        
        return result;
    }
    
    /**
     * 分段计算（适用于 1 亿位以上）
     * 
     * 优化原理：
     * 1. 将大区间拆分为小区间，分别计算 P,Q,T
     * 2. 每段计算完成后合并结果
     * 3. 合并后释放前一段的内存，降低峰值内存
     */
    private Result computeSegmented(int totalIterations, int segmentSize, int digits) throws InterruptedException {
        int segmentCount = (totalIterations + segmentSize - 1) / segmentSize;
        
        System.out.println("[分段计算] 开始大规模计算...");
        System.out.printf("            总迭代数：%,d%n", totalIterations);
        System.out.printf("            分段大小：%,d 次迭代/段%n", segmentSize);
        System.out.printf("            预计分段：%,d 段%n", segmentCount);
        System.out.println();
        
        Result accumulatedResult = null;
        int segmentStart = 0;
        int completedSegments = 0;
        
        // 如果有关联的检查点，跳过已完成的段
        CheckpointManager.CheckpointData checkpoint = CheckpointManager.loadCheckpoint();
        if (checkpoint != null && checkpoint.iteration > 0) {
            segmentStart = (checkpoint.iteration / segmentSize) * segmentSize;
            accumulatedResult = checkpoint.result;
            completedSegments = segmentStart / segmentSize;
            System.out.printf("[恢复] 已从段 %d (迭代 %d) 恢复%n", completedSegments, segmentStart);
        }
        
        binarySplitStartTime = System.nanoTime();
        
        while (segmentStart < totalIterations) {
            int segmentEnd = Math.min(segmentStart + segmentSize, totalIterations);
            int currentSegmentSize = segmentEnd - segmentStart;
            
            long segStart = System.nanoTime();
            Result segmentResult = binarySplit(segmentStart, segmentEnd);
            long segTime = System.nanoTime() - segStart;
            
            completedSegments++;
            
            // 合并结果
            if (accumulatedResult == null) {
                accumulatedResult = segmentResult;
            } else {
                accumulatedResult = mergeSegmentResults(accumulatedResult, segmentResult);
                // 优化：帮助 GC 回收
                segmentResult = null;
                
                // 每段完成后检查内存，必要时触发 GC
                if (completedSegments % 5 == 0) {
                    long usedMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
                    long maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024);
                    if (usedMemory > maxMemory * 0.8) {
                        System.out.printf("            [GC] 内存使用 %.0f%%，触发 GC...%n", 
                            usedMemory * 100.0 / maxMemory);
                        System.gc();
                    }
                }
            }
            
            // 显示进度
            double progress = segmentEnd * 100.0 / totalIterations;
            double speed = segmentEnd / ((System.nanoTime() - binarySplitStartTime) / 1_000_000_000.0);
            System.out.printf("            ✓ 段 %d/%d 完成 [%d-%d)，耗时 %.2f 秒 (总进度 %.1f%%, %.0f 迭代/秒)%n",
                completedSegments, segmentCount, segmentStart, segmentEnd, 
                segTime / 1_000_000_000.0, progress, speed);
            
            // 保存检查点（每段完成后）
            CheckpointManager.saveCheckpoint(segmentEnd, accumulatedResult);
            
            segmentStart = segmentEnd;
        }
        
        long totalTime = System.nanoTime() - binarySplitStartTime;
        System.out.println();
        System.out.printf("[分段计算] 所有 %d 段完成，总耗时 %.2f 秒%n", segmentCount, totalTime / 1_000_000_000.0);
        
        return accumulatedResult;
    }
    
    /**
     * 从检查点恢复计算
     */
    private Result resumeFromCheckpoint(CheckpointManager.CheckpointData checkpoint, int totalIterations) {
        System.out.printf("[恢复] 从迭代 %,d 继续计算到 %,d%n", checkpoint.iteration, totalIterations);
        
        binarySplitStartTime = System.nanoTime();
        Result remainingResult = binarySplit(checkpoint.iteration, totalIterations);
        long resumeTime = System.nanoTime() - binarySplitStartTime;
        
        System.out.printf("[恢复] 剩余部分计算完成，耗时 %.2f 秒%n", resumeTime / 1_000_000_000.0);
        
        return mergeSegmentResults(checkpoint.result, remainingResult);
    }
    
    /**
     * 合并两个分段的结果
     * 
     * Binary Splitting 合并公式：
     * P = P_left * P_right
     * Q = Q_left * Q_right  
     * T = T_left * Q_right + P_left * T_right
     */
    private Result mergeSegmentResults(Result left, Result right) {
        BigInteger P = left.P.multiply(right.P);
        BigInteger Q = left.Q.multiply(right.Q);
        BigInteger T = left.T.multiply(right.Q).add(left.P.multiply(right.T));
        return new Result(P, Q, T);
    }
    
    /**
     * 使用 Binary Splitting 算法计算 P, Q, T
     * 
     * 优化策略：
     * 1. 动态 threshold - 根据区间大小调整
     * 2. 深度限制 - 防止过度拆分
     * 3. 小任务顺序执行 - 减少 fork/join 开销
     */
    public Result binarySplit(int start, int end) {
        int range = end - start;
        
        // 动态计算 threshold
        int baseThreshold = Math.max(10, range / (parallelism * 8));
        int threshold = Math.min(Math.max(baseThreshold, 50), 500);
        
        // 计算最大深度
        int maxDepth = Math.max(3, 20 - (range / 10000));
        maxDepth = Math.min(maxDepth, 15);
        
        BinarySplitTask task = new BinarySplitTask(start, end, threshold, maxDepth, 0);
        return forkJoinPool.invoke(task);
    }
    
    /**
     * 流式输出 π 值到文件
     */
    private void streamPiToFile(Result result, int digits, String outputFile) throws Exception {
        System.out.println();
        System.out.println("[输出] 开始流式输出到文件：" + outputFile);
        
        streamStartTime = System.nanoTime();
        StreamingDivision.streamPi(result.Q, result.T, digits, outputFile);
        long streamTime = System.nanoTime() - streamStartTime;
        
        System.out.printf("[输出] 流式输出完成，耗时 %.2f 秒%n", streamTime / 1_000_000_000.0);
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
    
    /**
     * 获取并行度
     */
    public int getParallelism() {
        return parallelism;
    }
}
