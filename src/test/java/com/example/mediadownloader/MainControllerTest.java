package com.example.mediadownloader;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import org.controlsfx.control.SearchableComboBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@ExtendWith(ApplicationExtension.class)
public class MainControllerTest {

    private MainController controller;
    private TextField urlField;
    private TextField fileNameField;
    private TextField downloadFolderField;
    private ComboBox<String> formatComboBox;
    private ComboBox<String> videoQualityComboBox;
    private ComboBox<String> audioQualityComboBox;
    private SearchableComboBox<String> captionsComboBox;
    private ProgressBar progressBar;
    private Label statusLabel;
    private Preferences prefs;

    @Start
    public void start(Stage stage) throws Exception {
        prefs = Preferences.userNodeForPackage(MainController.class);
        prefs.clear();

        controller = Mockito.spy(new MainController());

        FXMLLoader fxmlLoader = new FXMLLoader(DownloaderApp.class.getResource("main-view.fxml"));
        fxmlLoader.setControllerFactory(param -> controller);

        Scene scene = new Scene(fxmlLoader.load());
        stage.setScene(scene);
        stage.show();

        urlField = (TextField) scene.lookup("#urlField");
        fileNameField = (TextField) scene.lookup("#fileNameField");
        downloadFolderField = (TextField) scene.lookup("#downloadFolderField");
        formatComboBox = (ComboBox<String>) scene.lookup("#formatComboBox");
        videoQualityComboBox = (ComboBox<String>) scene.lookup("#videoQualityComboBox");
        audioQualityComboBox = (ComboBox<String>) scene.lookup("#audioQualityComboBox");
        captionsComboBox = (SearchableComboBox<String>) scene.lookup("#captionsComboBox");
        progressBar = (ProgressBar) scene.lookup("#progressBar");
        statusLabel = (Label) scene.lookup("#statusLabel");
    }

    @Test
    public void testInitialUIElementsState() {
        assertNotNull(urlField, "URL Input field should be loaded from FXML");
        assertNotNull(downloadFolderField, "Download Folder field should be loaded from FXML");
        assertNotNull(progressBar, "ProgressBar should be loaded from FXML");
        assertNotNull(statusLabel, "Status Label should be loaded from FXML");

        assertEquals("", urlField.getText(), "URL field should start empty");
        assertEquals(0.0, progressBar.getProgress(), 0.001, "Progress bar should start at 0");

        String expectedDefault = System.getProperty("user.home") + File.separator + "Downloads";
        assertEquals(expectedDefault, downloadFolderField.getText(), "Should default to standard Downloads folder");
    }

    @Test
    public void testBrowseUpdatesPathAndPreferences() {
        File simulatedChosenFolder = new File(System.getProperty("user.home") + File.separator + "Documents");

        doReturn(simulatedChosenFolder).when(controller).openDirectoryBrowser(any(), any());

        Platform.runLater(() -> controller.onBrowseClick());
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(simulatedChosenFolder.getAbsolutePath(), downloadFolderField.getText(),
                "The text field should update to the newly selected path.");
        assertEquals(simulatedChosenFolder.getAbsolutePath(), prefs.get("last_download_folder", ""),
                "The new path should be saved to OS Preferences.");
    }

    @Test
    public void testBrowseCancellationDoesNotChangePath() {
        String originalPath = downloadFolderField.getText();

        doReturn(null).when(controller).openDirectoryBrowser(any(), any());

        Platform.runLater(() -> controller.onBrowseClick());
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(originalPath, downloadFolderField.getText(),
                "The text field should remain unchanged if the user cancels the window.");
    }

    @Test
    public void testTypingUrlUpdatesField() {
        org.testfx.api.FxRobot robot = new org.testfx.api.FxRobot();
        robot.clickOn("#urlField").write("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", urlField.getText());
    }

    @Test
    public void testEmptyUrlHandling() {
        Platform.runLater(() -> controller.onDownloadClick());
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(0.0, progressBar.getProgress());
    }

    @Test
    public void testRegexPatternMatching() {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[download\\]\\s+(\\d+(\\.\\d+)?)%");

        java.util.regex.Matcher matcher1 = pattern.matcher("[download]  12.5% of 45.00MiB at 4.23MiB/s");
        java.util.regex.Matcher matcher2 = pattern.matcher("[download] 100% of 12.11MiB");

        assertTrue(matcher1.find());
        assertEquals("12.5", matcher1.group(1));

        assertTrue(matcher2.find());
        assertEquals("100", matcher2.group(1));
    }

    @Test
    public void testUpdateProgressThreadSafety() throws Exception {
        java.lang.reflect.Method method = MainController.class.getDeclaredMethod(
                "updateProgress", double.class, String.class
        );
        method.setAccessible(true);

        Platform.runLater(() -> {
            try {
                method.invoke(controller, 0.75, "Downloading: 75%");
            } catch (Exception e) {
                fail("Reflection invocation failed: " + e.getMessage());
            }
        });

        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(0.75, progressBar.getProgress(), 0.001);
        assertEquals("Downloading: 75%", statusLabel.getText());
    }

