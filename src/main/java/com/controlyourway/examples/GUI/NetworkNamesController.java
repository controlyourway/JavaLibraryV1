package com.controlyourway.examples.GUI;

import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Collection;

/**
 * Created by alangley on 22/11/15.
 */
public class NetworkNamesController {

    @FXML
    public Button buttonCancel;
    @FXML
    public Button buttonSave;
    @FXML
    public Button buttonAdd;
    @FXML
    public Button buttonDelete;
    @FXML
    public TextField textNetworkName;
    @FXML
    public ListView<String> listNetworkNames;



    // Button Handlers
    @FXML
    protected void handleAddNetworkName(ActionEvent event) throws IOException {
        _networkNamesProperty.getValue().add(_networkName.get());
        _networkName.setValue(null);
    }

    @FXML
    protected void handleDeleteNetworkName(ActionEvent event) throws IOException {
        if (_selectedNetworkName.get() > -1) {
            _networkNamesProperty.getValue().remove(_selectedNetworkName.get());
        }
    }

    @FXML
    protected void handleCancel(ActionEvent event) throws IOException {
        _wasCanceled = true;
        close();
    }

    @FXML
    protected void handleSave(ActionEvent event) throws IOException {
        close();
    }

    private Stage _stage;
    public void setStage(Stage stage)
    {
        _stage = stage;

        ObservableList<String> observableList = FXCollections.observableArrayList();
        _networkNamesProperty.setValue(observableList);

        Bindings.bindBidirectional(listNetworkNames.itemsProperty(), _networkNamesProperty);
        _selectedNetworkName.bind(listNetworkNames.getSelectionModel().selectedIndexProperty());
        Bindings.bindBidirectional(textNetworkName.textProperty(), _networkName);
    }

    private boolean _wasCanceled;
    public boolean getWasCanceled()
    {
        return _wasCanceled;
    }

    public void setNetworkNames(Collection<String> items)
    {
        ObservableList<String> observableList = FXCollections.observableArrayList(items);
        _networkNamesProperty.setValue(observableList);
    }

    public Collection<String> getNetworkNames() {
        return _networkNamesProperty.get();
    }
    private ListProperty<String> _networkNamesProperty = new SimpleListProperty<String>();
    private StringProperty _networkName = new SimpleStringProperty();
    private IntegerProperty _selectedNetworkName = new SimpleIntegerProperty();
    private void close()
    {
        _stage.close();
    }
}
