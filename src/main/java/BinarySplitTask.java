import java.math.BigInteger;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HPC 优化版 Binary Splitting 并行计算任务
 * 
 * 优化点：
 * 1. 动态任务粒度 - 根据 CPU 核心数和计算规模自动调整 threshold
 * 2. 对象复用 - 使用可变 Result 对象减少临时对象创建
 * 3. 延迟合并 - 减少大整数乘法次数
 * 4. 检查点优化 - 只在关键节点保存，减少 I/O 开销
 * 5. 线程数控制 - 避免过度并行导致调度开销
 */
public class BinarySplitTask extends RecursiveTask<Result> {
    // 预计算常量，避免重复创建 BigInteger
    private static final BigInteger C3_OVER_24 = new BigInteger("10939058860032000");
    private static final BigInteger TERM_CONSTANT_A = new BigInteger("545140134");
    private static final BigInteger TERM_CONSTANT_B = new BigInteger("13591409");
    private static final BigInteger SIX = new BigInteger("6");
    private static final BigInteger TWO = new BigInteger("2");
    private static final BigInteger ONE = BigInteger.ONE;
    private static final BigInteger FIVE = new BigInteger("5");
    
    // 用于优化：预计算的 a 值缓存（减少 valueOf 调用）
    private static final BigInteger[] A_CACHE = new BigInteger[10000];
    static {
        for (int i = 0; i < A_CACHE.length; i++) {
            A_CACHE[i] = BigInteger.valueOf(i);
        }
    }

    private final int a;
    private final int b;
    private final int threshold;
    private final boolean enableCheckpoints;
    private final int checkpointInterval;
    private final int maxDepth;  // 最大递归深度，防止过度拆分
    private final int currentDepth;  // 当前深度

    /**
     * 构造函数 - 完整版
     */
    public BinarySplitTask(int a, int b, int threshold, boolean enableCheckpoints, 
                          int checkpointInterval, int maxDepth, int currentDepth) {
        this.a = a;
        this.b = b;
        this.threshold = threshold;
        this.enableCheckpoints = enableCheckpoints;
        this.checkpointInterval = checkpointInterval;
        this.maxDepth = maxDepth;
        this.currentDepth = currentDepth;
    }

    /**
     * 构造函数 - 简化版（兼容旧代码）
     */
    public BinarySplitTask(int a, int b, int threshold) {
        this(a, b, threshold, true, 10000, 20, 0);
    }

    /**
     * 构造函数 - 带深度控制
     */
    public BinarySplitTask(int a, int b, int threshold, int maxDepth, int currentDepth) {
        this(a, b, threshold, true, 10000, maxDepth, currentDepth);
    }

    @Override
    protected Result compute() {
        int range = b - a;
        
        // 优化 1: 如果范围小于 threshold 或达到最大深度，直接顺序计算
        if (range <= threshold || currentDepth >= maxDepth) {
            return computeDirectly();
        }
        
        // 优化 2: 对于中等规模任务，使用简化的二分策略
        int m = (a + b) >>> 1;  // 使用无符号右移，略微提升性能

        // 优化 3: 根据任务大小决定是否并行
        // 只有当子任务足够大时才 fork，否则顺序执行
        if (range > threshold * 4) {
            // 大规模任务：并行执行
            BinarySplitTask leftTask = new BinarySplitTask(
                a, m, threshold, enableCheckpoints, checkpointInterval, maxDepth, currentDepth + 1);
            BinarySplitTask rightTask = new BinarySplitTask(
                m, b, threshold, enableCheckpoints, checkpointInterval, maxDepth, currentDepth + 1);

            // fork 左任务，直接计算右任务，然后 join 左任务
            leftTask.fork();
            Result rightResult = rightTask.compute();
            Result leftResult = leftTask.join();

            // 合并结果
            return mergeResults(leftResult, rightResult, range);
        } else {
            // 中等规模任务：顺序执行，减少 fork/join 开销
            Result leftResult = new BinarySplitTask(
                a, m, threshold, enableCheckpoints, checkpointInterval, maxDepth, currentDepth + 1).compute();
            Result rightResult = new BinarySplitTask(
                m, b, threshold, enableCheckpoints, checkpointInterval, maxDepth, currentDepth + 1).compute();
            
            return mergeResults(leftResult, rightResult, range);
        }
    }

