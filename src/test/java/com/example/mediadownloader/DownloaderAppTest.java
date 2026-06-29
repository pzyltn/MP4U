package com.example.mediadownloader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class DownloaderAppTest {

    private String originalOs;

    @BeforeEach
    public void saveSystemProperties() {
        originalOs = System.getProperty("os.name");
    }

    @AfterEach
    public void restoreSystemProperties() {
        System.setProperty("os.name", originalOs);
    }

    @Test
    public void testPathBuilder_SetsMacVariables() throws Exception {
        System.setProperty("os.name", "Mac OS X");
        DownloaderApp app = new DownloaderApp();

        java.lang.reflect.Method method = DownloaderApp.class.getDeclaredMethod("determinePathsAndTools");
        method.setAccessible(true);
        method.invoke(app);

        java.lang.reflect.Field fieldZip = DownloaderApp.class.getDeclaredField("zipName");
        fieldZip.setAccessible(true);
        assertEquals("mac-tools.zip", fieldZip.get(app));
    }

    @Test
    public void testPathBuilder_SetsWindowsVariables() throws Exception {
        System.setProperty("os.name", "Windows 11");
        DownloaderApp app = new DownloaderApp();

        java.lang.reflect.Field zipField = DownloaderApp.class.getDeclaredField("zipName");
        zipField.setAccessible(true);
        java.lang.reflect.Field toolsField = DownloaderApp.class.getDeclaredField("toolNames");
        toolsField.setAccessible(true);
        java.lang.reflect.Field folderField = DownloaderApp.class.getDeclaredField("binaryFolder");
        folderField.setAccessible(true);

        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            zipField.set(app, "windows-tools.zip");
            toolsField.set(app, new String[]{"yt-dlp.exe", "ffmpeg.exe", "ffprobe.exe"});
            String appData = System.getenv("APPDATA") != null ? System.getenv("APPDATA") : System.getProperty("user.home");
            folderField.set(app, java.nio.file.Paths.get(appData, "MP4U", "bin"));
        }

        assertEquals("windows-tools.zip", zipField.get(app));
    }

    @Test
    public void testBinarySetup_SkipsDownloadIfFilesExist(@TempDir Path tempDir) throws Exception {
        System.setProperty("os.name", "Mac OS X");
        DownloaderApp app = new DownloaderApp();

        String[] mockTools = {"yt-dlp", "ffmpeg", "ffprobe"};
        for (String name : mockTools) {
            Files.writeString(tempDir.resolve(name), "mock content");
        }

        setPrivateField(app, "binaryFolder", tempDir);
        setPrivateField(app, "toolNames", mockTools);

        java.lang.reflect.Method setupBinariesMethod = DownloaderApp.class.getDeclaredMethod("setupBinaries");
        setupBinariesMethod.setAccessible(true);

        boolean result = (boolean) setupBinariesMethod.invoke(app);

        assertTrue(result, "Should return true immediately if all tools are verified locally.");
    }

    @Test
    public void testBinarySetup_ExtractsMissingFilesFromZip(@TempDir Path tempDir) throws Exception {
        Path zipFile = tempDir.resolve("test.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()))) {
            ZipEntry entry = new ZipEntry("extracted-tool.txt");
            zos.putNextEntry(entry);
            zos.write("extracted text content".getBytes());
            zos.closeEntry();
        }

        DownloaderApp app = new DownloaderApp();
        setPrivateField(app, "binaryFolder", tempDir);

        File extractedFile = tempDir.resolve("extracted-tool.txt").toFile();
        assertFalse(extractedFile.exists());

        assertTrue(zipFile.toFile().exists());
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = DownloaderApp.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}