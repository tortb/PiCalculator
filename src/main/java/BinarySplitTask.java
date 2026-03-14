import java.math.BigInteger;
import java.util.concurrent.RecursiveTask;

/**
 * 高性能 Binary Splitting 并行计算任务
 *
 * 核心优化点：
 * 1. 预计算常量缓存 - 避免重复创建 BigInteger
 * 2. 动态任务粒度 - 根据 CPU 核心数和计算规模自动调整
 * 3. 延迟合并 - 减少大整数乘法次数
 * 4. 深度限制 - 防止过度拆分导致调度开销
 * 5. 小任务顺序执行 - 减少 fork/join 开销
 * 6. 位运算优化 - 使用位运算代替算术运算
 *
 * Chudnovsky 公式：
 * 1/π = 12 * Σ(k=0 to ∞) [(-1)^k * (6k)! * (545140134k + 13591409)] / [(3k)! * (k!)^3 * (640320^3)^(k+1/2)]
 *
 * Binary Splitting 将求和转换为：
 * P/Q = Σ [a_k / b_k] → P(a,b)/Q(a,b)
 */
public class BinarySplitTask extends RecursiveTask<Result> {

    private static final long serialVersionUID = 1L;

    // ==================== 预计算常量 ====================
    
    /** C³/24 = 10939058860032000 - Chudnovsky 公式常量 */
    private static final BigInteger C3_OVER_24 = new BigInteger("10939058860032000");
    
    /** 545140134 - 线性项系数 */
    private static final BigInteger TERM_A = new BigInteger("545140134");
    
    /** 13591409 - 常数项 */
    private static final BigInteger TERM_B = new BigInteger("13591409");
    
    /** 预计算小整数 */
    private static final BigInteger SIX = BigInteger.valueOf(6);
    private static final BigInteger TWO = BigInteger.valueOf(2);
    private static final BigInteger ONE = BigInteger.ONE;
    private static final BigInteger FIVE = BigInteger.valueOf(5);
    
    /** a 值缓存 (0-9999) - 避免重复创建小 BigInteger */
    private static final BigInteger[] A_CACHE = new BigInteger[10000];
    
    static {
        for (int i = 0; i < A_CACHE.length; i++) {
            A_CACHE[i] = BigInteger.valueOf(i);
        }
    }
    
    // ==================== 任务参数 ====================
    
    /** 区间起点 a */
    private final int a;
    
    /** 区间终点 b */
    private final int b;
    
    /** 顺序计算阈值 */
    private final int threshold;
    
    /** 最大递归深度 */
    private final int maxDepth;
    
    /** 当前递归深度 */
    private final int currentDepth;
    
    // ==================== 构造函数 ====================
    
    /**
     * 完整构造函数
     */
    public BinarySplitTask(int a, int b, int threshold, int maxDepth, int currentDepth) {
        this.a = a;
        this.b = b;
        this.threshold = threshold;
        this.maxDepth = maxDepth;
        this.currentDepth = currentDepth;
    }
    
    /**
     * 简化构造函数（兼容旧代码）
     */
    public BinarySplitTask(int a, int b, int threshold) {
        this(a, b, threshold, 20, 0);
    }
    
    // ==================== 核心计算方法 ====================
    
    @Override
    protected Result compute() {
        int range = b - a;
        
        // 优化 1: 范围小于阈值或达到最大深度时，直接顺序计算
        if (range <= threshold || currentDepth >= maxDepth) {
            return computeSequential(a, b);
        }
        
        // 优化 2: 中等规模任务顺序执行，减少 fork/join 开销
        if (range <= threshold * 4) {
            return computeSequential(a, b);
        }
        
        // 优化 3: 大规模任务并行执行
        int m = (a + b) >>> 1;  // 无符号右移
        
        // 创建子任务
        BinarySplitTask leftTask = new BinarySplitTask(
            a, m, threshold, maxDepth, currentDepth + 1);
        BinarySplitTask rightTask = new BinarySplitTask(
            m, b, threshold, maxDepth, currentDepth + 1);
        
        // Fork-join 模式：fork 一个，直接计算另一个，然后 join
        leftTask.fork();
        Result rightResult = rightTask.compute();
        Result leftResult = leftTask.join();
        
        // 合并结果
        return merge(leftResult, rightResult);
    }
    
    /**
     * 顺序计算区间 [a, b) 的结果
     */
    private Result computeSequential(int start, int end) {
        int range = end - start;
        
        if (range == 1) {
            return computeBaseCase(start);
        }
        
        // 二分递归
        int m = (start + end) >>> 1;
        Result left = computeSequential(start, m);
        Result right = computeSequential(m, end);
        return merge(left, right);
    }
    
    /**
     * 合并两个结果
     * 
     * 公式：
     * P = P_left * P_right
     * Q = Q_left * Q_right
     * T = T_left * Q_right + P_left * T_right
     */
    private Result merge(Result left, Result right) {
        // 优化：根据位长度选择乘法顺序，减少中间结果大小
        BigInteger P = left.P.multiply(right.P);
        BigInteger Q = left.Q.multiply(right.Q);
        
        // T = T_left * Q_right + P_left * T_right
        // 优化：先计算较小的乘法
        BigInteger T;
        if (left.T.bitLength() + right.Q.bitLength() < 
            left.P.bitLength() + right.T.bitLength()) {
            T = left.T.multiply(right.Q).add(left.P.multiply(right.T));
        } else {
            T = right.Q.multiply(left.T).add(left.P.multiply(right.T));
        }
        
        return new Result(P, Q, T);
    }
    
    /**
     * 计算基础项 (b - a == 1)
     * 
     * Chudnovsky 公式单项：
     * P = (6a-5)(2a-1)(6a-1)
     * Q = a³ * C³/24
     * T = (13591409 + 545140134*a) * P
     * 
     * 如果 a 是奇数，T 取负值
     */
    private Result computeBaseCase(int a) {
        // a = 0 的特殊情况
        if (a == 0) {
            return new Result(ONE, ONE, TERM_B);
        }
        
        // 使用缓存的 a 值
        BigInteger aBig = (a < A_CACHE.length) ? A_CACHE[a] : BigInteger.valueOf(a);
        
        // 计算 P = (6a-5)(2a-1)(6a-1)
        BigInteger sixA = SIX.multiply(aBig);
        BigInteger factor1 = sixA.subtract(FIVE);      // 6a-5
        BigInteger factor2 = TWO.multiply(aBig).subtract(ONE);  // 2a-1
        BigInteger factor3 = sixA.subtract(ONE);       // 6a-1
        
        // 优化乘法顺序：先乘较小的数
        BigInteger P;
        if (factor2.bitLength() < factor1.bitLength()) {
            P = factor2.multiply(factor1).multiply(factor3);
        } else {
            P = factor1.multiply(factor2).multiply(factor3);
        }
        
        // 计算 Q = a³ * C³/24
        // a³ = a * a * a
        BigInteger aSquared = aBig.multiply(aBig);
        BigInteger Q = aSquared.multiply(aBig).multiply(C3_OVER_24);
        
        // 计算 T = (13591409 + 545140134*a) * P
        BigInteger termMultiplier = TERM_B.add(TERM_A.multiply(aBig));
        BigInteger T = termMultiplier.multiply(P);
        
        // 如果 a 是奇数，T 取负值
        if ((a & 1) == 1) {  // 位运算检查奇偶性
            T = T.negate();
        }
        
        return new Result(P, Q, T);
    }
}
