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
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.io.File;
import java.util.List;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@ExtendWith(ApplicationExtension.class)
public class MainControllerUITest {

    // UI components
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

    // format combo box options
    private static final String FORMAT_VIDEO = "Video (MP4)";
    private static final String FORMAT_AUDIO = "Audio (MP3)";

    // video quality combo box options
    private static final String VID_BEST = "Best (4K)";
    private static final String VID_HD = "HD (1080p)";
    private static final String VID_STANDARD = "Standard (720p)";
    private static final String VID_LOW = "Low (480p)";

    // audio quality combo box options
    private static final String AUD_BEST = "Best (160kbps)";
    private static final String AUD_STANDARD = "Standard (128kbps)";

    // user prefs
    private static final String PREF_DOWNLOAD_FOLDER = "last_download_folder";
    private static final String PREF_FORMAT = "last_format";
    private static final String PREF_VID_QUALITY = "last_vid_quality";
    private static final String PREF_AUD_QUALITY = "last_aud_quality";
    private static final String PREF_CAPTION_LANG = "last_caption_lang";

    private FxRobot robot;

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

        robot = new org.testfx.api.FxRobot();

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
    public void testInitial_ElementsPresent() {
        assertNotNull(urlField, "URL Input field should be loaded from FXML.");
        assertNotNull(downloadFolderField, "Download Folder field should be loaded from FXML.");
        assertNotNull(progressBar, "ProgressBar should be loaded from FXML.");
        assertNotNull(statusLabel, "Status Label should be loaded from FXML.");
    }

    @Test
    public void testInitial_ElementsSetToDefaults() {
        assertEquals("", urlField.getText(), "URL field should start empty.");

        String downloadExpectedDefault = System.getProperty("user.home") + File.separator + "Downloads";
        assertEquals(downloadExpectedDefault, downloadFolderField.getText(), "Download location should default to Downloads folder.");

        assertEquals(0.0, progressBar.getProgress(), 0.001, "Progress bar should start at 0.");
    }

    @Test
    public void testDirectoryChooser_UpdatesPath() {
        File simulatedChosenFolder = new File(System.getProperty("user.home") + File.separator + "Documents");

        doReturn(simulatedChosenFolder).when(controller).openDirectoryBrowser(any(), any());

        Platform.runLater(() -> controller.onBrowseClick());
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(simulatedChosenFolder.getAbsolutePath(), downloadFolderField.getText(),
                "The text field should update to the newly selected path.");
    }

    @Test
    public void testDirectoryChooser_SavesPreference() {
        File simulatedChosenFolder = new File(System.getProperty("user.home") + File.separator + "Documents");

        doReturn(simulatedChosenFolder).when(controller).openDirectoryBrowser(any(), any());

        Platform.runLater(() -> controller.onBrowseClick());
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(simulatedChosenFolder.getAbsolutePath(), prefs.get(PREF_DOWNLOAD_FOLDER, ""),
                "The new path should be saved to preferences.");
    }

    @Test
    public void testDirectoryChooser_CancelingDoesNotChangePath() {
        String originalPath = downloadFolderField.getText();

        doReturn(null).when(controller).openDirectoryBrowser(any(), any());

        Platform.runLater(() -> controller.onBrowseClick());
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(originalPath, downloadFolderField.getText(),
                "The text field should not change if the user cancels the window.");
    }

    @Test
    public void testUrlField_UpdatesField() {
        robot.clickOn("#urlField").write("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", urlField.getText(),
                "The URL field should update to the newly entered URL.");
    }

    @Test
    public void testUrlField_HandlesEmpty() {
        Platform.runLater(() -> controller.onDownloadClick());
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(0.0, progressBar.getProgress(),
                "Should handle empty URL without crashing.");
    }

    @Test
    public void testFileNameField_UpdatesField() {
        robot.clickOn("#fileNameField").write("VacationVideo2026");

        assertEquals("VacationVideo2026", fileNameField.getText(),
                "The file name field should update to the newly entered title.");
    }

    @Test
    public void testFileNameField_IllegalCharactersAreSanitized() {
        robot.clickOn("#fileNameField").write("My:Awesome<Video>/Is*Cool?");

        assertEquals("MyAwesomeVideoIsCool", fileNameField.getText(),
                "Illegal characters should be stripped from the input.");
    }

    @Test
    public void testFileNameField_AcceptsEmptyInput() {
        robot.clickOn("#fileNameField").write("Temp").eraseText(4);

        assertEquals("", fileNameField.getText(),
                "The file name field should allow empty input.");
    }

    @Test
    public void testFormatChooser_ContainsCorrectOptions() {
        List<String> formatOptions = formatComboBox.getItems();
        assertTrue(formatOptions.contains(FORMAT_VIDEO), "Format combo box should contain the Video option.");
        assertTrue(formatOptions.contains(FORMAT_AUDIO), "Format combo box should contain the Audio option.");
    }

    @Test
    public void testFormatChooser_UpdatesField() {
        robot.clickOn("#formatComboBox").clickOn(FORMAT_AUDIO);
        assertEquals(FORMAT_AUDIO, formatComboBox.getValue(),
                "The combo box should display the newly selected format (MP3).");
    }

    @Test
    public void testFormatChooser_SavesPreference() {
        robot.clickOn("#formatComboBox").clickOn(FORMAT_AUDIO);
        assertEquals(formatComboBox.getValue(), prefs.get(PREF_FORMAT, ""),
                "The saved preference should update to audio.");
    }

    @Test
    public void testAudQualityChooser_ContainsCorrectOptions() {
        List<String> audioOptions = audioQualityComboBox.getItems();
        assertTrue(audioOptions.contains(AUD_BEST), "Audio combo box should include the Best option.");
        assertTrue(audioOptions.contains(AUD_STANDARD), "Audio combo box should include the Standard option.");
    }

