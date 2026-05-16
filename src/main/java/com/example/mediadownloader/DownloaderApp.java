package com.example.mediadownloader;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.control.Alert;
import java.io.File;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DownloaderApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(DownloaderApp.class);

    @Override
    public void start(Stage stage) throws IOException {
        if (!setupBinaries()) {
            // if tools missing, show error and exit
            showError("Missing Tools", "Could not find yt-dlp or ffmpeg or ffprobe in the /bin folder.");
            System.exit(1);
        }

        // load the UI if everything okay
        FXMLLoader fxmlLoader = new FXMLLoader(DownloaderApp.class.getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        stage.setTitle("Media Downloader Pro");
        stage.setScene(scene);
        stage.show();
    }

    private boolean setupBinaries() {
        logger.info("Starting binary check...");

        String os = System.getProperty("os.name").toLowerCase();
        logger.debug("Operating System detected: {}", os);
        String baseDir = System.getProperty("user.dir") + File.separator + "bin" + File.separator;

        // define the tools we need for each OS
        String[] toolNames;
        String subDir;

        if (os.contains("win")) {
            subDir = "windows" + File.separator;
            toolNames = new String[]{"yt-dlp.exe", "ffmpeg.exe", "ffprobe.exe"};
        } else if (os.contains("mac")) {
            subDir = "mac" + File.separator;
            toolNames = new String[]{"yt-dlp", "ffmpeg", "ffprobe"};
        } else {
            System.err.println("Unsupported OS: " + os);
            return false;
        }

        for (String name : toolNames) {
            File file = new File(baseDir + subDir + name);
            String fileName = file.getName();

            // check if tool is present
            if (!file.exists()) {
                logger.error("Critical Error: {} missing", fileName);
                return false;
            }

            // ensure file is executable on mac
            if (!os.contains("win")) {
                boolean success = file.setExecutable(true);
                if (!success) {
                    logger.error("Warning: Could not set permissions for {}", fileName);
                    // don't return false, maybe already set to executable
                }
            }
        }

        logger.info("All tools verified and ready!");
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
