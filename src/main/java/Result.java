import java.math.BigInteger;

/**
 * Binary Splitting算法的返回结果结构
 */
public class Result {
    public final BigInteger P;
    public final BigInteger Q;
    public final BigInteger T;

    public Result(BigInteger p, BigInteger q, BigInteger t) {
        this.P = p;
        this.Q = q;
        this.T = t;
    }
}