import java.math.BigInteger;

/**
 * 大整数数学运算工具类
 */
public class BigIntMath {
    
    /**
     * 使用牛顿迭代法计算平方根
     * @param n 要计算平方根的数
     * @param precision 精度（小数点后的位数）
     * @return 平方根结果
     */
    public static BigInteger sqrt(BigInteger n) {
        if (n.equals(BigInteger.ZERO)) {
            return BigInteger.ZERO;
        }
        
        // 初始猜测值
        BigInteger x = n.shiftRight(n.bitLength() / 2).add(BigInteger.ONE);
        BigInteger xPrev;
        
        do {
            xPrev = x;
            x = x.add(n.divide(x)).shiftRight(1); // x = (x + n/x) / 2
        } while (x.compareTo(xPrev) < 0);
        
        return x;
    }
    
    /**
     * 使用牛顿迭代法计算高精度平方根(BigDecimal版本)
     * @param n 要计算平方根的数
     * @param precision 精度（小数点后的位数）
     * @return 平方根结果
     */
    public static java.math.BigDecimal sqrtDecimal(java.math.BigInteger n, int precision) {
        java.math.MathContext mc = new java.math.MathContext(precision + 10);
        
        // 将BigInteger转换为BigDecimal进行高精度计算
        java.math.BigDecimal x = new java.math.BigDecimal(n, mc).sqrt(mc);
        return x.round(new java.math.MathContext(precision));
    }
    
    /**
     * 计算阶乘（仅用于小数值，避免大数阶乘的性能问题）
     * @param n 要计算阶乘的数
     * @return 阶乘结果
     */
    public static BigInteger factorial(int n) {
        if (n <= 1) {
            return BigInteger.ONE;
        }
        
        BigInteger result = BigInteger.ONE;
        for (int i = 2; i <= n; i++) {
            result = result.multiply(BigInteger.valueOf(i));
        }
        return result;
    }
}