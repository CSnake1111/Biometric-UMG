module app {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;
    requires javafx.media;
    requires java.sql;
    requires org.postgresql.jdbc;
    requires com.google.zxing;
    requires com.google.zxing.javase;
    requires kernel;
    requires layout;
    requires io;
    requires java.mail;
    requires org.controlsfx.controls;
    requires webcam.capture;
    requires java.desktop;
    requires org.slf4j;
    requires static opencv;
    requires jdk.httpserver;
    requires java.prefs;

    opens app             to javafx.fxml;
    opens app.controladores to javafx.fxml;
    opens app.modelos     to javafx.base, javafx.fxml;
    opens app.seguridad   to javafx.fxml;

    exports app;
    exports app.modelos;
    exports app.dao;
    exports app.servicios;
    exports app.controladores;
    exports app.conexion;
    exports app.seguridad;
}