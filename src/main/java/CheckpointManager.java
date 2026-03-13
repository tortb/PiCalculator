import java.io.*;
import java.math.BigInteger;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 检查点管理器，用于保存和恢复计算状态
 */
public class CheckpointManager {
    private static final String CHECKPOINT_FILE = "checkpoint.dat";
    private static final String ITERATION_KEY = "iteration";
    private static final String P_VALUE_KEY = "p_value";
    private static final String Q_VALUE_KEY = "q_value";
    private static final String T_VALUE_KEY = "t_value";
    
    // 用于控制检查点输出频率：每 N 次才输出一次日志
    private static final AtomicInteger checkpointCounter = new AtomicInteger(0);
    private static final int CHECKPOINT_LOG_INTERVAL = 100;  // 每 100 次检查点输出一次日志

    /**
     * 保存检查点（默认不输出日志，避免大量输出阻塞）
     * @param iteration 当前迭代次数
     * @param result 计算结果
     */
    public static void saveCheckpoint(int iteration, Result result) {
        saveCheckpoint(iteration, result, false);
    }

    /**
     * 保存检查点
     * @param iteration 当前迭代次数
     * @param result 计算结果
     * @param forceLog 是否强制输出日志
     */
    public static void saveCheckpoint(int iteration, Result result, boolean forceLog) {
        Properties props = new Properties();
        props.setProperty(ITERATION_KEY, String.valueOf(iteration));
        props.setProperty(P_VALUE_KEY, result.P.toString());
        props.setProperty(Q_VALUE_KEY, result.Q.toString());
        props.setProperty(T_VALUE_KEY, result.T.toString());

        try (OutputStream output = new FileOutputStream(CHECKPOINT_FILE)) {
            props.store(output, "Pi 计算检查点文件");
            
            // 只有强制日志或达到间隔时才输出，避免大量输出阻塞终端
            if (forceLog || checkpointCounter.incrementAndGet() % CHECKPOINT_LOG_INTERVAL == 0) {
                System.out.println("检查点已保存，迭代次数：" + iteration);
            }
        } catch (IOException e) {
            System.err.println("保存检查点失败：" + e.getMessage());
        }
    }

    /**
     * 加载检查点
     * @return 检查点数据，如果不存在则返回 null
     */
    public static CheckpointData loadCheckpoint() {
        File checkpointFile = new File(CHECKPOINT_FILE);
        if (!checkpointFile.exists()) {
            return null;
        }

        Properties props = new Properties();
        try (InputStream input = new FileInputStream(CHECKPOINT_FILE)) {
            props.load(input);

            int iteration = Integer.parseInt(props.getProperty(ITERATION_KEY, "0"));
            BigInteger pValue = new BigInteger(props.getProperty(P_VALUE_KEY, "0"));
            BigInteger qValue = new BigInteger(props.getProperty(Q_VALUE_KEY, "0"));
            BigInteger tValue = new BigInteger(props.getProperty(T_VALUE_KEY, "0"));

            System.out.println("从检查点恢复，起始迭代：" + iteration);
            return new CheckpointData(iteration, new Result(pValue, qValue, tValue));
        } catch (IOException | NumberFormatException e) {
            System.err.println("加载检查点失败：" + e.getMessage());
            return null;
        }
    }

    /**
     * 删除检查点文件
     */
    public static void removeCheckpoint() {
        File checkpointFile = new File(CHECKPOINT_FILE);
        if (checkpointFile.exists()) {
            checkpointFile.delete();
        }
    }

    /**
     * 重置检查点计数器（用于新的计算任务）
     */
    public static void resetCounter() {
        checkpointCounter.set(0);
    }

    /**
     * 检查点数据类
     */
    public static class CheckpointData {
        public final int iteration;
        public final Result result;

        public CheckpointData(int iteration, Result result) {
            this.iteration = iteration;
            this.result = result;
        }
    }
}
