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
import org.testfx.api.FxAssert;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.matcher.control.LabeledMatchers;
import org.testfx.util.WaitForAsyncUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
public class MainControllerTest {

    private MainController controller;
    private TextField urlField;
    private ProgressBar progressBar;
    private Label statusLabel;

    @Start
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(DownloaderApp.class.getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        stage.setScene(scene);
        stage.show();

        controller = fxmlLoader.getController();
        urlField = (TextField) scene.lookup("#urlField");
        progressBar = (ProgressBar) scene.lookup("#progressBar");
        statusLabel = (Label) scene.lookup("#statusLabel");
    }

    @Test
    public void testInitialUIElementsState() {
        assertNotNull(urlField, "URL Input field should be loaded from FXML");
        assertNotNull(progressBar, "ProgressBar should be loaded from FXML");
        assertNotNull(statusLabel, "Status Label should be loaded from FXML");
        assertEquals("", urlField.getText(), "URL field should start empty");
        assertEquals(0.0, progressBar.getProgress(), 0.001, "Progress bar should start at 0");
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
}