import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/**
 * 高性能大整数数学运算工具类
 *
 * 核心优化点：
 * 1. Newton-Raphson 平方根 - 二次收敛，迭代次数少
 * 2. 智能初始猜测 - 减少迭代次数
 * 3. BigDecimal 原生 sqrt - Java 9+ 内置优化
 * 4. 位运算优化 - 使用位移代替除法
 * 5. 预计算常量 - 避免重复创建
 */
public class BigIntMath {
    
    // ==================== 预计算常量 ====================
    
    /** 2 的幂次缓存 (2^0 到 2^100) */
    private static final BigInteger[] POWERS_OF_TWO;
    
    static {
        POWERS_OF_TWO = new BigInteger[101];
        POWERS_OF_TWO[0] = BigInteger.ONE;
        for (int i = 1; i <= 100; i++) {
            POWERS_OF_TWO[i] = POWERS_OF_TWO[i - 1].shiftLeft(1);
        }
    }
    
    /**
     * 计算整数平方根（向下取整）
     * 
     * 使用 Newton-Raphson 迭代法：
     * x_{n+1} = (x_n + n/x_n) / 2
     * 
     * 优化点：
     * 1. 智能初始猜测：x_0 = 2^(bitLength/2)
     * 2. 提前终止：当 x_{n+1} >= x_n 时停止
     * 3. 位运算：使用 shiftRight 代替除法
     * 
     * @param n 被开方数（必须非负）
     * @return floor(sqrt(n))
     * @throws IllegalArgumentException 如果 n < 0
     */
    public static BigInteger sqrt(BigInteger n) {
        if (n.signum() < 0) {
            throw new IllegalArgumentException("Cannot compute square root of negative number");
        }
        
        if (n.equals(BigInteger.ZERO)) {
            return BigInteger.ZERO;
        }
        
        if (n.equals(BigInteger.ONE)) {
            return BigInteger.ONE;
        }
        
        // 小数值直接使用内置方法
        if (n.bitLength() < 64) {
            long sqrt = (long) Math.sqrt(n.longValue());
            return BigInteger.valueOf(sqrt);
        }
        
        // Newton-Raphson 迭代
        // 初始猜测：x_0 = 2^(bitLength/2)
        int bitLength = n.bitLength();
        BigInteger x = BigInteger.ONE.shiftLeft((bitLength + 1) / 2);
        
        // 迭代直到收敛
        BigInteger xPrev;
        do {
            xPrev = x;
            // x = (x + n/x) / 2
            x = x.add(n.divide(x)).shiftRight(1);
        } while (x.compareTo(xPrev) < 0);
        
        // xPrev 是最终结果（最后一个大于实际平方根的值的前一个值）
        return xPrev;
    }
    
    /**
     * 计算高精度平方根（BigDecimal 版本）
     * 
     * 使用 Java 9+ BigDecimal.sqrt() 方法，内部已优化
     * 
     * @param n 被开方数
     * @param precision 精度（总位数）
     * @return sqrt(n) 精确到指定精度
     */
    public static BigDecimal sqrtDecimal(BigInteger n, int precision) {
        MathContext mc = new MathContext(precision + 10);
        BigDecimal bd = new BigDecimal(n, mc);
        BigDecimal result = bd.sqrt(mc);
        return result.round(new MathContext(precision));
    }
    
    /**
     * 计算高精度平方根（直接对整数计算）
     * 
     * 用于计算 sqrt(10005) 等常数
     * 
     * @param n 被开方数
     * @param precision 精度（小数位数）
     * @return sqrt(n) 精确到指定小数位
     */
    public static BigDecimal sqrtWithPrecision(int n, int precision) {
        MathContext mc = new MathContext(precision + 20);
        return BigDecimal.valueOf(n).sqrt(mc);
    }
    
    /**
     * 快速计算 2 的幂次
     * 
     * 使用预计算缓存和位移优化
     * 
     * @param exp 指数
     * @return 2^exp
     */
    public static BigInteger pow2(int exp) {
        if (exp < 0) {
            throw new IllegalArgumentException("Exponent must be non-negative");
        }
        if (exp <= 100) {
            return POWERS_OF_TWO[exp];
        }
        return BigInteger.ONE.shiftLeft(exp);
    }
    
    /**
     * 计算 10 的幂次
     * 
     * 使用缓存优化小指数
     * 
     * @param exp 指数
     * @return 10^exp
     */
    private static final BigInteger[] POWERS_OF_TEN_SMALL = new BigInteger[21];
    
    static {
        POWERS_OF_TEN_SMALL[0] = BigInteger.ONE;
        for (int i = 1; i <= 20; i++) {
            POWERS_OF_TEN_SMALL[i] = POWERS_OF_TEN_SMALL[i - 1].multiply(BigInteger.TEN);
        }
    }
    
    public static BigInteger pow10(int exp) {
        if (exp < 0) {
            throw new IllegalArgumentException("Exponent must be non-negative");
        }
        if (exp <= 20) {
            return POWERS_OF_TEN_SMALL[exp];
        }
        return BigInteger.TEN.pow(exp);
    }
    
    /**
     * 计算阶乘（仅用于小数值）
     * 
     * 对于大数值，阶乘会非常巨大，不建议使用
     * 
     * @param n 要计算阶乘的数
     * @return n!
     */
    public static BigInteger factorial(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Factorial not defined for negative numbers");
        }
        if (n <= 1) {
            return BigInteger.ONE;
        }
        
        // 使用预计算的小阶乘
        if (n <= 20) {
            return precomputedFactorial(n);
        }
        
        BigInteger result = BigInteger.ONE;
        for (int i = 2; i <= n; i++) {
            result = result.multiply(BigInteger.valueOf(i));
        }
        return result;
    }
    
    /**
     * 预计算的小阶乘值
     */
    private static final BigInteger[] SMALL_FACTORIALS = new BigInteger[21];
    
    static {
        SMALL_FACTORIALS[0] = BigInteger.ONE;
        for (int i = 1; i <= 20; i++) {
            SMALL_FACTORIALS[i] = SMALL_FACTORIALS[i - 1].multiply(BigInteger.valueOf(i));
        }
    }
    
    private static BigInteger precomputedFactorial(int n) {
        return SMALL_FACTORIALS[n];
    }
    
    /**
     * 计算最大公约数（欧几里得算法）
     * 
     * @param a 第一个数
     * @param b 第二个数
     * @return gcd(a, b)
     */
    public static BigInteger gcd(BigInteger a, BigInteger b) {
        return a.gcd(b);
    }
    
    /**
     * 计算最小公倍数
     * 
     * @param a 第一个数
     * @param b 第二个数
     * @return lcm(a, b)
     */
    public static BigInteger lcm(BigInteger a, BigInteger b) {
        if (a.equals(BigInteger.ZERO) || b.equals(BigInteger.ZERO)) {
            return BigInteger.ZERO;
        }
        return a.multiply(b).divide(a.gcd(b)).abs();
    }
    
    /**
     * 判断是否为完全平方数
     * 
     * @param n 待判断的数
     * @return 如果 n 是完全平方数返回 true
     */
    public static boolean isPerfectSquare(BigInteger n) {
        if (n.signum() < 0) {
            return false;
        }
        BigInteger sqrt = sqrt(n);
        return sqrt.multiply(sqrt).equals(n);
    }
}
