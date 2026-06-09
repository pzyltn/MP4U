package com.example.mediadownloader;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.io.File;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.prefs.Preferences;
import java.util.function.UnaryOperator;

public class MainController {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    @FXML private TextField urlField;
    @FXML private TextField fileNameField;
    @FXML private TextField downloadFolderField;
    @FXML private ComboBox<String> formatComboBox;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;

    // combo box options
    private static final String FORMAT_VIDEO = "Video (MP4)";
    private static final String FORMAT_AUDIO = "Audio (MP3)";

    // maintain user prefs
    private Preferences prefs = Preferences.userNodeForPackage(MainController.class);
    private static final String PREF_DOWNLOAD_FOLDER = "last_download_folder";
    private static final String PREF_FORMAT = "last_format";

    // regex to extract %s
    private static final Pattern PERCENT_PATTERN = Pattern.compile("\\[download\\]\\s+(\\d+(\\.\\d+)?)%");

    @FXML
    public void initialize() {
        // file name
        String forbiddenCharacters = "[<>:\"/\\\\|?*]";

        UnaryOperator<TextFormatter.Change> filter = change -> {
            String newText = change.getText();

            if (newText.matches(".*" + forbiddenCharacters + ".*")) {
                logger.debug("User attempted to input illegal file name character. Sanitizing input.");
                String sanitized = newText.replaceAll(forbiddenCharacters, "");
                change.setText(sanitized);
            }

            return change;
        };

        fileNameField.setTextFormatter(new TextFormatter<>(filter));

        // download location
        String defaultDownloadsPath = System.getProperty("user.home") + File.separator + "Downloads";

        // if pref not set yet, use Downloads/ as default
        String savedPath = prefs.get(PREF_DOWNLOAD_FOLDER, defaultDownloadsPath);
        if (savedPath.equals(defaultDownloadsPath)){
            logger.info("No custom download location found, using Downloads folder: {}", defaultDownloadsPath);
        } else {
            logger.info("Custom download location found: {}", savedPath);
        }

        downloadFolderField.setText(savedPath);

        // format selection
        formatComboBox.getItems().addAll(FORMAT_VIDEO, FORMAT_AUDIO);

        // if pref not set, use video as default
        String savedFormat = prefs.get(PREF_FORMAT, FORMAT_VIDEO);
        formatComboBox.setValue(savedFormat);

        formatComboBox.setOnAction(event -> {
            logger.info("Selected format: {}", formatComboBox.getValue());
            prefs.put(PREF_FORMAT, formatComboBox.getValue());
            logger.debug("Saved format to preferences.");
        });
    }

    @FXML
    protected void onBrowseClick() {
        File currentDir = new File(downloadFolderField.getText());
        Stage stage = (Stage) downloadFolderField.getScene().getWindow();
        File selectedDirectory = openDirectoryBrowser(stage, currentDir);

        if (selectedDirectory != null) {
            String newPath = selectedDirectory.getAbsolutePath();
            logger.info("User selected new download directory: {}", newPath);
            downloadFolderField.setText(newPath);
            prefs.put(PREF_DOWNLOAD_FOLDER, newPath);
            logger.debug("Successfully saved new download directory to preferences.");
        } else {
            logger.debug("User canceled directory selection.");
        }
    }

    protected File openDirectoryBrowser(Stage stage, File currentDir) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Set Download Location");

        // start browser at currently selected directory (downloads folder @ start)
        if (currentDir.exists() && currentDir.isDirectory()) {
            logger.debug("Opening file browser at existing directory: {}", currentDir.getAbsolutePath());
            directoryChooser.setInitialDirectory(currentDir);
        } else {
            logger.warn("Saved directory no longer exists or is invalid. Opening file browser at system default. Previous path: {}", currentDir.getAbsolutePath());
        }

        return directoryChooser.showDialog(stage);
    }

    @FXML
    protected void onDownloadClick() {
        String videoUrl = urlField.getText().trim();
        String fileName = fileNameField.getText().trim();
        String savePath = downloadFolderField.getText().trim();

        String output;

        // check that url input is not empty
        if (videoUrl.isEmpty()) {
            logger.warn("Download clicked with an empty URL.");
            showErrorAlert("Invalid URL", "Please enter a valid YouTube URL before clicking download.");
            return;
        }

        if (fileName.isEmpty()){
            logger.info("No custom file name provided. Using default title.");
            output = "%(title)s.%(ext)s";
        } else {
            logger.info("Using custom file name: {}", fileName);
            output = fileName + ".%(ext)s";
        }

        updateProgress(0, "Starting download...");

        // prevent UI from freezing during download
        new Thread(() -> {
            try {
                logger.info("Preparing to download: {} to {}", videoUrl, savePath);

                // determine correct path to executable
                String command = getBinaryPath();
                String ffmpeg = getFfmpegPath();

                boolean isAudioOnly = FORMAT_AUDIO.equals(formatComboBox.getValue());

                // build command
                ArrayList<String> commandList = new ArrayList<String>();
                commandList.add(command);
                commandList.add("-P");
                commandList.add(savePath);
                commandList.add("-o");
                commandList.add(output);
                commandList.add("--ffmpeg-location");
                commandList.add(getFfmpegPath());

                if (isAudioOnly) {
                    logger.info("Configuring yt-dlp for audio only (MP3)");
                    commandList.add("-x"); // automatically downloads ONLY audio
                    commandList.add("--audio-format");
                    commandList.add("mp3");
                    commandList.add("--audio-quality");
                    commandList.add("0"); // best bitrate

                } else {
                    logger.info("Configuring yt-dlp for audio and video (MP4)");
                    commandList.add("-f");
                    commandList.add("bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"); // ensure mp4 is produced
                }

                commandList.add(videoUrl);

                ProcessBuilder pb = new ProcessBuilder(commandList);
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

    private String getFfmpegPath() {
        String os = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");
        if (os.contains("win")) {
            return Paths.get(System.getenv("APPDATA"), "MP4U", "bin", "ffmpeg.exe").toString();
        } else {
            return Paths.get(userHome, "Library", "Application Support", "MP4U", "bin", "ffmpeg").toString();
        }
    }
}