#!/bin/bash

# π 计算器编译脚本
# 使用 Maven 编译项目

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "╔═══════════════════════════════════════════════════════════"
echo "║              π 计算器 - 编译脚本                          "
echo "╚═══════════════════════════════════════════════════════════"
echo ""

# 检查 Maven 是否存在
if ! command -v mvn &> /dev/null; then
    echo "❌ 错误：未找到 Maven (mvn) 命令"
    echo ""
    echo "请先安装 Maven:"
    echo "  Ubuntu/Debian: sudo apt-get install maven"
    echo "  CentOS/RHEL:   sudo yum install maven"
    echo "  macOS:         brew install maven"
    echo ""
    exit 1
fi

echo "[1/3] 检查 Maven 版本..."
mvn --version | head -1
echo ""

echo "[2/3] 清理旧的编译文件..."
mvn clean -q
echo "      ✓ 清理完成"
echo ""

echo "[3/3] 编译项目..."
mvn package -DskipTests -q

if [ $? -eq 0 ]; then
    echo "      ✓ 编译成功"
    echo ""
    echo "╔═══════════════════════════════════════════════════════════"
    echo "║                    编译完成 ✓                            "
    echo "╠═══════════════════════════════════════════════════════════"
    echo "║  JAR 文件位置：                                           "
    echo "║    target/PiCalculator-2.0-HPC.jar                       "
    echo "║    target/PiCalculator-2.0-HPC-jar-with-dependencies.jar "
    echo "║                                                          "
    echo "║  运行命令：                                              "
    echo "║    ./java-pi 1000000                                     "
    echo "║    或                                                    "
    echo "║    java -Xms4G -Xmx8G -jar target/PiCalculator-2.0-HPC-jar-with-dependencies.jar 1000000"
    echo "╚═══════════════════════════════════════════════════════════"
else
    echo "      ❌ 编译失败"
    exit 1
fi
