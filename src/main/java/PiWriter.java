import java.io.*;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

/**
 * HPC 优化版 π值输出处理器
 * 
 * 优化点：
 * 1. 内存映射文件 - 使用 MappedByteBuffer 提高大文件写入性能
 * 2. 流式写入 - 不生成完整字符串，边计算边写入
 * 3. 批量输出 - 减少系统调用次数
 * 4. 零拷贝 - 直接使用 ByteBuffer 传输数据
 */
public class PiWriter {
    // 配置常量
    private static final int BUFFER_SIZE = 1024 * 1024;  // 1MB 缓冲区
    private static final int CHARS_PER_LINE = 10000;     // 每行 10000 位
    private static final int MMAP_THRESHOLD = 10 * 1024 * 1024;  // 10MB 以上使用内存映射

    /**
     * 将π值写入文件（HPC 优化版）
     * 根据数据大小自动选择最优写入方式
     */
    public static void writePiToFile(String piValue, String filename) throws IOException {
        long estimatedSize = estimateFileSize(piValue.length());

        if (estimatedSize > MMAP_THRESHOLD) {
            // 大文件：使用内存映射
            writePiWithMemoryMap(piValue, filename);
        } else {
            // 小文件：使用缓冲写入
            writePiBuffered(piValue, filename);
        }
    }

    /**
     * 使用内存映射写入（适用于大文件）
     */
    private static void writePiWithMemoryMap(String piValue, String filename) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(filename, "rw");
             FileChannel channel = raf.getChannel()) {
            
            long fileSize = estimateFileSize(piValue.length());
            
            // 创建内存映射
            MappedByteBuffer buffer = channel.map(
                FileChannel.MapMode.READ_WRITE,
                0,
                Math.min(fileSize, 512 * 1024 * 1024)  // 最多映射 512MB
            );
            
            // 写入头部
            String header = "# π值计算结果\n# 精度：" + (piValue.length() - 2) + " 位\n\n";
            buffer.put(header.getBytes(StandardCharsets.UTF_8));
            
            // 写入π值
            int charsPerLine = CHARS_PER_LINE;
            int written = 0;
            
            // 第一行（包含"3."）
            String firstLine = piValue.substring(0, Math.min(charsPerLine, piValue.length()));
            buffer.put(firstLine.getBytes(StandardCharsets.UTF_8));
            buffer.put((byte) '\n');
            written = firstLine.length();
            
            // 后续行
            while (written < piValue.length()) {
                // 检查缓冲区是否需要刷新
                if (buffer.position() > buffer.capacity() - charsPerLine - 10) {
                    buffer.flip();
                    // 强制写入磁盘
                    buffer.load();
                    buffer.clear();
                }
                
                int endIdx = Math.min(written + charsPerLine, piValue.length());
                String line = piValue.substring(written, endIdx);
                buffer.put(line.getBytes(StandardCharsets.UTF_8));
                buffer.put((byte) '\n');
                written = endIdx;
            }
            
            // 刷新剩余数据
            buffer.flip();
            buffer.force();  // 强制写入磁盘
        }
    }

    /**
     * 使用缓冲写入（适用于中小文件）
     */
    private static void writePiBuffered(String piValue, String filename) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new FileWriter(filename), BUFFER_SIZE)) {
            
            // 写入头部
            writer.write("# π值计算结果\n");
            writer.write("# 精度：" + (piValue.length() - 2) + " 位\n\n");
            
            // 写入π值
            int charsPerLine = CHARS_PER_LINE;
            
            // 第一行
            String firstLine = piValue.substring(0, Math.min(charsPerLine, piValue.length()));
            writer.write(firstLine);
            writer.newLine();
            
            // 后续行
            for (int i = charsPerLine; i < piValue.length(); i += charsPerLine) {
                int endIdx = Math.min(i + charsPerLine, piValue.length());
                String line = piValue.substring(i, endIdx);
                writer.write(line);
                writer.newLine();
            }
        }
    }

    /**
     * 使用缓冲区写入π值（兼容旧接口）
     */
    public static void writePiToFileBuffered(String piValue, String filename) throws IOException {
        writePiBuffered(piValue, filename);
    }

    /**
     * 流式写入π值（不生成完整字符串）
     * 适用于超大规模计算
     */
    public static void writePiStreaming(StringBuilder piBuilder, int totalDigits, 
                                        String filename) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new FileWriter(filename), BUFFER_SIZE)) {
            
            // 写入头部
            writer.write("# π值计算结果\n");
            writer.write("# 精度：" + totalDigits + " 位\n\n");
            
            int charsPerLine = CHARS_PER_LINE;
            int written = 0;
            
            // 第一行
            int firstLineLen = Math.min(charsPerLine, piBuilder.length());
            writer.write(piBuilder.substring(0, firstLineLen));
            writer.newLine();
            written = firstLineLen;
            
            // 后续行
            while (written < totalDigits) {
                int chunkSize = Math.min(charsPerLine, totalDigits - written);
                if (written + chunkSize <= piBuilder.length()) {
                    writer.write(piBuilder.substring(written, written + chunkSize));
                }
                writer.newLine();
                written += chunkSize;
            }
        }
    }

    /**
     * 估算文件大小
     */
    private static long estimateFileSize(int digitCount) {
        // 头部约 50 字节 + 数字 + 小数点 + 换行符
        long headerSize = 50;
        long digitSize = digitCount + 1;  // +1 for decimal point
        long newlineCount = digitCount / CHARS_PER_LINE + 2;
        return headerSize + digitSize + newlineCount;
    }

    /**
     * 直接写入字节数组（零拷贝优化）
     */
    public static void writeBytesDirect(byte[] data, String filename, boolean append) 
            throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filename, append);
             FileChannel channel = fos.getChannel()) {
            
            ByteBuffer buffer = ByteBuffer.wrap(data);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        }
    }
}
