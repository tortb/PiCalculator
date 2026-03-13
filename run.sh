#!/bin/bash

# π计算器运行脚本
# 用法: ./run.sh <位数> [输出文件名]
# 示例: ./run.sh 1000000
#       ./run.sh 1000000 pi_result.md

if [ $# -lt 1 ]; then
    echo "用法: $0 <位数> [输出文件名]"
    echo "示例: $0 1000000"
    echo "示例: $0 1000000 pi_result.md"
    exit 1
fi

DIGITS=$1
OUTPUT=$2

# 自动检测系统可用内存并设置合适的 JVM 参数
get_available_memory() {
    # 获取可用内存 (MB)
    local avail=$(free -m | awk '/^Mem:/{print $7}')
    echo $avail
}

# 根据位数和可用内存计算合适的堆大小
calculate_heap_size() {
    local digits=$1
    local avail_mem=$2
    
    # 基础内存 2GB
    local base_mem=2048
    
    # 根据位数估算额外内存需求 (每 100 万位约需 100MB)
    local extra_mem=$((digits / 1000000 * 100))
    
    # 计算所需内存
    local needed=$((base_mem + extra_mem))
    
    # 使用可用内存的 70%，但不超过所需内存
    local max_use=$((avail_mem * 70 / 100))
    
    # 取较小值，但至少有 512MB
    if [ $max_use -lt 512 ]; then
        max_use=512
    fi
    
    if [ $needed -lt $max_use ]; then
        echo $needed
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
elif [ $HEAP_SIZE -gt 16384 ]; then
    HEAP_SIZE=16384
fi

# 设置 JVM 参数
JAVA_OPTS="-Xms${HEAP_SIZE}M -Xmx${HEAP_SIZE}M -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+AlwaysPreTouch"

# 如果系统支持 NUMA 且内存充足，启用 NUMA 优化
if [ $HEAP_SIZE -gt 4096 ] && [ -f /sys/devices/system/node/possible ]; then
    JAVA_OPTS="$JAVA_OPTS -XX:+UseNUMA"
fi

echo "可用内存：${AVAIL_MEM}MB, 分配堆内存：${HEAP_SIZE}MB"
echo "正在计算π，位数：$DIGITS..."

# 运行程序
java $JAVA_OPTS -jar PiCalculator.jar $DIGITS $OUTPUT