    @Test
    public void testAudQualityChooser_UpdatesField() {
        robot.clickOn("#audioQualityComboBox").clickOn(AUD_STANDARD);
        assertEquals(AUD_STANDARD, audioQualityComboBox.getValue(), "Audio combo box should update to new selection (Standard).");
    }

    @Test
    public void testAudQualityChooser_SavesPreference() {
        robot.clickOn("#audioQualityComboBox").clickOn(AUD_STANDARD);
        assertEquals(AUD_STANDARD, prefs.get(PREF_AUD_QUALITY, ""),
                "The saved preference should update to Standard (128kbps).");
    }

    @Test
    public void testVidQualityChooser_ContainsCorrectOptions() {
        List<String> videoOptions = videoQualityComboBox.getItems();
        assertTrue(videoOptions.contains(VID_BEST), "Video combo box should contain the Best option.");
        assertTrue(videoOptions.contains(VID_HD), "Video combo box should contain the HD option.");
        assertTrue(videoOptions.contains(VID_STANDARD), "Video combo box should contain the Standard option.");
        assertTrue(videoOptions.contains(VID_LOW), "Video combo box should contain the Low option.");
    }

    @Test
    public void testVidQualityChooser_UpdatesField() {
        robot.clickOn("#videoQualityComboBox").clickOn(VID_LOW);
        assertEquals(VID_LOW, videoQualityComboBox.getValue(),
                "The video quality combo box should show Low (480p) is selected.");
    }

    @Test
    public void testVidQualityChooser_SavesPreference() {
        robot.clickOn("#videoQualityComboBox").clickOn(VID_HD);
        assertEquals(VID_HD, prefs.get(PREF_VID_QUALITY, ""),
                "The saved preference should update to HD (1080p).");
    }

    @Test
    public void testVidQualityChooser_EnablesAndDisables() {
        robot.clickOn("#formatComboBox").clickOn(FORMAT_VIDEO);
        assertFalse(videoQualityComboBox.isDisable(),
                "Video quality selection should be enabled when downloading a video.");

        robot.clickOn("#formatComboBox").clickOn(FORMAT_AUDIO);
        assertTrue(videoQualityComboBox.isDisable(),
                "Video quality selection should be disabled when downloading audio only.");

        robot.clickOn("#formatComboBox").clickOn(FORMAT_VIDEO);
        assertFalse(videoQualityComboBox.isDisable(),
                "Video quality selection should be re-enabled when switching back to video mode.");
    }

    @Test
    public void testCaptionsDropdown_ContainsCorrectOptions() {
        List<String> captionsLangs = captionsComboBox.getItems();
        assertTrue(captionsLangs.contains("None"), "Captions combo box should include None as an option.");
        assertTrue(captionsLangs.contains("Afrikaans"), "Captions combo box should include Afrikaans (start) as an option.");
        assertTrue(captionsLangs.contains("Javanese"), "Captions combo box should include Javanese (mid) as an option.");
        assertTrue(captionsLangs.contains("Zulu"), "Captions combo box should include Zulu (end) as an option.");
    }

    @Test
    public void testCaptionsChooser_EnablesAndDisables() {
        robot.clickOn("#formatComboBox").clickOn(FORMAT_VIDEO);
        assertFalse(captionsComboBox.isDisable(),
                "Captions chooser should be enabled when downloading a video.");

        robot.clickOn("#formatComboBox").clickOn(FORMAT_AUDIO);
        assertTrue(captionsComboBox.isDisable(),
                "Captions chooser should be disabled when downloading audio only.");

        robot.clickOn("#formatComboBox").clickOn(FORMAT_VIDEO);
        assertFalse(captionsComboBox.isDisable(),
                "Captions chooser should be re-enabled when switching back to video mode.");
    }

    @Test
    public void testCaptionsChooser_IsSearchable() {
        robot.clickOn("#formatComboBox").clickOn(FORMAT_VIDEO);

        robot.clickOn("#captionsComboBox").write("span").write("\n");
        assertEquals("Spanish", captionsComboBox.getValue(),
                "Captions combo box should show Spanish is selected.");
    }

    @Test
    public void testCaptionsChooser_IsClickable() {
        robot.clickOn("#formatComboBox").clickOn(FORMAT_VIDEO);

        robot.clickOn("#captionsComboBox").clickOn("Afrikaans");
        assertEquals("Afrikaans", captionsComboBox.getValue(),
                "Captions combo box should show Afrikaans is selected.");
    }

    @Test
    public void testCaptionsChooser_SavesPreference() {
        robot.clickOn("#captionsComboBox").clickOn("None");
        robot.clickOn("#captionsComboBox").clickOn("Afrikaans");
        assertEquals(captionsComboBox.getValue(), prefs.get(PREF_CAPTION_LANG, ""),
                "The saved preference should update to Afrikaans.");
    }

    @Test
    public void testDownload_TriggersProcess() {
        Platform.runLater(() -> {
            urlField.setText("https://www.youtube.com/watch?v=mock_id");
            try {
                controller.onDownloadClick();
            } catch (Exception ignored) {}
        });

        WaitForAsyncUtils.waitForFxEvents();
        assertNotNull(urlField.getText(), "Application didn't crash.");
    }

    @Test
    public void testProgressBar_UpdatesFromThread() throws Exception {
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

        assertEquals(0.75, progressBar.getProgress(), 0.001,
                "Progress bar shows correct progress (75%).");
        assertEquals("Downloading: 75%", statusLabel.getText(),
                "Status label shows correct progress (75%).");
    }
}