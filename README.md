# 工业级高性能π计算器

这是一个基于 Java 的工业级高性能π计算器，能够稳定计算 1 亿到 100 亿位π值。

## 特性

- 使用 Chudnovsky 算法和 Binary Splitting 技术
- 支持 ForkJoinPool 并行计算
- 实现流式除法，避免生成完整π字符串
- 支持内存映射文件输出
- 具备检查点恢复功能
- 实时进度监控
- NUMA 优化（需配合特定 JVM 参数）

## 算法说明

### Chudnovsky 算法

使用 Chudnovsky 算法，每项增加约 14.181647 位精度：

$$\frac{1}{\pi} = 12 \sum_{k=0}^{\infty} \frac{(-1)^k (6k)! (545140134k + 13591409)}{(3k)! (k!)^3 (640320)^{3k+3/2}}$$

### Binary Splitting

采用分治策略，将大规模计算分解为多个小任务并行处理。

**基础项公式**（当 $b - a = 1$ 时）：

- 若 $a = 0$：
  - $P = 1$
  - $Q = 1$
  - $T = 13591409$

- 若 $a > 0$：
  - $P = (6a-5)(2a-1)(6a-1)$
  - $Q = a^3 \times 10939058860032000$
  - $T = (13591409 + 545140134a) \times P$
  - 若 $a$ 为奇数，$T = -T$

**合并公式**：

```
P = P_left × P_right
Q = Q_left × Q_right
T = T_left × Q_right + P_left × T_right
```

**最终π计算公式**：

$$\pi = \frac{426880 \times \sqrt{10005} \times Q}{T}$$

其中 $\sqrt{10005}$ 使用 BigDecimal Newton 迭代法计算。

## 编译和运行

### 编译

```bash
./build.sh
```

或使用 Maven：

```bash
mvn clean package
```

### 运行

**推荐方式** - 自动内存管理：

```bash
# 使用便捷命令
./java-pi <位数>

# 或使用 run.sh
./run.sh <位数>
```

**手动方式** - 自定义 JVM 参数：

```bash
java -Xms4G -Xmx4G -XX:+UseG1GC -jar PiCalculator.jar <位数>
```

### 示例

```bash
# 快速测试 (100 位)
./java-pi 100

# 计算 100 万位
./java-pi 1000000

# 计算 1000 万位
./java-pi 10000000

# 指定输出文件
./java-pi 1000000 my_pi.md
```

### 全局安装（可选）

```bash
sudo ./install.sh
```

安装后可在任意目录使用：
```bash
java-pi 1000000
```

### JVM 优化参数

脚本会自动根据系统内存和计算位数调整 JVM 参数。如需手动指定：

| 参数 | 说明 |
|------|------|
| `-Xms4G -Xmx4G` | 设置堆内存（根据位数调整） |
| `-XX:+UseG1GC` | 使用 G1 垃圾收集器 |
| `-XX:+UseNUMA` | 启用 NUMA 感知（多路 CPU 系统） |
| `-XX:+AlwaysPreTouch` | 预触内存页，减少缺页中断 |

**内存参考**：
- 100 万位：2-4GB
- 1000 万位：4-8GB
- 1 亿位：8-16GB

## 输出格式

结果输出到 markdown 文件中，格式如下：

```markdown
# π值计算结果
# 精度：100 位

3.1415926535897932384626433832795028841971693993751058209749445923078164062862089986280348253421170679
```

每行最多包含 10000 位数字。

## 架构组件

| 组件 | 说明 |
|------|------|
| `PiEngine` | π计算核心引擎 |
| `BinarySplitTask` | 并行计算任务，实现 Chudnovsky Binary Splitting |
| `BigIntMath` | 大整数运算工具类 |
| `StreamingDivision` | 流式除法计算，支持高精度输出 |
| `PiWriter` | π值输出处理器 |
| `CheckpointManager` | 检查点管理器，支持断点恢复 |
| `ProgressMonitor` | 进度监控器 |
| `SystemMonitor` | 系统资源监控器 |
| `NUMAThreadManager` | NUMA 优化线程管理器 |
| `Result` | Binary Splitting 计算结果封装（P, Q, T） |

## 项目结构

```
PiCalculator/
├── src/main/java/
│   ├── Main.java              # 程序入口
│   ├── PiCalculator.java      # 主控制器
│   ├── PiEngine.java          # 计算引擎
│   ├── BinarySplitTask.java   # Binary Splitting 任务
│   ├── BigIntMath.java        # 大整数数学库
│   ├── StreamingDivision.java # 流式除法
│   ├── PiWriter.java          # 输出处理器
│   ├── CheckpointManager.java # 检查点管理
│   ├── ProgressMonitor.java   # 进度监控
│   ├── SystemMonitor.java     # 系统监控
│   ├── NUMAThreadManager.java # NUMA 线程管理
│   └── Result.java            # 计算结果封装
├── build.sh                   # 编译脚本
├── run.sh                     # 运行脚本（自动内存管理）
├── java-pi                    # 便捷启动命令
├── install.sh                 # 安装脚本
└── pom.xml                    # Maven 配置
```

## 性能参考

| 位数 | 预计时间 | 内存需求 |
|------|----------|----------|
| 100 万 | ~1 秒 | 2GB |
| 1000 万 | ~10 秒 | 4GB |
| 1 亿 | ~2 分钟 | 8GB |
| 10 亿 | ~30 分钟 | 16GB |
| 100 亿 | ~8 小时 | 64GB |

*实际性能取决于 CPU 核心数和内存带宽*

## 许可证

MIT License
