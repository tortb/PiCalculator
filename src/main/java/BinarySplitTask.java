import java.math.BigInteger;
import java.util.concurrent.RecursiveTask;

/**
 * Binary Splitting 并行计算任务
 * 用于计算 Chudnovsky 级数
 */
public class BinarySplitTask extends RecursiveTask<Result> {
    private static final BigInteger C3_OVER_24 = new BigInteger("10939058860032000");
    private static final BigInteger TERM_CONSTANT_A = new BigInteger("545140134");
    private static final BigInteger TERM_CONSTANT_B = new BigInteger("13591409");
    private static final BigInteger SIX = new BigInteger("6");
    private static final BigInteger TWO = new BigInteger("2");
    private static final BigInteger ONE = BigInteger.ONE;

    private final int a;
    private final int b;
    private final int threshold;
    private final boolean enableCheckpoints;
    private final int checkpointInterval;

    public BinarySplitTask(int a, int b, int threshold) {
        this(a, b, threshold, true, 10000);
    }

    public BinarySplitTask(int a, int b, int threshold, boolean enableCheckpoints, int checkpointInterval) {
        this.a = a;
        this.b = b;
        this.threshold = threshold;
        this.enableCheckpoints = enableCheckpoints;
        this.checkpointInterval = checkpointInterval;
    }

    @Override
    protected Result compute() {
        if (b - a <= threshold) {
            return computeDirectly();
        } else {
            int m = (a + b) / 2;

            BinarySplitTask leftTask = new BinarySplitTask(a, m, threshold, enableCheckpoints, checkpointInterval);
            BinarySplitTask rightTask = new BinarySplitTask(m, b, threshold, enableCheckpoints, checkpointInterval);

            // 并行执行：left.fork(), right.compute(), left.join()
            leftTask.fork();
            Result rightResult = rightTask.compute();
            Result leftResult = leftTask.join();

            // 合并公式：
            // P = P_left * P_right
            // Q = Q_left * Q_right
            // T = T_left * Q_right + P_left * T_right
            BigInteger P = leftResult.P.multiply(rightResult.P);
            BigInteger Q = leftResult.Q.multiply(rightResult.Q);
            BigInteger T = leftResult.T.multiply(rightResult.Q).add(leftResult.P.multiply(rightResult.T));

            if (enableCheckpoints && (b - a) > checkpointInterval) {
                CheckpointManager.saveCheckpoint(b, new Result(P, Q, T));
            }

            return new Result(P, Q, T);
        }
    }

    private Result computeDirectly() {
        if (b - a == 1) {
            if (a == 0) {
                // a = 0 的基本情况
                // P = 1, Q = 1, T = 13591409
                return new Result(ONE, ONE, TERM_CONSTANT_B);
            } else {
                BigInteger aBig = BigInteger.valueOf(a);

                // P = (6a-5)(2a-1)(6a-1)
                BigInteger factor1 = SIX.multiply(aBig).subtract(BigInteger.valueOf(5));
                BigInteger factor2 = TWO.multiply(aBig).subtract(ONE);
                BigInteger factor3 = SIX.multiply(aBig).subtract(ONE);
                BigInteger P = factor1.multiply(factor2).multiply(factor3);

                // Q = a^3 * 10939058860032000
                BigInteger Q = aBig.pow(3).multiply(C3_OVER_24);

                // T = (13591409 + 545140134*a) * P
                BigInteger termMultiplier = TERM_CONSTANT_B.add(TERM_CONSTANT_A.multiply(aBig));
                BigInteger T = termMultiplier.multiply(P);

                // 如果 a 是奇数，T 取负值
                if (a % 2 == 1) {
                    T = T.negate();
                }

                if (enableCheckpoints && a % checkpointInterval == 0) {
                    CheckpointManager.saveCheckpoint(a, new Result(P, Q, T));
                }

                return new Result(P, Q, T);
            }
        } else {
            int m = (a + b) / 2;
            BinarySplitTask leftTask = new BinarySplitTask(a, m, threshold, enableCheckpoints, checkpointInterval);
            BinarySplitTask rightTask = new BinarySplitTask(m, b, threshold, enableCheckpoints, checkpointInterval);

            Result leftResult = leftTask.computeDirectly();
            Result rightResult = rightTask.computeDirectly();

            // 合并公式：
            // P = P_left * P_right
            // Q = Q_left * Q_right
            // T = T_left * Q_right + P_left * T_right
            BigInteger P = leftResult.P.multiply(rightResult.P);
            BigInteger Q = leftResult.Q.multiply(rightResult.Q);
            BigInteger T = leftResult.T.multiply(rightResult.Q).add(leftResult.P.multiply(rightResult.T));

            return new Result(P, Q, T);
        }
    }
}