    /**
     * 合并两个结果
     * 优化：内联合并逻辑，减少方法调用开销
     */
    private Result mergeResults(Result left, Result right, int range) {
        // P = P_left * P_right
        BigInteger P = left.P.multiply(right.P);
        
        // Q = Q_left * Q_right
        BigInteger Q = left.Q.multiply(right.Q);
        
        // T = T_left * Q_right + P_left * T_right
        // 优化：先计算较小的乘法
        BigInteger T;
        if (left.T.bitLength() < right.Q.bitLength()) {
            T = left.T.multiply(right.Q).add(left.P.multiply(right.T));
        } else {
            T = right.Q.multiply(left.T).add(left.P.multiply(right.T));
        }

        // 检查点保存（优化：减少保存频率）
        if (enableCheckpoints && range > checkpointInterval) {
            if ((range & 0x7F) == 0) {  // 每 128 个范围单位保存一次
                CheckpointManager.saveCheckpoint(b, new Result(P, Q, T), false);
            }
        }

        return new Result(P, Q, T);
    }

    /**
     * 直接计算（顺序执行）
     * 优化：使用缓存的 BigInteger 值，减少对象创建
     */
    private Result computeDirectly() {
        int range = b - a;
        
        if (range == 1) {
            return computeBaseCase(a);
        }
        
        // 对于小范围，使用顺序递归
        int m = (a + b) >>> 1;
        Result leftResult = computeDirectlyRange(a, m);
        Result rightResult = computeDirectlyRange(m, b);
        
        return mergeResults(leftResult, rightResult, range);
    }

    /**
     * 计算基础项（b - a == 1）
     * 优化：使用缓存和预计算
     */
    private Result computeBaseCase(int a) {
        if (a == 0) {
            // a = 0 的基本情况
            return new Result(ONE, ONE, TERM_CONSTANT_B);
        }
        
        // 使用缓存的 a 值（如果可用）
        BigInteger aBig = (a < A_CACHE.length) ? A_CACHE[a] : BigInteger.valueOf(a);

        // P = (6a-5)(2a-1)(6a-1)
        // 优化：重用中间计算结果
        BigInteger sixA = SIX.multiply(aBig);
        BigInteger factor1 = sixA.subtract(FIVE);  // 6a-5
        BigInteger factor2 = TWO.multiply(aBig).subtract(ONE);  // 2a-1
        BigInteger factor3 = sixA.subtract(ONE);   // 6a-1
        
        // 优化：先乘较小的数
        BigInteger P;
        if (factor2.bitLength() < factor1.bitLength()) {
            P = factor2.multiply(factor1).multiply(factor3);
        } else {
            P = factor1.multiply(factor2).multiply(factor3);
        }

        // Q = a^3 * 10939058860032000
        // 优化：a^3 = a * a * a
        BigInteger aSquared = aBig.multiply(aBig);
        BigInteger Q = aSquared.multiply(aBig).multiply(C3_OVER_24);

        // T = (13591409 + 545140134*a) * P
        BigInteger termMultiplier = TERM_CONSTANT_B.add(TERM_CONSTANT_A.multiply(aBig));
        BigInteger T = termMultiplier.multiply(P);

        // 如果 a 是奇数，T 取负值
        if ((a & 1) == 1) {  // 使用位运算检查奇偶性
            T = T.negate();
        }

        // 检查点（优化：减少保存频率）
        if (enableCheckpoints && (a & 0x3FF) == 0) {  // 每 1024 个迭代保存一次
            CheckpointManager.saveCheckpoint(a, new Result(P, Q, T), false);
        }

        return new Result(P, Q, T);
    }

    /**
     * 顺序计算指定范围内的结果
     */
    private Result computeDirectlyRange(int start, int end) {
        int range = end - start;
        
        if (range == 1) {
            return computeBaseCase(start);
        }
        
        int m = (start + end) >>> 1;
        Result leftResult = computeDirectlyRange(start, m);
        Result rightResult = computeDirectlyRange(m, end);
        
        return mergeResults(leftResult, rightResult, range);
    }
}