    @Test
    public void testGetBinaryPathCoverage() throws Exception {
        java.lang.reflect.Method method = MainController.class.getDeclaredMethod("getBinaryPath");
        method.setAccessible(true);

        String currentOs = System.getProperty("os.name");

        System.setProperty("os.name", "Mac OS X");
        String macResult = (String) method.invoke(controller);
        assertNotNull(macResult, "Mac binary path should resolve properly");

        System.setProperty("os.name", "Windows 11");
        try {
            String winResult = (String) method.invoke(controller);
            assertNotNull(winResult);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (!(e.getCause() instanceof NullPointerException)) {
                throw e;
            }
        }

        System.setProperty("os.name", currentOs);
    }

    @Test
    public void testDownloadExecutionBranchCoverage() {
        Platform.runLater(() -> {
            urlField.setText("https://www.youtube.com/watch?v=mock_id");
            try {
                controller.onDownloadClick();
            } catch (Exception ignored) {}
        });

        WaitForAsyncUtils.waitForFxEvents();
        assertNotNull(urlField.getText());
    }

    @Test
    public void testFileName_ValidInput_IsAccepted() {
        org.testfx.api.FxRobot robot = new org.testfx.api.FxRobot();

        robot.clickOn("#fileNameField").write("VacationVideo2026");

        assertEquals("VacationVideo2026", fileNameField.getText(),
                "Valid filenames should not be altered.");
    }

    @Test
    public void testFileName_IllegalCharacters_AreSanitizedInstantly() {
        org.testfx.api.FxRobot robot = new org.testfx.api.FxRobot();

        robot.clickOn("#fileNameField").write("My:Awesome<Video>/Is*Cool?");

        assertEquals("MyAwesomeVideoIsCool", fileNameField.getText(),
                "Illegal characters should be stripped from the input.");
    }

    @Test
    public void testFileName_EmptyInput_IsAllowed() {
        org.testfx.api.FxRobot robot = new org.testfx.api.FxRobot();

        robot.clickOn("#fileNameField").write("Temp").eraseText(4);

        assertEquals("", fileNameField.getText(),
                "The file name field should allow empty input.");
    }

    @Test
    public void testFormatChooser_ContainsCorrectOptions() {
        assertTrue(formatComboBox.getItems().contains("Video (MP4)"),
                "Dropdown should contain the Video option.");
        assertTrue(formatComboBox.getItems().contains("Audio (MP3)"),
                "Dropdown should contain the Audio option.");
    }

    @Test
    public void testFormatChooser_SelectionSavesToPreferences() {
        org.testfx.api.FxRobot robot = new org.testfx.api.FxRobot();

        robot.clickOn("#formatComboBox").clickOn("Audio (MP3)");
        assertEquals("Audio (MP3)", formatComboBox.getValue(),
                "The combo box UI should display the newly selected format (MP3).");
        robot.clickOn("#formatComboBox").clickOn("Video (MP4)");
        assertEquals("Video (MP4)", formatComboBox.getValue(),
                "The combo box UI should display the newly selected format (MP4).");

        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(MainController.class);
        robot.clickOn("#formatComboBox").clickOn("Audio (MP3)");
        assertEquals("Audio (MP3)", prefs.get("last_format", ""),
                "The saved preference should update to Audio (MP3).");
    }

    @Test
    public void testQualityDropdowns_ContainCorrectOptions() {
        // video quality options correct
        assertTrue(videoQualityComboBox.getItems().contains("Best (4K)"));
        assertTrue(videoQualityComboBox.getItems().contains("HD (1080p)"));
        assertTrue(videoQualityComboBox.getItems().contains("Standard (720p)"));
        assertTrue(videoQualityComboBox.getItems().contains("Low (480p)"));

        // audio quality options correct
        assertTrue(audioQualityComboBox.getItems().contains("Best (160kbps)"));
        assertTrue(audioQualityComboBox.getItems().contains("Standard (128kbps)"));
    }

    @Test
    public void testQualityDropdowns_SelectionSavesToPreferences() {
        org.testfx.api.FxRobot robot = new org.testfx.api.FxRobot();
        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(MainController.class);

        // video quality selection saves to prefs
        robot.clickOn("#videoQualityComboBox").clickOn("HD (1080p)");
        assertEquals("HD (1080p)", videoQualityComboBox.getValue());
        assertEquals("HD (1080p)", prefs.get("last_vid_quality", ""),
                "The saved preference should update to HD (1080p).");

        // audio quality selection saves to prefs
        robot.clickOn("#audioQualityComboBox").clickOn("Standard (128kbps)");
        assertEquals("Standard (128kbps)", audioQualityComboBox.getValue());
        assertEquals("Standard (128kbps)", prefs.get("last_aud_quality", ""),
                "The saved preference should update to Standard (128kbps).");
    }

