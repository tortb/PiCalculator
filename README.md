# 高精度圆周率计算器

基于 **Chudnovsky 算法** 的高性能 Java 圆周率计算程序，支持任意精度计算（百万位以上）。

## 特性

- ✅ **Chudnovsky 算法** - 每项约增加 14 位精度，收敛速度极快
- ✅ **任意精度** - 支持百万位甚至更高精度计算
- ✅ **BigDecimal 高精度** - 确保计算过程无精度丢失
- ✅ **二分法阶乘优化** - 减少大整数阶乘的递归深度
- ✅ **多线程加速** - 可选并行计算级数项
- ✅ **Markdown 输出** - 默认输出到 MD 文件，包含计算时间、位数、速度统计
- ✅ **灵活输出** - 支持控制台/文件输出，可配置每行字符数
- ✅ **进度日志** - 实时显示计算进度

## 环境要求

- **Java 25** 或更高版本
- 足够的内存（百万位约需数百 MB 内存）

## 编译与运行

### 编译并打包

```bash
# 编译
javac src/PiCalculator.java

# 打包为可执行 JAR
cd src && jar cfm ../pi.jar ../manifest.mf *.class && cd ..
```

### 运行

```bash
# 方式 1: 使用启动脚本（推荐）
./pi -p 10000

# 方式 2: 直接使用 JAR
java -jar pi.jar -p 10000

# 方式 3: 使用 classpath
java -cp src PiCalculator -p 10000
```

### 常用命令

```bash
# 计算 10000 位，保存到 pi_result.md
./pi -p 10000

# 计算 100000 位，保存到指定文件
./pi -p 100000 -o pi_100k.md

# 计算 1000000 位，单线程
./pi -p 1000000 --single-thread -l 0 -o pi_million.md

# 输出到控制台（不保存文件）
./pi -p 10000 --console
```

## 命令行参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `-p, --precision <n>` | 计算精度（小数位数） | 10000 |
| `-o, --output <file>` | 输出文件路径 | `pi_result.md` |
| `-l, --lines <n>` | 每行字符数（0 不换行） | 100 |
| `-t, --threads <n>` | 线程数量 | CPU 核心数 |
| `--no-logging` | 禁用日志输出 | 启用 |
| `--single-thread` | 使用单线程模式 | 多线程 |
| `--console` | 输出到控制台（不保存文件） | 保存到文件 |
| `-h, --help` | 显示帮助信息 | - |

## 算法原理

### Chudnovsky 公式

```
1/π = 12 × Σ(k=0 to ∞) [(-1)^k × (6k)! × (545140134k + 13591409)] 
                        / [(3k)! × (k!)³ × (640320)^(3k+3/2)]
```

该公式由 Chudnovsky 兄弟于 1988 年提出，是目前计算 π 最高效的算法之一。

### 性能优化

1. **二分法阶乘** - 将 n! 分解为多个区间乘积，降低递归深度
   ```
   n! = (1×2×...×m) × ((m+1)×...×n)  其中 m = n/2
   ```

2. **阶乘缓存** - 避免重复计算相同的阶乘值

3. **多线程并行** - 将级数项分配到多个线程并行计算

4. **动态迭代次数** - 根据目标精度自动计算所需迭代次数
   ```
   iterations = ceil(precision / 14) + 2
   ```

## 性能参考

| 精度（位） | 单线程耗时 | 8 线程耗时 | 内存占用 |
|-----------|-----------|-----------|---------|
| 10,000    | ~0.5s     | ~0.2s     | ~50MB   |
| 100,000   | ~30s      | ~8s       | ~200MB  |
| 1,000,000 | ~50min    | ~10min    | ~2GB    |

*实际性能取决于硬件配置*

## 输出示例

### Markdown 文件输出

```markdown
# 圆周率 π 计算结果

## 统计信息

| 项目 | 数值 |
|------|------|
| 计算精度 | 10000 位小数 |
| 计算耗时 | 0.523 秒 |
| 计算速度 | 19120.46 位/秒 |
| 使用线程 | 8 |
| 计算时间 | 2026-03-13T10:30:45.123 |

## π 值

```
3.1415926535897932384626433832795028841971693993751058209749445923078164062862089986280348253421170679
8214808651328230664709384460955058223172535940812848111745028410270193852110555964462294895493038196
...
```
```

### 控制台输出

```
[PiCalculator] 开始计算 π，精度：10000 位小数
[PiCalculator] 使用多线程：true, 线程数：8
[PiCalculator] 迭代次数：718
[PiCalculator] 进度：0% (0/718)
[PiCalculator] 线程 0 完成：0-89
...
[PiCalculator] 计算完成

计算耗时：523 ms

=== 计算统计 ===
精度：10000 位小数
耗时：0.523 秒
速度：19120.46 位/秒
使用线程：8
```

## 扩展接口

程序提供以下公共方法可供扩展：

```java
// 创建计算器实例
PiCalculator calculator = new PiCalculator(
    precision,      // 精度
    enableLogging,  // 是否日志
    useMultithreading, // 是否多线程
    threadCount     // 线程数
);

// 计算 π
BigDecimal pi = calculator.calculate();

// 格式化输出
String formatted = calculator.formatPi(pi, 100);

// 保存到 Markdown 文件（含统计）
calculator.saveToMarkdown(pi, "output.md", 100, calcTimeMs);

// 保存到纯文本文件
calculator.saveToFile(pi, "output.txt", 100);
```

## 异常处理

程序包含完整的异常处理：

- `NumberFormatException` - 参数格式错误
- `ArithmeticException` - 计算错误
- `IOException` - 文件写入错误
- `RuntimeException` - 多线程计算失败

## 项目结构

```
pi/
├── src/
│   └── PiCalculator.java    # 主程序源码
├── pi.jar                    # 可执行 JAR
├── pi                        # Linux/Mac 启动脚本
├── build.sh                  # 编译打包脚本
├── manifest.mf               # JAR 清单文件
└── README.md                 # 说明文档
```

## 许可证

MIT License

## 参考

- Chudnovsky, D. V., & Chudnovsky, G. V. (1988). "The computation of classical constants sp".
- [Pi - Wikipedia](https://en.wikipedia.org/wiki/Pi)
