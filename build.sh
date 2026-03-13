#!/bin/bash

# 编译Java项目
echo "正在编译项目..."
javac -d ./build -cp ".:./build/*" ./src/main/java/*.java

if [ $? -eq 0 ]; then
    echo "编译成功！"
    
    # 创建jar包
    echo "正在创建jar包..."
    jar cfe PiCalculator.jar Main -C ./build .
    
    if [ $? -eq 0 ]; then
        echo "jar包创建成功！"
        echo "使用以下命令运行程序："
        echo "java -Xms8G -Xmx16G -XX:+UseG1GC -XX:+UseNUMA -XX:+AlwaysPreTouch -jar PiCalculator.jar <位数>"
        echo ""
        echo "例如："
        echo "java -Xms8G -Xmx16G -XX:+UseG1GC -XX:+UseNUMA -XX:+AlwaysPreTouch -jar PiCalculator.jar 1000000"
    else
        echo "创建jar包失败！"
    fi
else
    echo "编译失败！"
fi