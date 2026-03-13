import java.io.*;
import java.math.BigInteger;
import java.util.Properties;

/**
 * 检查点管理器，用于保存和恢复计算状态
 */
public class CheckpointManager {
    private static final String CHECKPOINT_FILE = "checkpoint.dat";
    private static final String ITERATION_KEY = "iteration";
    private static final String P_VALUE_KEY = "p_value";
    private static final String Q_VALUE_KEY = "q_value";
    private static final String T_VALUE_KEY = "t_value";
    
    /**
     * 保存检查点
     * @param iteration 当前迭代次数
     * @param result 计算结果
     */
    public static void saveCheckpoint(int iteration, Result result) {
        Properties props = new Properties();
        props.setProperty(ITERATION_KEY, String.valueOf(iteration));
        props.setProperty(P_VALUE_KEY, result.P.toString());
        props.setProperty(Q_VALUE_KEY, result.Q.toString());
        props.setProperty(T_VALUE_KEY, result.T.toString());
        
        try (OutputStream output = new FileOutputStream(CHECKPOINT_FILE)) {
            props.store(output, "Pi计算检查点文件");
            System.out.println("检查点已保存，迭代次数: " + iteration);
        } catch (IOException e) {
            System.err.println("保存检查点失败: " + e.getMessage());
        }
    }
    
    /**
     * 加载检查点
     * @return 检查点数据，如果不存在则返回null
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
            
            System.out.println("检查点已加载，从迭代次数: " + iteration + " 开始");
            return new CheckpointData(iteration, new Result(pValue, qValue, tValue));
        } catch (IOException | NumberFormatException e) {
            System.err.println("加载检查点失败: " + e.getMessage());
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
            System.out.println("检查点文件已删除");
        }
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