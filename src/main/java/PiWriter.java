import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

/**
 * π值输出处理器，支持内存映射文件输出
 */
public class PiWriter {
    private static final int BUFFER_SIZE = 8192;
    
    /**
     * 将π值写入文件，每行10000位
     * @param piValue π值字符串
     * @param filename 输出文件名
     * @throws IOException 文件操作异常
     */
    public static void writePiToFile(String piValue, String filename) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(filename, "rw");
             FileChannel channel = file.getChannel()) {
            
            // 使用内存映射文件提高大文件写入性能
            MappedByteBuffer buffer = channel.map(
                FileChannel.MapMode.READ_WRITE, 
                0, 
                Math.max(piValue.length() * 2L, 1024 * 1024) // 至少分配1MB空间
            );
            
            // 写入头部信息
            String header = "# π值计算结果\n# 精度: " + (piValue.length() - 2) + " 位\n\n";
            buffer.put(header.getBytes(StandardCharsets.UTF_8));
            
            // 写入π值，每行10000位（除了第一行包含"3."）
            int charsPerLine = 10000;
            int written = 0;
            
            // 写入第一行（包含"3."）
            String firstLine = piValue.substring(0, Math.min(charsPerLine, piValue.length()));
            buffer.put(firstLine.getBytes(StandardCharsets.UTF_8));
            buffer.put("\n".getBytes(StandardCharsets.UTF_8));
            written = firstLine.length();
            
            // 写入后续行
            while (written < piValue.length()) {
                int endIdx = Math.min(written + charsPerLine, piValue.length());
                String line = piValue.substring(written, endIdx);
                buffer.put(line.getBytes(StandardCharsets.UTF_8));
                buffer.put("\n".getBytes(StandardCharsets.UTF_8));
                written = endIdx;
            }
        }
    }
    
    /**
     * 使用缓冲区写入π值到文件
     * @param piValue π值字符串
     * @param filename 输出文件名
     * @throws IOException 文件操作异常
     */
    public static void writePiToFileBuffered(String piValue, String filename) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename), BUFFER_SIZE)) {
            // 写入头部信息
            writer.write("# π值计算结果\n");
            writer.write("# 精度: " + (piValue.length() - 2) + " 位\n\n");
            
            // 写入π值，每行10000位
            int charsPerLine = 10000;
            
            // 写入第一行（包含"3."）
            String firstLine = piValue.substring(0, Math.min(charsPerLine, piValue.length()));
            writer.write(firstLine);
            writer.newLine();
            
            // 写入后续行
            for (int i = charsPerLine; i < piValue.length(); i += charsPerLine) {
                int endIdx = Math.min(i + charsPerLine, piValue.length());
                String line = piValue.substring(i, endIdx);
                writer.write(line);
                writer.newLine();
            }
        }
    }
}