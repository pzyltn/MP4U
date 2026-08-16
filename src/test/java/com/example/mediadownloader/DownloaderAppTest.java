package com.example.mediadownloader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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

        Method method = DownloaderApp.class.getDeclaredMethod("determinePathsAndTools");
        method.setAccessible(true);
        method.invoke(app);

        assertEquals("mac-tools.zip", getPrivateField(app, "zipName"),
                "Zip should be named mac-tools.zip.");

        String[] tools = (String[]) getPrivateField(app, "toolNames");
        assertArrayEquals(new String[]{"yt-dlp", "ffmpeg", "ffprobe"}, tools,
                "Tools should include yt-dlp, ffmpeg, and ffprobe.");

        Path folder = (Path) getPrivateField(app, "binaryFolder");
        assertTrue(folder.endsWith(Path.of("MP4U", "bin")),
                "Binary folder path should end with MP4U/bin.");
    }

    @Test
    public void testPathBuilder_SetsWindowsVariables() throws Exception {
        System.setProperty("os.name", "Windows 11");
        DownloaderApp app = new DownloaderApp();

        Method method = DownloaderApp.class.getDeclaredMethod("determinePathsAndTools");
        method.setAccessible(true);
        method.invoke(app);

        assertEquals("windows-tools.zip", getPrivateField(app, "zipName"),
                "Zip should be named windows-tools.zip.");

        String[] tools = (String[]) getPrivateField(app, "toolNames");
        assertArrayEquals(new String[]{"yt-dlp.exe", "ffmpeg.exe", "ffprobe.exe"}, tools,
                "Tools should include yt-dlp, ffmpeg, and ffprobe.");

        Path folder = (Path) getPrivateField(app, "binaryFolder");
        assertTrue(folder.endsWith(Path.of("MP4U", "bin")),
                "Binary folder path should end with MP4U/bin.");
    }

    @Test
    public void testResolveWindowsBinaryFolder_UsesAppDataWhenPresent() {
        Path result = DownloaderApp.resolveWindowsBinaryFolder("C:\\Users\\foo\\AppData\\Roaming", "C:\\Users\\foo");
        assertEquals(Path.of("C:\\Users\\foo\\AppData\\Roaming", "MP4U", "bin"), result,
                "Should use APPDATA when it's present.");
    }

    @Test
    public void testResolveWindowsBinaryFolder_UsesUserHomeWhenAppDataAbsent() {
        Path result = DownloaderApp.resolveWindowsBinaryFolder(null, "C:\\Users\\foo");
        assertEquals(Path.of("C:\\Users\\foo", "MP4U", "bin"), result,
                "Should fall back to using user home when APPDATA is not set.");
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

        Method setupBinariesMethod = DownloaderApp.class.getDeclaredMethod("setupBinaries");
        setupBinariesMethod.setAccessible(true);

        boolean result = (boolean) setupBinariesMethod.invoke(app);

        assertTrue(result, "Should return true immediately if all tools are verified locally.");
    }

    @Test
    public void testExtractZip_ExtractsFilesFromZip(@TempDir Path tempDir) throws Exception {
        DownloaderApp app = new DownloaderApp();

        setPrivateField(app, "binaryFolder", tempDir);

        Path zipFile = tempDir.resolve("test.zip");

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream((zipFile.toFile())))) {
            ZipEntry entry = new ZipEntry("yt-dlp");
            zos.putNextEntry(entry);
            zos.write("Fake zip data".getBytes());
            zos.closeEntry();
        }

        Method extractMethod = DownloaderApp.class.getDeclaredMethod("extractZip", Path.class);
        extractMethod.setAccessible(true);
        extractMethod.invoke(app, zipFile);
        Path extracted = tempDir.resolve("yt-dlp");

        assertTrue(Files.exists(extracted), "The fake yt-dlp file should be extracted from the zip.");
        assertEquals("Fake zip data", Files.readString(extracted),
                "Extracted file content should match the zip entry's content.");
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = DownloaderApp.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Object getPrivateField(Object target, String fieldName) throws Exception {
        Field field = DownloaderApp.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}