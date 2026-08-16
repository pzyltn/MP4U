package com.example.mediadownloader;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.control.Alert;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DownloaderApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(DownloaderApp.class);

    // Dynamic path variables needed for the Release functionality
    private static final String GITHUB_RELEASE_URL = "https://github.com/pzyltn/MP4U/releases/download/v0.1.0/";
    private Path binaryFolder;
    private String[] toolNames;
    private String zipName;

    @Override
    public void start(Stage stage) throws IOException {
        // run OS detection before running setupBinaries
        determinePathsAndTools();

        if (!setupBinaries()) {
            // if tools missing, show error and exit
            showError("Missing Tools", "Could not find or download yt-dlp, ffmpeg, or ffprobe.");
            System.exit(1);
        }

        // load the UI if everything okay
        FXMLLoader fxmlLoader = new FXMLLoader(DownloaderApp.class.getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        stage.setTitle("Media Downloader Pro");
        stage.setScene(scene);
        stage.show();
    }

    // map system folders and filenames based on OS
    private void determinePathsAndTools() {
        String os = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");

        if (os.contains("win")) {
            binaryFolder = resolveWindowsBinaryFolder(System.getenv("APPDATA"), userHome);
            toolNames = new String[]{"yt-dlp.exe", "ffmpeg.exe", "ffprobe.exe"};
            zipName = "windows-tools.zip";
        } else if (os.contains("mac")) {
            binaryFolder = Paths.get(userHome, "Library", "Application Support", "MP4U", "bin");
            toolNames = new String[]{"yt-dlp", "ffmpeg", "ffprobe"};
            zipName = "mac-tools.zip";
        } else {
            showError("Unsupported OS", "This operating system is not supported.");
            System.exit(1);
        }
    }

    static Path resolveWindowsBinaryFolder(String appData, String userHome) {
        String root = (appData == null) ? userHome : appData;
        return Paths.get(root, "MP4U", "bin");
    }

    private boolean setupBinaries() {
        logger.info("Checking local tools at: {}", binaryFolder.toAbsolutePath());

        // check if all tools present in the system folder, if they are, set permissions
        if(allToolsPresent()) {
            logger.info("All tools verified locally and ready!");
            return ensurePermissions();
        }

        // if tools missing, download the zip from GitHub Release and extract them
        logger.warn("Required tools missing locally. Initiating download from GitHub Release...");
        try {
            Files.createDirectories(binaryFolder);

            Path tempZipFile = binaryFolder.resolve("temp_dependencies.zip");
            downloadZip(zipName, tempZipFile);
            extractZip(tempZipFile);
            Files.deleteIfExists(tempZipFile);

            logger.info("Extraction completed and temporary files cleaned up.");
            return ensurePermissions();
        } catch (Exception e) {
            logger.error("Critical error while downloading or extracting release assets", e);
            return false;
        }
    }

    private boolean allToolsPresent() {
        for (String name : toolNames) {
            if (!binaryFolder.resolve(name).toFile().exists()) {
                return false;
            }
        }
        return true;
    }

    private void downloadZip(String zipName, Path destination) throws IOException, InterruptedException {
        String fullDownloadUrl = GITHUB_RELEASE_URL + zipName;
        logger.info("Downloading dependencies from: {}", fullDownloadUrl);

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullDownloadUrl))
                .GET()
                .build();

        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(destination));

        if (response.statusCode() != 200) {
            throw new IOException("Failed to download assets. Server status code: " + response.statusCode());
        }

        logger.info("Download completed successfully. Extracting assets...");
    }

    private void extractZip(Path zipFile) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path filePath = binaryFolder.resolve(entry.getName());
                if (!entry.isDirectory()) {
                    Files.createDirectories(filePath.getParent());
                    Files.copy(zis, filePath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    // ensure executable on mac
    private boolean ensurePermissions() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return true;
        }

        for (String name : toolNames) {
            File file = binaryFolder.resolve(name).toFile();
            boolean success = file.setExecutable(true);
            if (!success) {
                logger.error("Failed to set execution permissions for: {}", file.getName());
                return false;
            }
        }
        return true;
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch();
    }
}