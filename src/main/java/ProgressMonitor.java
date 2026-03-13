import java.text.DecimalFormat;

/**
 * 进度监控器
 */
public class ProgressMonitor {
    private final long startTime;
    private final int totalIterations;
    private final SystemMonitor systemMonitor;
    private final DecimalFormat decimalFormat;
    
    public ProgressMonitor(int totalIterations) {
        this.totalIterations = totalIterations;
        this.startTime = System.currentTimeMillis();
        this.systemMonitor = new SystemMonitor();
        this.decimalFormat = new DecimalFormat("#.##");
    }
    
    /**
     * 更新进度
     * @param currentIteration 当前迭代次数
     * @param digitsCalculated 已计算的位数
     */
    public void updateProgress(int currentIteration, int digitsCalculated) {
        long currentTime = System.currentTimeMillis();
        long elapsedMs = currentTime - startTime;
        
        // 计算进度百分比
        double progressPercent = (double) currentIteration / totalIterations * 100.0;
        
        // 计算每秒计算的位数
        double digitsPerSecond = elapsedMs > 0 ? 
            (double) digitsCalculated / (elapsedMs / 1000.0) : 0;
        
        // 估算剩余时间
        double remainingPercent = 100.0 - progressPercent;
        double estimatedTotalTime = elapsedMs / progressPercent * 100.0;
        double remainingTimeMs = estimatedTotalTime - elapsedMs;
        double remainingTimeSec = remainingTimeMs / 1000.0;
        
        // 获取系统资源使用情况
        double cpuUsage = systemMonitor.getCpuUsage();
        double memoryUsage = systemMonitor.getMemoryUsage();
        
        // 格式化输出
        System.out.printf("进度: %s%% | 已用时: %s秒 | 剩余: %s秒 | 速度: %s位/秒 | CPU: %s%% | 内存: %s%%\n",
                decimalFormat.format(progressPercent),
                decimalFormat.format(elapsedMs / 1000.0),
                decimalFormat.format(remainingTimeSec),
                decimalFormat.format(digitsPerSecond),
                cpuUsage >= 0 ? decimalFormat.format(cpuUsage) : "N/A",
                decimalFormat.format(memoryUsage));
    }
    
    /**
     * 获取运行时间（秒）
     * @return 运行时间（秒）
     */
    public double getElapsedTimeSeconds() {
        return (System.currentTimeMillis() - startTime) / 1000.0;
    }
}