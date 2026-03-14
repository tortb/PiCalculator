import java.math.BigInteger;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 工业级 Binary Splitting 并行计算任务
 * 支持万亿位 π 计算，带实时进度显示
 */
public class BinarySplitTask extends RecursiveTask<Result> {

    private static final long serialVersionUID = 1L;

    // ==================== 预计算常量 ====================

    private static final BigInteger C3_OVER_24 = new BigInteger("10939058860032000");
    private static final BigInteger TERM_A = new BigInteger("545140134");
    private static final BigInteger TERM_B = new BigInteger("13591409");
    private static final BigInteger SIX = BigInteger.valueOf(6);
    private static final BigInteger TWO = BigInteger.valueOf(2);
    private static final BigInteger ONE = BigInteger.ONE;
    private static final BigInteger FIVE = BigInteger.valueOf(5);

    // 预计算 0-9999 的 BigInteger
    private static final BigInteger[] A_CACHE = new BigInteger[10000];
    static {
        for (int i = 0; i < A_CACHE.length; i++) {
            A_CACHE[i] = BigInteger.valueOf(i);
        }
    }

    // ==================== 进度跟踪 ====================

    /** 全局已完成的迭代次数 */
    private static final AtomicLong completedIterations = new AtomicLong(0);

    // ==================== 任务参数 ====================

    private final int a;
    private final int b;
    private final int threshold;
    private final int maxDepth;
    private final int currentDepth;

    // ==================== 构造函数 ====================

    public BinarySplitTask(int a, int b, int threshold, int maxDepth, int currentDepth) {
        this.a = a;
        this.b = b;
        this.threshold = threshold;
        this.maxDepth = maxDepth;
        this.currentDepth = currentDepth;
    }

    // ==================== 核心计算方法 ====================

    @Override
    protected Result compute() {
        int range = b - a;

        // 达到叶子节点，直接计算
        if (range <= threshold || currentDepth >= maxDepth) {
            return computeSequential(a, b);
        }

        // 中等规模任务顺序执行
        if (range <= threshold * 2) {
            return computeSequential(a, b);
        }

        // 大规模任务并行执行
        int m = (a + b) >>> 1;

        BinarySplitTask leftTask = new BinarySplitTask(a, m, threshold, maxDepth, currentDepth + 1);
        BinarySplitTask rightTask = new BinarySplitTask(m, b, threshold, maxDepth, currentDepth + 1);

        // Fork-join 模式
        leftTask.fork();
        Result rightResult = rightTask.compute();
        Result leftResult = leftTask.join();

        return merge(leftResult, rightResult);
    }

    /**
     * 顺序计算区间 [start, end)
     */
    private Result computeSequential(int start, int end) {
        int range = end - start;

        if (range == 1) {
            Result result = computeBaseCase(start);
            completedIterations.addAndGet(1);
            return result;
        }

        int m = (start + end) >>> 1;
        Result left = computeSequential(start, m);
        Result right = computeSequential(m, end);
        return merge(left, right);
    }

    /**
     * 计算基础项
     */
    private Result computeBaseCase(int a) {
        if (a == 0) {
            return new Result(ONE, ONE, TERM_B);
        }

        BigInteger aBig = (a < A_CACHE.length) ? A_CACHE[a] : BigInteger.valueOf(a);

        // P = (6a-5)(2a-1)(6a-1)
        BigInteger sixA = SIX.multiply(aBig);
        BigInteger factor1 = sixA.subtract(FIVE);
        BigInteger factor2 = TWO.multiply(aBig).subtract(ONE);
        BigInteger factor3 = sixA.subtract(ONE);

        BigInteger P = factor1.multiply(factor2).multiply(factor3);

        // Q = a³ * C³/24
        BigInteger aSquared = aBig.multiply(aBig);
        BigInteger Q = aSquared.multiply(aBig).multiply(C3_OVER_24);

        // T = (13591409 + 545140134*a) * P
        BigInteger termMultiplier = TERM_B.add(TERM_A.multiply(aBig));
        BigInteger T = termMultiplier.multiply(P);

        // 如果 a 是奇数，T 取负值
        if ((a & 1) == 1) {
            T = T.negate();
        }

        return new Result(P, Q, T);
    }

    /**
     * 合并两个结果
     */
    private Result merge(Result left, Result right) {
        BigInteger P = left.P.multiply(right.P);
        BigInteger Q = left.Q.multiply(right.Q);
        BigInteger T = left.T.multiply(right.Q).add(left.P.multiply(right.T));
        return new Result(P, Q, T);
    }

    // ==================== 进度管理 ====================

    /**
     * 重置进度计数器
     */
    public static void resetProgress() {
        completedIterations.set(0);
    }

    /**
     * 获取已完成的迭代数
     */
    public static long getCompletedIterations() {
        return completedIterations.get();
    }

    /**
     * 后台进度显示线程
     */
    public static class ProgressMonitor implements Runnable {
        private final long totalIterations;
        private volatile boolean running = true;
        private final long startTime;

        public ProgressMonitor(long totalIterations) {
            this.totalIterations = totalIterations;
            this.startTime = System.nanoTime();
        }

        public void stop() {
            running = false;
        }

        @Override
        public void run() {
            long lastCompleted = 0;

            while (running) {
                try {
                    Thread.sleep(2000);

                    long current = completedIterations.get();
                    long elapsedNanos = System.nanoTime() - startTime;
                    double elapsedSec = elapsedNanos / 1_000_000_000.0;

                    double progress = current * 100.0 / totalIterations;
                    double speed = current / elapsedSec;
                    double remainingSec = (totalIterations - current) / speed;

                    String remainingTime = formatTime(remainingSec);

                    System.out.printf("       [进度] %7.2f%% | 已完成 %,d/%,d | 速度 %,d 迭代/秒 | 剩余 %s%n",
                        Math.min(99.99, progress), current, totalIterations, (long)speed, remainingTime);

                    lastCompleted = current;

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        private String formatTime(double seconds) {
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
}
