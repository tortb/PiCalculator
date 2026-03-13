#!/bin/bash
# 编译并打包

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "编译 Java 源文件..."
javac "$SCRIPT_DIR/src/PiCalculator.java"

echo "打包为可执行 JAR..."
cd "$SCRIPT_DIR/src" && jar cfm ../pi.jar ../manifest.mf *.class

echo "完成！使用方式:"
echo "  ./pi -p 10000"
echo "  java -jar pi.jar -p 10000"
