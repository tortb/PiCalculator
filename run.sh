#!/bin/bash

# π 计算器运行脚本
# 用法：./run.sh <位数> [输出文件名]
# 示例：./run.sh 1000000
#       ./run.sh 1000000 pi_result.md

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [ $# -lt 1 ]; then
    echo "用法：$0 <位数> [输出文件名]"
    echo ""
    echo "示例:"
    echo "  $0 1000        # 快速测试"
    echo "  $0 1000000     # 100 万位"
    echo "  $0 100000000   # 1 亿位"
    echo ""
    echo "常用位数:"
    echo "  1000       - 快速测试 (~0.1 秒)"
    echo "  100000     - 高精度 (~1.5 秒)"
    echo "  1000000    - 超高精度 (~35 秒)"
    echo "  10000000   - 扩展精度 (~8 分钟)"
    echo "  100000000  - 1 亿位 (~2 小时)"
    exit 1
fi

DIGITS=$1
OUTPUT=$2

# JAR 文件路径（只使用 target 目录）
JAR_FILE="$SCRIPT_DIR/target/PiCalculator-2.0-HPC-jar-with-dependencies.jar"

# 如果 JAR 不存在，尝试编译
if [ ! -f "$JAR_FILE" ]; then
    echo "⚠️  未找到编译文件，正在编译项目..."
    ./build.sh
fi

# 自动检测系统总内存
get_total_memory() {
    local total=$(cat /proc/meminfo 2>/dev/null | grep MemTotal | awk '{print int($2/1024)}')
    if [ -z "$total" ] || [ "$total" = "0" ]; then
        total=$(free -m 2>/dev/null | awk '/^Mem:/{print $2}')
    fi
    if [ -z "$total" ] || [ "$total" = "0" ]; then
        total=8192  # 默认 8GB
    fi
    echo $total
}

# 根据位数计算合适的堆大小
calculate_heap_size() {
    local digits=$1
    local total_mem=$2

    # 根据位数估算所需内存
    local needed_mem=1024
    
    if [ $digits -ge 1000000000 ]; then
        # 10 亿位：需要 32GB+
        needed_mem=32768
    elif [ $digits -ge 100000000 ]; then
        # 1 亿位：需要 8-16GB
        needed_mem=12288
    elif [ $digits -ge 50000000 ]; then
        # 5000 万位：需要 6-10GB
        needed_mem=8192
    elif [ $digits -ge 10000000 ]; then
        # 1000 万位：需要 4-6GB
        needed_mem=5120
    elif [ $digits -ge 1000000 ]; then
        # 100 万位：需要 2-3GB
        needed_mem=2560
    else
        # 100 万位以下：1GB 足够
        needed_mem=1024
    fi

    # 使用总内存的 80% 作为上限
    local max_use=$((total_mem * 80 / 100))

    # 取较小值，但至少满足需求
    if [ $needed_mem -lt $max_use ]; then
        echo $needed_mem
    else
        echo $max_use
    fi
}

# 清理内存（释放缓存）
clean_memory() {
    # 尝试清理页面缓存（需要 root 权限）
    if [ -w /proc/sys/vm/drop_caches ]; then
        echo 1 > /proc/sys/vm/drop_caches 2>/dev/null || true
    fi
    # 同步文件系统
    sync
}

# 获取总内存
TOTAL_MEM=$(get_total_memory)
HEAP_SIZE=$(calculate_heap_size $DIGITS $TOTAL_MEM)

# 确保堆大小在合理范围内
if [ $HEAP_SIZE -lt 512 ]; then
    HEAP_SIZE=512
elif [ $HEAP_SIZE -gt 32768 ]; then
    HEAP_SIZE=32768
fi

# 设置 JVM 参数
JAVA_OPTS="-Xms${HEAP_SIZE}M -Xmx${HEAP_SIZE}M -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+AlwaysPreTouch"

# 如果系统支持 NUMA 且内存充足，启用 NUMA 优化
if [ $HEAP_SIZE -gt 4096 ] && [ -f /sys/devices/system/node/possible ]; then
    JAVA_OPTS="$JAVA_OPTS -XX:+UseNUMA"
fi

echo "╔═══════════════════════════════════════════════════════════"
echo "║              π 计算器 - 运行配置                          "
echo "╠═══════════════════════════════════════════════════════════"
echo "║  系统总内存：${TOTAL_MEM}MB"
echo "║  分配堆内存：${HEAP_SIZE}MB"
echo "║  计算位数：$DIGITS"
if [ -n "$OUTPUT" ]; then
    echo "║  输出文件：$OUTPUT"
fi
echo "╚═══════════════════════════════════════════════════════════"
echo ""

# 检查内存是否足够
if [ $DIGITS -ge 100000000 ] && [ $HEAP_SIZE -lt 8192 ]; then
    echo "⚠️  警告：计算 $DIGITS 位推荐至少 8GB 内存，当前配置可能不足"
    echo "   建议增加系统内存或使用以下 JVM 参数手动运行："
    echo "   java -Xms8G -Xmx16G -jar $JAR_FILE $DIGITS $OUTPUT"
    echo ""
fi

# 清理内存（可选）
echo "[系统] 正在清理内存缓存..."
clean_memory
sleep 1

# 运行程序
java $JAVA_OPTS -jar "$JAR_FILE" $DIGITS $OUTPUT
