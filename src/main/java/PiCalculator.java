import java.io.IOException;
import java.math.BigInteger;

/**
 * 工业级π计算器主类
 */
public class PiCalculator {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("用法: java PiCalculator <位数> [输出文件名]");
            System.out.println("示例: java PiCalculator 1000000");
            System.out.println("示例: java PiCalculator 1000000 pi_result.md");
            return;
        }
        
        try {
            int digits = Integer.parseInt(args[0]);
            if (digits <= 0) {
                System.out.println("错误: 位数必须大于0");
                return;
            }
            
            String outputFilename = args.length > 1 ? args[1] : "pi_" + digits + "_digits.md";
            
            System.out.println("开始计算π，精度: " + digits + " 位");
            
            // 创建π计算引擎
            PiEngine engine = new PiEngine();
            
            // 使用流式输出计算π值
            engine.calculatePiStream(digits, outputFilename);
            
            // 清理资源
            engine.shutdown();
            CheckpointManager.removeCheckpoint();
            
        } catch (NumberFormatException e) {
            System.out.println("错误: 请输入有效的数字作为位数");
        } catch (Exception e) {
            System.err.println("计算过程中发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 计算π值并输出到文件
     * @param digits 要计算的位数
     * @param outputFile 输出文件名
     * @throws Exception 计算过程中的异常
     */
    public static void calculateAndSavePi(int digits, String outputFile) throws Exception {
        System.out.println("开始计算π，精度: " + digits + " 位");
        
        // 创建π计算引擎
        PiEngine engine = new PiEngine();
        
        // 使用流式输出计算π值
        engine.calculatePiStream(digits, outputFile);
        
        // 清理资源
        engine.shutdown();
        CheckpointManager.removeCheckpoint();
    }
}