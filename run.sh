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

# JAR 文件路径
JAR_FILE="$SCRIPT_DIR/target/PiCalculator-2.0-HPC-jar-with-dependencies.jar"

# 如果 JAR 不存在，尝试编译
if [ ! -f "$JAR_FILE" ]; then
    echo "⚠️  未找到编译文件，正在编译项目..."
    ./build.sh
fi

# 自动检测系统可用内存
get_available_memory() {
    local avail=$(free -m 2>/dev/null | awk '/^Mem:/{print $7}')
    if [ -z "$avail" ] || [ "$avail" = "0" ]; then
        local total=$(cat /proc/meminfo 2>/dev/null | grep MemTotal | awk '{print int($2/1024)}')
        if [ -n "$total" ] && [ "$total" != "0" ]; then
            avail=$((total / 2))
        else
            avail=2048
        fi
    fi
    echo $avail
}

# 根据位数计算合适的堆大小
calculate_heap_size() {
    local digits=$1
    local avail_mem=$2

    # 基础内存配置
    local base_mem=1024
    
    # 根据位数估算额外内存
    if [ $digits -ge 1000000000 ]; then
        # 10 亿位：需要 32GB+
        base_mem=32768
    elif [ $digits -ge 100000000 ]; then
        # 1 亿位：需要 8-16GB
        base_mem=8192
    elif [ $digits -ge 10000000 ]; then
        # 1000 万位：需要 2-4GB
        base_mem=2048
    elif [ $digits -ge 1000000 ]; then
        # 100 万位：需要 1-2GB
        base_mem=1024
    else
        # 100 万位以下：512MB 足够
        base_mem=512
    fi

    # 使用可用内存的 70%，但不超过所需内存
    local max_use=$((avail_mem * 70 / 100))

    # 取较小值
    if [ $base_mem -lt $max_use ]; then
        echo $base_mem
    else
        echo $max_use
    fi
}

# 获取可用内存
AVAIL_MEM=$(get_available_memory)
HEAP_SIZE=$(calculate_heap_size $DIGITS $AVAIL_MEM)

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
echo "║  可用内存：${AVAIL_MEM}MB"
echo "║  分配堆内存：${HEAP_SIZE}MB"
echo "║  计算位数：$DIGITS"
if [ -n "$OUTPUT" ]; then
    echo "║  输出文件：$OUTPUT"
fi
echo "╚═══════════════════════════════════════════════════════════"
echo ""

# 运行程序
java $JAVA_OPTS -jar "$JAR_FILE" $DIGITS $OUTPUT