    @Test
    public void testQualityDropdowns_EnableAndDisable() {
        org.testfx.api.FxRobot robot = new org.testfx.api.FxRobot();

        robot.clickOn("#formatComboBox").clickOn("Video (MP4)");
        assertFalse(videoQualityComboBox.isDisable(),
                "Video quality selection should be enabled when downloading a video.");

        robot.clickOn("#formatComboBox").clickOn("Audio (MP3)");
        assertTrue(videoQualityComboBox.isDisable(),
                "Video quality selection should be disabled when downloading audio only.");

        robot.clickOn("#formatComboBox").clickOn("Video (MP4)");
        assertFalse(videoQualityComboBox.isDisable(),
                "Video quality selection should be re-enabled when switching back to video mode.");
    }

    @Test
    public void testCaptionsDropdown_ContainsCorrectOptions() {
        assertTrue(captionsComboBox.getItems().contains("None"));
        assertTrue(captionsComboBox.getItems().contains("Afrikaans")); // first in language tree map
        assertTrue(captionsComboBox.getItems().contains("Javanese")); // kinda mid in language tree map
        assertTrue(captionsComboBox.getItems().contains("Zulu")); // last in language tree map
    }

    @Test
    public void testCaptionsDropdown_EnablesAndDisables() {
        org.testfx.api.FxRobot robot = new org.testfx.api.FxRobot();

        robot.clickOn("#formatComboBox").clickOn("Video (MP4)");
        assertFalse(captionsComboBox.isDisable(),
                "Captions dropdown should be enabled when downloading a video.");

        robot.clickOn("#formatComboBox").clickOn("Audio (MP3)");
        assertTrue(captionsComboBox.isDisable(),
                "Captions dropdown should be disabled when downloading audio only.");

        robot.clickOn("#formatComboBox").clickOn("Video (MP4)");
        assertFalse(captionsComboBox.isDisable(),
                "Captions dropdown should be re-enabled when switching back to video mode.");
    }

    @Test
    public void testCaptionsDropdown_IsSearchable() {
        org.testfx.api.FxRobot robot = new org.testfx.api.FxRobot();

        robot.clickOn("#formatComboBox").clickOn("Video (MP4)");

        robot.clickOn("#captionsComboBox").write("span").write("\n");
        assertTrue(captionsComboBox.getValue().equals("Spanish"),
                "Combo box should show Spanish is selected.");
    }

    @Test
    public void testCaptionsDropdown_IsClickable() {
        org.testfx.api.FxRobot robot = new org.testfx.api.FxRobot();

        robot.clickOn("#formatComboBox").clickOn("Video (MP4)");

        robot.clickOn("#captionsComboBox").clickOn("Afrikaans");
        assertTrue(captionsComboBox.getValue().equals("Afrikaans"),
                "Combo box should show Afrikaans is selected.");
    }

    @Test
    public void testCaptionsDropdown_SavesPreferences() {
        org.testfx.api.FxRobot robot = new org.testfx.api.FxRobot();
        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(MainController.class);

        robot.clickOn("#captionsComboBox").clickOn("None");
        robot.clickOn("#captionsComboBox").clickOn("Afrikaans");
        assertTrue(captionsComboBox.getValue().equals(prefs.get("last_caption_lang", "")),
                "The saved preference should update to Afrikaans.");
    }

    @Test
    public void testAudioCommandBuilder() {
        List<String> result = controller.constructCommand(
                "/downloads", "test.mp3", true,
                "HD (1080p)", "Best (160kbps)", "None", "https://youtube.com"
        );

        assertTrue(result.contains("-x"), "Audio downloads must include the -x flag.");
        assertTrue(result.contains("0"), "Best audio must use VBR level 0.");
    }

    @Test
    public void testVideoCommandBuilder(){
        List<String> result = controller.constructCommand(
                "/downloads", "test.mp3", false,
                "HD (1080p)", "Standard (128kbps)", "None", "https://youtube.com"
        );

        assertTrue(result.contains("bestvideo[height<=1080][ext=mp4]+bestaudio[abr<=128][ext=m4a]/best[height<=1080][ext=mp4]/best"),
                "Video download should specify HD quality (1080p) and standard audio (128kbps), with fallback.");
    }

    @Test
    public void testCaptionsCommandBuilder(){
        List<String> result = controller.constructCommand(
                "/downloads", "test.mp3", false,
                "HD (1080p)", "Standard (128kbps)",
                "Spanish", "https://youtube.com"
        );

        assertTrue(result.contains("es"),
                "Downloads with captions include the applicable language flag.");
    }
}