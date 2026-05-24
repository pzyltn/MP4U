package com.example.mediadownloader;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainController {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    @FXML private TextField urlField;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;
    @FXML private TextField downloadFolderField;

    // regex to extract %s
    private static final Pattern PERCENT_PATTERN = Pattern.compile("\\[download\\]\\s+(\\d+(\\.\\d+)?)%");

    @FXML
    public void initialize() {
        String defaultDownloadsPath = System.getProperty("user.home") + File.separator + "Downloads";
        downloadFolderField.setText(defaultDownloadsPath);
    }

    @FXML
    protected void onBrowseClick() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Set Download Location");

        // start browser at currently selected directory (downloads folder @ start)
        File currentDir = new File(downloadFolderField.getText());
        if (currentDir.exists() && currentDir.isDirectory()) {
            directoryChooser.setInitialDirectory(currentDir);
        }

        Stage stage = (Stage) downloadFolderField.getScene().getWindow();
        File selectedDirectory = directoryChooser.showDialog(stage);

        if (selectedDirectory != null) {
            downloadFolderField.setText(selectedDirectory.getAbsolutePath());
        }
    }

    @FXML
    protected void onDownloadClick() {
        String videoUrl = urlField.getText().trim();
        String savePath = downloadFolderField.getText().trim();

        // check that url input is not empty
        if (videoUrl.isEmpty()) {
            logger.warn("Download clicked with an empty URL.");
            showErrorAlert("Invalid URL", "Please enter a valid YouTube URL before clicking download.");
            return;
        }

        updateProgress(0, "Starting download...");

        // prevent UI from freezing during download
        new Thread(() -> {
            try {
                logger.info("Preparing to download: {} to {}", videoUrl, savePath);

                // determine correct path to executable
                String command = getBinaryPath();

                // make call to the executable
                ProcessBuilder pb = new ProcessBuilder(command, "-P", savePath, videoUrl);
                pb.redirectErrorStream(true); // includes error logs in the standard output stream

                logger.debug("Executing command: {}", command);
                Process process = pb.start();

                // read output from yt-dlp and send it to logger & UI
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.debug("[yt-dlp] {}", line);

                        // parse the line for progress percentages
                        Matcher matcher = PERCENT_PATTERN.matcher(line);
                        if (matcher.find()) {
                            double percentage = Double.parseDouble(matcher.group(1));
                            // expects value between 0.0 and 1.0
                            double progressValue = percentage / 100.0;

                            // update the progress bar in real time
                            updateProgress(progressValue, "Downloading: " + matcher.group(1) + "%");
                        }
                    }
                }

                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    logger.info("Download finished successfully!");
                    updateProgress(1.0, "Download Complete!");
                } else {
                    logger.error("Download failed with exit code: {}", exitCode);
                    updateProgress(0, "Download Failed.");

                    // trigger popup alert
                    showErrorAlert(
                            "Download Failed",
                            "The download tool encountered an error.\n\n Please verify that the URL is a direct video link and try again."
                    );
                }

            } catch (Exception e) {
                logger.error("A critical error occurred during the process", e);
                updateProgress(0, "An error occurred.");
                // trigger popup alert
                showErrorAlert("Critical Error", "An unexpected error occurred: " + e.getMessage());
            }
        }).start();
    }

    // update the progress bar & status tracker
    private void updateProgress(double progress, String statusText) {
        Platform.runLater(() -> {
            progressBar.setProgress(progress);
            statusLabel.setText(statusText);
        });
    }

    // show popup alerts
    private void showErrorAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private String getBinaryPath() {
        String os = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");

        if (os.contains("win")) {
            return Paths.get(System.getenv("APPDATA"), "MP4U", "bin", "yt-dlp.exe").toString();
        } else {
            return Paths.get(userHome, "Library", "Application Support", "MP4U", "bin", "yt-dlp").toString();
        }
    }
}