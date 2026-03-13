import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 高精度圆周率计算器 - 使用 Chudnovsky 算法
 * 支持任意精度计算，可输出到控制台或文件
 */
public class PiCalculator {

    // Chudnovsky 算法常量
    private static final BigDecimal M_MULTIPLIER = new BigDecimal("13591409");
    private static final BigDecimal K_COEFFICIENT = new BigDecimal("545140134");
    private static final BigDecimal SIXTEEN = new BigDecimal("16");
    private static final BigDecimal SIXTYFOUR = new BigDecimal("64");

    // 计算精度（额外位数用于中间计算）
    private static final int EXTRA_PRECISION = 50;

    // 延迟计算常量 C
    private BigDecimal constantC = null;

    /**
     * 获取常量 C = 426880 * sqrt(10005)
     */
    private BigDecimal getConstantC() {
        if (constantC == null) {
            constantC = new BigDecimal("426880").multiply(
                sqrt(new BigDecimal("10005"), mathContext.getPrecision())
            );
        }
        return constantC;
    }

    private final int precision;
    private final MathContext mathContext;
    private final boolean enableLogging;
    private final boolean useMultithreading;
    private final int threadCount;

    /**
     * 构造函数
     * @param precision 小数位数
     * @param enableLogging 是否启用日志
     * @param useMultithreading 是否使用多线程
     * @param threadCount 线程数量
     */
    public PiCalculator(int precision, boolean enableLogging, 
                        boolean useMultithreading, int threadCount) {
        this.precision = precision;
        this.mathContext = new MathContext(precision + EXTRA_PRECISION, RoundingMode.HALF_UP);
        this.enableLogging = enableLogging;
        this.useMultithreading = useMultithreading;
        this.threadCount = Math.max(1, threadCount);
    }

    /**
     * 计算圆周率
     * @return π的 BigDecimal 值
     */
    public BigDecimal calculate() {
        log("开始计算 π，精度：" + precision + " 位小数");
        log("使用多线程：" + useMultithreading + ", 线程数：" + threadCount);

        // 计算需要的迭代次数
        int iterations = calculateIterations(precision);
        log("迭代次数：" + iterations);

        // 计算级数和
        BigDecimal sum;
        if (useMultithreading && threadCount > 1) {
            sum = calculateSumMultithreaded(iterations);
        } else {
            sum = calculateSumSingleThreaded(iterations);
        }

        // 应用 Chudnovsky 公式
        BigDecimal pi = getConstantC().divide(sum, mathContext);

        log("计算完成");
        return pi.round(new MathContext(precision, RoundingMode.HALF_UP));
    }

    /**
     * 根据精度计算所需的迭代次数
     * Chudnovsky 算法每项约增加 14 位精度
     */
    private int calculateIterations(int precision) {
        return (int) Math.ceil(precision / 14.0) + 2;
    }

    /**
     * 单线程计算级数和
     */
    private BigDecimal calculateSumSingleThreaded(int iterations) {
        BigDecimal sum = BigDecimal.ZERO;

        // 预计算常量
        BigDecimal basePower = new BigDecimal("640320").pow(3);
        
        // 缓存阶乘结果
        BigDecimal[] factorialCache = new BigDecimal[iterations * 6 + 1];
        factorialCache[0] = BigDecimal.ONE;

        long startTime = System.currentTimeMillis();
        long lastLogTime = startTime;

        for (int k = 0; k < iterations; k++) {
            BigDecimal term = calculateTerm(k, basePower, factorialCache);
            sum = sum.add(term, mathContext);

            // 每秒显示一次进度
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastLogTime >= 1000) {
                log("进度：" + (k * 100 / iterations) + "% (" + k + "/" + iterations + 
                    ") 耗时：" + formatTime(currentTime - startTime));
                lastLogTime = currentTime;
            }
        }

