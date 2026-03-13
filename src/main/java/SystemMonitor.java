import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;

/**
 * 系统资源监控器
 */
public class SystemMonitor {
    private final OperatingSystemMXBean osBean;
    private final MemoryMXBean memoryBean;
    
    public SystemMonitor() {
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.memoryBean = ManagementFactory.getMemoryMXBean();
    }
    
    /**
     * 获取CPU使用率
     * @return CPU使用率百分比
     */
    public double getCpuUsage() {
        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean platformBean = 
                (com.sun.management.OperatingSystemMXBean) osBean;
            return platformBean.getProcessCpuLoad() * 100.0;
        }
        // 如果无法获取详细CPU使用率，返回-1
        return -1;
    }
    
    /**
     * 获取内存使用率
     * @return 内存使用率百分比
     */
    public double getMemoryUsage() {
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();
        return (double) heapUsed / heapMax * 100.0;
    }
    
    /**
     * 获取可用处理器数量
     * @return 可用处理器数量
     */
    public int getAvailableProcessors() {
        return osBean.getAvailableProcessors();
    }
    
    /**
     * 获取系统负载平均值
     * @return 系统负载平均值
     */
    public double getSystemLoadAverage() {
        return osBean.getSystemLoadAverage();
    }
    
    /**
     * 获取已使用堆内存（字节）
     * @return 已使用堆内存
     */
    public long getUsedHeapMemory() {
        return memoryBean.getHeapMemoryUsage().getUsed();
    }
    
    /**
     * 获取最大堆内存（字节）
     * @return 最大堆内存
     */
    public long getMaxHeapMemory() {
        return memoryBean.getHeapMemoryUsage().getMax();
    }
}