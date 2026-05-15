module com.example.mediadownloader {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;

    opens com.example.mediadownloader to javafx.fxml;
    exports com.example.mediadownloader;
}