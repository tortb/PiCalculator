#!/bin/bash

# π计算器安装脚本
# 将 java-pi 命令安装到系统路径

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INSTALL_DIR="/usr/local/bin"

echo "正在安装 π计算器..."

# 检查是否需要 sudo
if [ ! -w "$INSTALL_DIR" ]; then
    echo "需要管理员权限安装到 $INSTALL_DIR"
    echo "正在使用 sudo 安装..."
    sudo cp "$SCRIPT_DIR/java-pi" "$INSTALL_DIR/"
    sudo chmod +x "$INSTALL_DIR/java-pi"
else
    cp "$SCRIPT_DIR/java-pi" "$INSTALL_DIR/"
    chmod +x "$INSTALL_DIR/java-pi"
fi

echo ""
echo "✓ 安装成功！"
echo ""
echo "现在可以使用以下命令计算π:"
echo "  java-pi 100        # 计算 100 位"
echo "  java-pi 1000000    # 计算 100 万位"
echo ""
echo "或者在项目目录使用:"
echo "  ./run.sh 1000000"
