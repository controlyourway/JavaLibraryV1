package com.controlyourway.examples.GUI;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

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