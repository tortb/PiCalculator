# 工业级高性能π计算器 - 项目结构

## 项目概述
这是一个基于Java的工业级高性能π计算器，能够稳定计算1亿到100亿位π值。

## 项目结构
```
PiCalculator/
├── src/
│   └── main/
│       └── java/
│           ├── BigIntMath.java          # 大整数数学运算工具类
│           ├── BinarySplitTask.java     # Binary Splitting并行计算任务
│           ├── CheckpointManager.java   # 检查点管理器
│           ├── Main.java               # 主程序入口点
│           ├── NUMAThreadManager.java   # NUMA优化线程管理器
│           ├── PiCalculator.java       # 工业级π计算器主类
│           ├── PiEngine.java           # π计算引擎
│           ├── PiWriter.java           # π值输出处理器
│           ├── ProgressMonitor.java     # 进度监控器
│           ├── Result.java             # Binary Splitting算法的返回结果结构
│           ├── StreamingDivision.java   # 流式除法计算
│           └── SystemMonitor.java       # 系统资源监控器
├── build/                             # 编译输出目录
├── pom.xml                           # Maven项目配置文件
├── README.md                         # 项目说明文档
├── build.sh                          # 编译脚本
└── run.sh                            # 运行脚本
```

## 核心特性

### 1. Chudnovsky算法 + Binary Splitting
- 使用Chudnovsky算法，每项增加约14.181647位精度
- 采用Binary Splitting分治策略，将大规模计算分解为多个小任务并行处理

### 2. 并行计算
- 使用ForkJoinPool实现并行计算
- 支持动态并行深度控制，避免过多任务创建
- 实现了left.fork()、right.compute()、left.join()的并行模式

### 3. 大整数优化
- 充分利用Java BigInteger内部算法（Karatsuba、Toom-Cook、FFT）
- 避免不必要的BigInteger创建，减少临时对象

### 4. 流式输出
- 实现Streaming Division算法，避免生成完整π字符串
- 每次计算固定digits chunk（默认10000位）
- 使用BufferedWriter高效写入文件

### 5. 内存映射输出
- 支持MappedByteBuffer进行大文件输出
- 避免大字符串，减少GC压力

### 6. 检查点恢复
- 实现CheckpointManager定期保存计算状态
- 支持程序中断后自动恢复计算
- 保存iteration index、P、Q、T值到checkpoint.dat

### 7. 进度监控
- 实现ProgressMonitor实时显示计算进度
- 显示进度百分比、digits/秒、预计剩余时间、CPU和内存使用率
- 每秒刷新一次监控信息

### 8. NUMA优化
- 设计NUMA友好的任务调度策略
- 减少跨CPU socket内存访问

## 编译和运行

### 编译
```bash
./build.sh
```

### 运行
```bash
./run.sh <位数> [输出文件名]
```

例如：
```bash
./run.sh 1000000              # 计算100万位π，默认输出到pi_1000000_digits.md
./run.sh 1000000 pi_result.md # 计算100万位π，输出到pi_result.md
```

## JVM优化参数
程序使用以下JVM参数以获得最佳性能：
- `-Xms8G -Xmx16G`: 设置堆内存大小
- `-XX:+UseG1GC`: 使用G1垃圾收集器
- `-XX:+UseNUMA`: 启用NUMA感知
- `-XX:+AlwaysPreTouch`: 预触内存页

## 输出格式
结果输出到markdown文件中，格式如下：
```
# π值计算结果
# 精度: 1000000 位

3.
1415926535...
```

每行包含10000位数字。