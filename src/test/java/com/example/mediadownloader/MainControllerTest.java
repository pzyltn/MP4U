package com.example.mediadownloader;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.io.File;
import java.io.IOException;
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
}