        return sum;
    }

    /**
     * 多线程计算级数和
     */
    private BigDecimal calculateSumMultithreaded(int iterations) {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CompletionService<BigDecimal> completionService = new ExecutorCompletionService<>(executor);

        int chunkSize = (iterations + threadCount - 1) / threadCount;
        int submittedTasks = 0;

        // 预计算常量
        BigDecimal basePower = new BigDecimal("640320").pow(3);

        // 进度跟踪
        long startTime = System.currentTimeMillis();
        AtomicInteger completedTasks = new AtomicInteger(0);
        AtomicInteger totalIterationsCompleted = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            int start = t * chunkSize;
            int end = Math.min(start + chunkSize, iterations);

            if (start < iterations) {
                final int taskStart = start;
                final int taskEnd = end;
                final int taskIndex = t;

                completionService.submit(() -> {
                    BigDecimal partialSum = BigDecimal.ZERO;
                    BigDecimal[] factorialCache = new BigDecimal[taskEnd * 6 + 1];
                    factorialCache[0] = BigDecimal.ONE;

                    long taskStartTime = System.currentTimeMillis();
                    long lastLogTime = taskStartTime;
                    int localCompleted = 0;

                    for (int k = taskStart; k < taskEnd; k++) {
                        BigDecimal term = calculateTerm(k, basePower, factorialCache);
                        partialSum = partialSum.add(term, mathContext);
                        localCompleted++;

                        // 每秒显示一次线程进度
                        long currentTime = System.currentTimeMillis();
                        if (enableLogging && currentTime - lastLogTime >= 1000) {
                            int globalCompleted = totalIterationsCompleted.addAndGet(localCompleted);
                            int percent = (globalCompleted * 100) / iterations;
                            log("进度：" + percent + "% (" + globalCompleted + "/" + iterations + ") 耗时：" + 
                                formatTime(currentTime - startTime) + " [线程" + taskIndex + "]");
                            lastLogTime = currentTime;
                            localCompleted = 0;
                        }
                    }

                    // 处理剩余的迭代计数
                    if (localCompleted > 0) {
                        totalIterationsCompleted.addAndGet(localCompleted);
                    }

                    int completed = completedTasks.incrementAndGet();
                    if (enableLogging) {
                        log("线程 " + taskIndex + " 完成：" + taskStart + "-" + (taskEnd - 1) +
                            " (" + completed + "/" + threadCount + ")");
                    }

                    return partialSum;
                });
                submittedTasks++;
            }
        }

        BigDecimal totalSum = BigDecimal.ZERO;
        try {
            for (int i = 0; i < submittedTasks; i++) {
                Future<BigDecimal> future = completionService.take();
                totalSum = totalSum.add(future.get(), mathContext);
            }
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("多线程计算失败", e);
        } finally {
            executor.shutdown();
        }

        return totalSum;
    }

    /**
     * 计算 Chudnovsky 级数的单一项
     * 公式项：(-1)^k * (6k)! * (545140134k + 13591409) / [(3k)! * (k!)^3 * (640320)^(3k)]
     */
    private BigDecimal calculateTerm(int k, BigDecimal basePower, BigDecimal[] factorialCache) {
        if (k == 0) {
            return M_MULTIPLIER;
        }

        // 计算分子部分
        BigDecimal numerator = calculateNumerator(k, factorialCache);

        // 计算分母部分
        BigDecimal denominator = calculateDenominator(k, basePower, factorialCache);

        // 计算符号
        BigDecimal sign = (k % 2 == 0) ? BigDecimal.ONE : BigDecimal.ONE.negate();

        return sign.multiply(numerator).divide(denominator, mathContext);
    }

    /**
     * 计算分子：(6k)! * (545140134k + 13591409)
     */
    private BigDecimal calculateNumerator(int k, BigDecimal[] factorialCache) {
        BigDecimal factorial6k = getFactorial(6 * k, factorialCache);
        BigDecimal linearTerm = K_COEFFICIENT.multiply(new BigDecimal(k)).add(M_MULTIPLIER);
        return factorial6k.multiply(linearTerm, mathContext);
    }

    /**
     * 计算分母：(3k)! * (k!)^3 * (640320)^(3k)
     */
    private BigDecimal calculateDenominator(int k, BigDecimal basePower, BigDecimal[] factorialCache) {
        BigDecimal factorial3k = getFactorial(3 * k, factorialCache);
        BigDecimal factorialK = getFactorial(k, factorialCache);
        BigDecimal factorialKCubed = factorialK.pow(3, mathContext);
        BigDecimal powerTerm = basePower.pow(k, mathContext);

        return factorial3k.multiply(factorialKCubed, mathContext)
                         .multiply(powerTerm, mathContext);
    }

    /**
     * 获取阶乘值（带缓存）
     * 使用二分法优化大整数阶乘计算
     */
    private BigDecimal getFactorial(int n, BigDecimal[] cache) {
        if (cache[n] != null) {
            return cache[n];
        }

        // 使用二分法计算阶乘
        BigDecimal result = binaryFactorial(1, n);
        cache[n] = result;
        return result;
    }

    /**
     * 二分法计算阶乘
     * 将 n! 分解为多个区间的乘积，减少递归深度
     */
    private BigDecimal binaryFactorial(int start, int end) {
        if (start > end) {
            return BigDecimal.ONE;
        }
        if (start == end) {
            return new BigDecimal(start);
        }
        if (end - start == 1) {
            return new BigDecimal(start * end);
        }

        int mid = (start + end) / 2;
        return binaryFactorial(start, mid).multiply(
            binaryFactorial(mid + 1, end), mathContext
        );
    }

    /**
     * 平方根计算（Newton-Raphson 方法）
     */
    private static BigDecimal sqrt(BigDecimal value, int precision) {
        MathContext mc = new MathContext(precision + EXTRA_PRECISION, RoundingMode.HALF_UP);
        
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("不能计算负数的平方根");
        }
        if (value.equals(BigDecimal.ZERO)) {
            return BigDecimal.ZERO;
        }

        BigDecimal x = new BigDecimal(Math.sqrt(value.doubleValue()));
        BigDecimal lastX;

        do {
            lastX = x;
            x = value.divide(x, mc).add(x).divide(SIXTEEN.subtract(new BigDecimal("14")), mc);
        } while (!x.equals(lastX));

        return x.round(new MathContext(precision, RoundingMode.HALF_UP));
    }

    /**
     * 格式化时间显示（自动选择单位）
     */
    private String formatTime(long ms) {
        if (ms < 1000) {
            return ms + "ms";
        } else if (ms < 60000) {
            return String.format("%.2f 秒", ms / 1000.0);
        } else if (ms < 3600000) {
            long minutes = ms / 60000;
            long seconds = (ms % 60000) / 1000;
            return String.format("%d分%d秒", minutes, seconds);
        } else {
            long hours = ms / 3600000;
            long minutes = (ms % 3600000) / 60000;
            return String.format("%d小时%d分", hours, minutes);
        }
    }

    /**
     * 输出日志
     */
    private void log(String message) {
        if (enableLogging) {
            System.out.println("[PiCalculator] " + message);
        }
    }

    /**
     * 格式化 π 值输出
     * @param pi π值
     * @param charsPerLine 每行字符数（0 表示不换行）
     * @return 格式化后的字符串
     */
    public String formatPi(BigDecimal pi, int charsPerLine) {
        String piStr = pi.toPlainString();
        
        if (charsPerLine <= 0) {
            return piStr;
        }

        StringBuilder sb = new StringBuilder(piStr.length() + piStr.length() / charsPerLine);
        
        // 添加 "3." 前缀
        int integerPartEnd = piStr.indexOf('.');
        if (integerPartEnd == -1) {
            integerPartEnd = piStr.length();
        }
        
        sb.append(piStr, 0, integerPartEnd);
        
        // 处理小数部分
        if (integerPartEnd < piStr.length()) {
            String decimalPart = piStr.substring(integerPartEnd + 1);
            sb.append('.');
            
            for (int i = 0; i < decimalPart.length(); i++) {
                if (i > 0 && i % charsPerLine == 0) {
                    sb.append('\n');
                }
                sb.append(decimalPart.charAt(i));
            }
        }

        return sb.toString();
    }

    /**
     * 将 π 值保存到 Markdown 文件（包含统计信息）
     * @param pi π值
     * @param filename 文件名
     * @param charsPerLine 每行字符数
     * @param calcTimeMs 计算耗时（毫秒）
     * @throws IOException IO 异常
     */
    public void saveToMarkdown(BigDecimal pi, String filename, int charsPerLine, long calcTimeMs) throws IOException {
        log("正在保存到 Markdown 文件：" + filename);
        
        int decimalPlaces = pi.toPlainString().length() - 2; // 减去 "3."
        double speed = (calcTimeMs > 0) ? (double) decimalPlaces / (calcTimeMs / 1000.0) : 0;
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename), 65536)) {
            // 写入统计信息头部
            writer.write("# 圆周率 π 计算结果\n\n");
            writer.write("## 统计信息\n\n");
            writer.write("| 项目 | 数值 |\n");
            writer.write("|------|------|\n");
            writer.write(String.format("| 计算精度 | %d 位小数 |\n", decimalPlaces));
            writer.write(String.format("| 计算耗时 | %s |\n", formatTime(calcTimeMs)));
            writer.write(String.format("| 计算速度 | %.2f 位/秒 |\n", speed));
            writer.write(String.format("| 使用线程 | %d |\n", useMultithreading ? threadCount : 1));
            writer.write(String.format("| 计算时间 | %s |\n", LocalDateTime.now()));
            writer.write("\n");
            
            // 写入 π 值
            writer.write("## π 值\n\n```\n");
            String formatted = formatPi(pi, charsPerLine);
            writer.write(formatted);
            writer.write("\n```\n");
        }

        log("文件保存完成：" + filename);
    }

    /**
     * 将 π 值保存到文件（纯文本，不含统计）
     * @param pi π值
     * @param filename 文件名
     * @param charsPerLine 每行字符数
     * @throws IOException IO 异常
     */
    public void saveToFile(BigDecimal pi, String filename, int charsPerLine) throws IOException {
        log("正在保存到文件：" + filename);
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename), 65536)) {
            String formatted = formatPi(pi, charsPerLine);
            writer.write(formatted);
        }

        log("文件保存完成：" + filename);
    }

    /**
     * 主方法 - 程序入口
     */
    public static void main(String[] args) {
        // 默认参数
        int precision = 10000;
        boolean enableLogging = true;
        boolean useMultithreading = true;
        int threadCount = Runtime.getRuntime().availableProcessors();
        String outputFile = "pi_result.md";  // 默认输出到 Markdown 文件
        int charsPerLine = 100;

        // 解析命令行参数
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-p":
                case "--precision":
                    if (i + 1 < args.length) {
                        precision = Integer.parseInt(args[++i]);
                    }
                    break;
                case "-o":
                case "--output":
                    if (i + 1 < args.length) {
                        outputFile = args[++i];
                    }
                    break;
                case "-l":
                case "--lines":
                    if (i + 1 < args.length) {
                        charsPerLine = Integer.parseInt(args[++i]);
                    }
                    break;
                case "-t":
                case "--threads":
                    if (i + 1 < args.length) {
                        threadCount = Integer.parseInt(args[++i]);
                    }
                    break;
                case "--no-logging":
                    enableLogging = false;
                    break;
                case "--single-thread":
                    useMultithreading = false;
                    break;
                case "--console":
                    outputFile = null;  // 输出到控制台
                    break;
                case "-h":
                case "--help":
                    printHelp();
                    return;
                default:
                    System.err.println("未知参数：" + args[i]);
                    printHelp();
                    return;
            }
        }

        try {
            long startTime = System.nanoTime();

            // 创建计算器并执行
            PiCalculator calculator = new PiCalculator(
                precision, enableLogging, useMultithreading, threadCount
            );
            BigDecimal pi = calculator.calculate();

            long endTime = System.nanoTime();
            long totalCalcTime = (endTime - startTime) / 1_000_000;
            System.out.println("\n计算耗时：" + calculator.formatTime(totalCalcTime));

            // 输出结果
            if (outputFile != null) {
                if (outputFile.endsWith(".md")) {
                    calculator.saveToMarkdown(pi, outputFile, charsPerLine, totalCalcTime);
                } else {
                    calculator.saveToFile(pi, outputFile, charsPerLine);
                }
            } else {
                System.out.println("\nπ = " + calculator.formatPi(pi, charsPerLine));
            }

            // 显示统计信息
            int decimalPlaces = pi.toPlainString().length() - 2;
            double speed = (totalCalcTime > 0) ? (double) decimalPlaces / (totalCalcTime / 1000.0) : 0;
            System.out.println("\n=== 计算统计 ===");
            System.out.println("精度：" + decimalPlaces + " 位小数");
            System.out.println("耗时：" + calculator.formatTime(totalCalcTime));
            System.out.println("速度：" + String.format("%.2f", speed) + " 位/秒");
            System.out.println("使用线程：" + (useMultithreading ? threadCount : 1));

        } catch (NumberFormatException e) {
            System.err.println("参数格式错误：" + e.getMessage());
            printHelp();
        } catch (ArithmeticException e) {
            System.err.println("计算错误：" + e.getMessage());
        } catch (IOException e) {
            System.err.println("文件写入错误：" + e.getMessage());
        } catch (Exception e) {
            System.err.println("未知错误：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 打印帮助信息
     */
    private static void printHelp() {
        System.out.println("高精度圆周率计算器 - Chudnovsky 算法");
        System.out.println("\n用法：java PiCalculator [选项]");
        System.out.println("\n选项:");
        System.out.println("  -p, --precision <n>     计算精度（小数位数），默认 10000");
        System.out.println("  -o, --output <file>     输出文件，默认 pi_result.md");
        System.out.println("  -l, --lines <n>         每行字符数，默认 100（设为 0 不换行）");
        System.out.println("  -t, --threads <n>       线程数量，默认 CPU 核心数");
        System.out.println("  --no-logging            禁用日志输出");
        System.out.println("  --single-thread         使用单线程模式");
        System.out.println("  --console               输出到控制台（不保存文件）");
        System.out.println("  -h, --help              显示此帮助信息");
        System.out.println("\n示例:");
        System.out.println("  java PiCalculator -p 100000           # 保存到 pi_result.md");
        System.out.println("  java PiCalculator -p 1000000 -t 8     # 8 线程计算百万位");
        System.out.println("  java PiCalculator -p 10000 --console  # 输出到控制台");
    }
}
