package com.controlyourway.examples.GUI;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.event.Event;

/**
 * Created by alangley on 21/10/15.
 */
public class Program extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            // load the graphical layout (known as a View)
            FXMLLoader loader = new FXMLLoader();
            URL res = Program.class.getResource("/MainView.fxml");
            loader.setLocation(res);

            // grab the root control in the View
            Pane page = (Pane) loader.load();

            Scene scene = new Scene(page);

            primaryStage.setScene(scene);
            primaryStage.setTitle("Control Your Way");
            primaryStage.setOnCloseRequest(event -> {
                Platform.exit();
                System.exit(0);
            });

            // the View should have already created an instance of a controller class for us - grab it.
            MainController controller = loader.getController();

            // inject the stage into the controller, so it can do windowy things
            controller.setStage(primaryStage);
            primaryStage.show();

        } catch (Exception ex) {
            Logger.getLogger(Program.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}