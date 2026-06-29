package com.runlog.log;

import android.content.Context;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

public class FileLog {
    private static final int TAIL_LINES = 12;
    private final File logDir;
    private final File logFile;
    private final SimpleDateFormat stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    private final Deque<String> tailCache = new ArrayDeque<>();

    public FileLog(Context context) {
        logDir = new File(context.getFilesDir(), "logs");
        if (!logDir.exists()) logDir.mkdirs();
        logFile = new File(logDir, "runlog.log");
    }

    public File file() {
        return logFile;
    }

    public synchronized void append(String message) {
        String line = "[" + stamp.format(new Date()) + "] " + message + "\n";
        try (FileOutputStream out = new FileOutputStream(logFile, true)) {
            out.write(line.getBytes(StandardCharsets.UTF_8));
            cacheLine(line.trim());
        } catch (Exception ignored) {
        }
    }

    public synchronized String tail() {
        try {
            if (tailCache.isEmpty()) {
                hydrateTailCache();
            }
            if (tailCache.isEmpty()) return "";
            StringBuilder builder = new StringBuilder();
            for (String line : tailCache) {
                if (builder.length() > 0) builder.append('\n');
                builder.append(line);
            }
            return builder.toString();
        } catch (Exception e) {
            return "读取日志失败: " + e.getMessage();
        }
    }

    public synchronized void clear() {
        deleteChildren(logDir);
        tailCache.clear();
    }

    private void hydrateTailCache() throws Exception {
        tailCache.clear();
        if (!logFile.exists()) return;
        byte[] all = readAll(logFile);
        String text = new String(all, StandardCharsets.UTF_8);
        String[] lines = text.split("\n");
        int start = Math.max(0, lines.length - TAIL_LINES);
        for (int i = start; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!line.isEmpty()) cacheLine(line);
        }
    }

    private static byte[] readAll(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private void cacheLine(String line) {
        if (line == null || line.isEmpty()) return;
        tailCache.addLast(line);
        while (tailCache.size() > TAIL_LINES) {
            tailCache.removeFirst();
        }
    }

    private static void deleteChildren(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) deleteChildren(file);
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }
}
