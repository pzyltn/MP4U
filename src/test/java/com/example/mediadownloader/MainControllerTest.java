package com.example.mediadownloader;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.nio.file.Paths;
import java.util.List;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
public class MainControllerTest {

    private MainController controller;

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

    @Start
    public void start(Stage stage) throws Exception {
        Preferences.userNodeForPackage(MainController.class).clear();

        controller = Mockito.spy(new MainController());

        FXMLLoader fxmlLoader = new FXMLLoader(DownloaderApp.class.getResource("main-view.fxml"));
        fxmlLoader.setControllerFactory(param -> controller);

        Scene scene = new Scene(fxmlLoader.load());
        stage.setScene(scene);
        stage.show();
    }

    @Test
    public void testRegexParser_ExtractsPercentage() {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[download\\]\\s+(\\d+(\\.\\d+)?)%");

        java.util.regex.Matcher matcher1 = pattern.matcher("[download]  12.5% of 45.00MiB at 4.23MiB/s");
        java.util.regex.Matcher matcher2 = pattern.matcher("[download] 100% of 12.11MiB");

        assertTrue(matcher1.find());
        assertEquals("12.5", matcher1.group(1));

        assertTrue(matcher2.find());
        assertEquals("100", matcher2.group(1));
    }

    @Test
    public void testResolveToolPath_ResolvesCorrectOs() throws Exception {
        java.lang.reflect.Method method = MainController.class.getDeclaredMethod("resolveToolPath", String.class, String.class);
        method.setAccessible(true);

        String currentOs = System.getProperty("os.name");
        String userHome = System.getProperty("user.home");

        try {
            System.setProperty("os.name", "Mac OS X");
            String macResult = (String) method.invoke(controller, "yt-dlp.exe", "yt-dlp");
            assertEquals(Paths.get(userHome, "Library", "Application Support", "MP4U", "bin", "yt-dlp").toString(),
                    macResult, "Mac binary path should resolve under ~/Library/Application Support/MP4U/bin");

            System.setProperty("os.name", "Windows 11");
            String winResult = (String) method.invoke(controller, "yt-dlp.exe", "yt-dlp");
            String expectedAppData = (System.getenv("APPDATA") == null) ? userHome : System.getenv("APPDATA");
            assertEquals(Paths.get(expectedAppData, "MP4U", "bin", "yt-dlp.exe").toString(), winResult,
                    "Win binary path should resolve under APPDATA, or userHome if APPDATA is not set.");
        } finally {
            System.setProperty("os.name", currentOs);
        }
    }

    @Test
    public void testAudioCommandBuilder() {
        List<String> result = controller.constructCommand(
                "/Downloads", "test.mp3", true,
                VID_HD, AUD_BEST, "None", "https://youtube.com"
        );

        assertTrue(result.contains("-x"), "Audio downloads must include the -x flag.");
        assertTrue(result.contains("0"), "Best audio must use VBR level 0.");
    }

    @Test
    public void testVideoCommandBuilder(){
        List<String> result = controller.constructCommand(
                "/Downloads", "test.mp4", false,
                VID_HD, AUD_STANDARD, "None", "https://youtube.com"
        );

        assertTrue(result.contains("bestvideo[height<=1080][ext=mp4]+bestaudio[abr<=128][ext=m4a]/best[height<=1080][ext=mp4]/best"),
                "Video download should specify HD quality (1080p) and standard audio (128kbps), with fallback.");
    }

    @Test
    public void testCaptionsCommandBuilder(){
        List<String> resultPos = controller.constructCommand(
                "/Downloads", "test.mp4", false,
                VID_HD, AUD_STANDARD, "Spanish", "https://youtube.com"
        );

        assertTrue(resultPos.contains("es"),
                "Downloads with captions include the applicable language flag.");
    }
}