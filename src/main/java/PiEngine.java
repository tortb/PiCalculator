import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.concurrent.ForkJoinPool;

/**
 * π计算引擎
 */
public class PiEngine {
    private static final BigInteger SQRT_CONST = new BigInteger("10005");
    private static final BigInteger MULTIPLIER = new BigInteger("426880");

    private final ForkJoinPool forkJoinPool;
    private final int parallelism;

    public PiEngine() {
        this.parallelism = Runtime.getRuntime().availableProcessors();
        this.forkJoinPool = new ForkJoinPool(parallelism);

        // 初始化 NUMA 优化
        NUMAThreadManager.initializeNUMA();
    }

    /**
     * 计算π到指定精度
     * @param digits 要计算的位数
     * @return π的字符串表示
     */
    public String calculatePi(int digits) throws InterruptedException {
        int iterations = digits / 14 + 1;

        System.out.println("开始计算π，目标精度：" + digits + "位，迭代次数：" + iterations);

        CheckpointManager.CheckpointData checkpoint = CheckpointManager.loadCheckpoint();
        Result result;

        if (checkpoint != null) {
            result = resumeFromCheckpoint(checkpoint, iterations);
        } else {
            result = binarySplit(0, iterations);
        }

        // π = (426880 * sqrt(10005) * Q) / T
        // 使用 BigDecimal Newton iteration 计算 sqrt(10005)
        int extraPrecision = digits + 100;
        MathContext mc = new MathContext(extraPrecision);

        BigDecimal sqrt10005 = new BigDecimal("10005").sqrt(mc);
        BigDecimal bigQ = new BigDecimal(result.Q);
        BigDecimal bigT = new BigDecimal(result.T);
        BigDecimal bigMultiplier = new BigDecimal(MULTIPLIER);

        // 计算分子：426880 * sqrt(10005) * Q
        BigDecimal numerator = bigMultiplier.multiply(sqrt10005, mc).multiply(bigQ, mc);

        // 计算π = numerator / T
        BigDecimal piDecimal = numerator.divide(bigT, mc);

        // 转换为字符串并格式化
        String piStr = piDecimal.toPlainString();

        // 移除小数点，只保留数字
        piStr = piStr.replace(".", "");

        // 确保有足够的位数
        if (piStr.length() > digits + 1) {
            piStr = piStr.substring(0, digits + 1);
        }

        // 添加小数点
        if (piStr.length() > 1) {
            return piStr.charAt(0) + "." + piStr.substring(1);
        }

        return piStr;
    }

    /**
     * 计算π到指定精度，并支持流式输出
     * @param digits 要计算的位数
     * @param outputFile 输出文件
     */
    public void calculatePiStream(int digits, String outputFile) throws Exception {
        int iterations = digits / 14 + 1;

        System.out.println("开始计算π，目标精度：" + digits + "位，迭代次数：" + iterations);

        CheckpointManager.CheckpointData checkpoint = CheckpointManager.loadCheckpoint();
        Result result;

        if (checkpoint != null) {
            result = resumeFromCheckpoint(checkpoint, iterations);
        } else {
            result = binarySplit(0, iterations);
        }

        // π = (426880 * sqrt(10005) * Q) / T
        // 使用 BigDecimal Newton iteration 计算 sqrt(10005)
        int extraPrecision = digits + 100;
        MathContext mc = new MathContext(extraPrecision);

        BigDecimal sqrt10005 = new BigDecimal("10005").sqrt(mc);
        BigDecimal bigQ = new BigDecimal(result.Q);
        BigDecimal bigT = new BigDecimal(result.T);
        BigDecimal bigMultiplier = new BigDecimal(MULTIPLIER);

        // 计算分子：426880 * sqrt(10005) * Q
        BigDecimal numerator = bigMultiplier.multiply(sqrt10005, mc).multiply(bigQ, mc);

        // 计算π = numerator / T
        BigDecimal piDecimal = numerator.divide(bigT, mc);

        // 流式输出
        StreamingDivision.streamPi(result.Q, result.T, digits, outputFile);

        System.out.println("π值已计算完成，结果保存到：" + outputFile);
    }

    /**
     * 从检查点恢复计算
     */
    private Result resumeFromCheckpoint(CheckpointManager.CheckpointData checkpoint, int totalIterations) {
        System.out.println("从检查点恢复计算，起始迭代：" + checkpoint.iteration);
        Result remainingResult = binarySplit(checkpoint.iteration, totalIterations);

        // 正确的 Binary Splitting 合并公式
        // P = P_left * P_right
        // Q = Q_left * Q_right
        // T = T_left * Q_right + P_left * T_right
        Result combinedResult = new Result(
            checkpoint.result.P.multiply(remainingResult.P),
            checkpoint.result.Q.multiply(remainingResult.Q),
            checkpoint.result.T.multiply(remainingResult.Q).add(checkpoint.result.P.multiply(remainingResult.T))
        );

        return combinedResult;
    }

    /**
     * 使用 Binary Splitting 算法计算 P, Q, T
     */
    public Result binarySplit(int start, int end) {
        int threshold = Math.max(1, (end - start) / (parallelism * 4));
        threshold = Math.min(threshold, 100);

        BinarySplitTask task = new BinarySplitTask(start, end, threshold);
        return forkJoinPool.invoke(task);
    }

    /**
     * 关闭线程池
     */
    public void shutdown() {
        forkJoinPool.shutdown();
    }
}
