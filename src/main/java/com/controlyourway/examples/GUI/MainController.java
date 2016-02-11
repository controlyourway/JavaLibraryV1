package com.controlyourway.examples.GUI;

import com.controlyourway.ConnectionListener;
import com.controlyourway.CywCloudInterface;
import com.controlyourway.CywDataToSend;
import com.controlyourway.DataReceivedEvent;
import com.controlyourway.examples.Logger;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import jfxtras.scene.control.ToggleGroupValue;

import java.io.Console;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;

/**
 * Created by alangley on 2/11/15.
 */
public class MainController implements Initializable, ConnectionListener {
    private CywCloudInterface _cloud;

    @Override
    public void initialize(URL url, ResourceBundle resources)
    {
        bindControlsToProperties();

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if ( null != _cloud)
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            _requestCountProperty.setValue(_cloud.getHttpDownloadRequestCount());
                        }
                    });

            }
        }, 1000, 1000);
    }

    /*
    This method associates all the 'property' fields, to their associated UI controls.
    It also includes 'binding rules' which define how a property and control are associated.
    For example, a boolean property may be bound to the text of a button, however the rule defines that
    when the boolean is True, the text is "Stop", and when False, it's "Start".

    Binding is a much better way to implement UI business rules than procedural code which explicitly sets control states.
    The latter can get very messy, very quickly - and user interfaces have a tendency to be 'manipultable' in an infinite number
    of ways - meaning you'd need just as many conditional code branches in traditional procedural code! Yuck!
     */
    private void bindControlsToProperties(){
        radioConnMethodAuto.setUserData("auto");
        toggleGroupConnMethod.add( radioConnMethodAuto, radioConnMethodAuto.getUserData() );
        radioConnMethodLongPolling.setUserData("longpolling");
        toggleGroupConnMethod.add( radioConnMethodLongPolling, radioConnMethodLongPolling.getUserData() );
        radioConnMethodWebSocket.setUserData("websocket");
        toggleGroupConnMethod.add( radioConnMethodWebSocket, radioConnMethodWebSocket.getUserData() );

        radioSendToDefaultNetworks.setUserData("default");
        toggleGroupSendTo.add( radioSendToDefaultNetworks, radioSendToDefaultNetworks.getUserData() );
        radioSendToSessionIDs.setUserData("sessions");
        toggleGroupSendTo.add( radioSendToSessionIDs, radioSendToSessionIDs.getUserData() );
        radioSendToSelectedNetworks.setUserData("networks");
        toggleGroupSendTo.add( radioSendToSelectedNetworks, radioSendToSelectedNetworks.getUserData() );

        Bindings.bindBidirectional(userNameTextField.textProperty(), _userNameProperty);
        Bindings.bindBidirectional(networkPasswordTextField.textProperty(), _networkPasswordProperty);
        Bindings.bindBidirectional(dataTypeTextField.textProperty(), _dataTypeProperty);
        Bindings.bindBidirectional(textToSendTextField.textProperty(), _textToSendProperty);

        sessionIdLabel.textProperty().bind(_sessionIdProperty);
        requestCountLabel.textProperty().bind(Bindings.convert(_requestCountProperty));
        sendCountLabel.textProperty().bind(Bindings.convert(_sendCountProperty));
        Bindings.bindBidirectional(useEncryptionCheckBox.selectedProperty(), _useEncryptionProperty);

        buttonStart.textProperty().bind(Bindings.when(_isConnected).then("Stop").otherwise("Start"));

        userNameTextField.disableProperty().bind(_isConnected);
        networkPasswordTextField.disableProperty().bind(_isConnected);

        Bindings.bindBidirectional(enableDebugMessagesCheckBox.selectedProperty(), _enableDebugMessagesProperty);

        Bindings.bindBidirectional(sendToSessionIdsTextField.textProperty(), _sendToSessionIds);
        sendToSessionIdsTextField.disableProperty().bind(radioSendToSessionIDs.selectedProperty().not());

        Bindings.bindBidirectional(selectedNetworkNamesListView.itemsProperty(), _selectedNetworkNames);
        selectedNetworkNamesListView.disableProperty().bind(radioSendToSelectedNetworks.selectedProperty().not());
        selectedNetworkNamesListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        _selectedNetworkNamesSelection.bind(new SimpleListProperty<String>(selectedNetworkNamesListView.getSelectionModel().getSelectedItems()));

        Bindings.bindBidirectional(toggleGroupConnMethod.valueProperty(), _connMethodName);
        Bindings.bindBidirectional(toggleGroupSendTo.valueProperty(), _sendToMethodName);

        Bindings.bindBidirectional(addCarriageReturnCheckBox.selectedProperty(), _addCarriageReturnProperty);
        Bindings.bindBidirectional(addLineFeedCheckBox.selectedProperty(), _addLineFeedProperty);

        Bindings.bindBidirectional(addSendCountToStringCheckBox.selectedProperty(), _addSendCountToStringProperty);

        Bindings.bindBidirectional(textReceivedTextField.textProperty(), _textReceivedProperty);
        Bindings.bindBidirectional(messageListView.itemsProperty(), _messageProperty);

        _enableDebugMessagesProperty.addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                if (null != _cloud)
                    _cloud.setEnableDebugMessages(newValue);
            }
        });

        _useEncryptionProperty.addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                if ( null != _cloud)
                    try {
                        _cloud.setUseEncryption(newValue);
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
            }
        });
    }

    private void addStatusMessage(String message)
    {
        // delegate across to the UI thread - necessary whenever we want to manipulate the user interface.
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                _messageProperty.get().add(message);
                messageListView.scrollTo(_messageProperty.get().size() - 1);

            }
        });
    }


    @Override
    public void connectionStatusDelegate(Boolean connected) {
        if (connected) {
            // delegate across to the UI thread - necessary whenever we want to manipulate the user interface.
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    _isConnected.setValue(connected);

                    addStatusMessage("ConnectionStatus - Connection Successful");

                    _sessionIdProperty.setValue(_cloud.getSessionId());
                }
            });
        }
        else {
            // delegate across to the UI thread - necessary whenever we want to manipulate the user interface.
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    System.out.println("Disconnected.");

                    _isConnected.setValue(connected);
                    _sessionIdProperty.setValue(null);
                }
            });

        }
    }

    @Override
    public void dataReceived(DataReceivedEvent event) {
        // delegate across to the UI thread - necessary whenever we want to manipulate the user interface.
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                String str = new String(event.data, StandardCharsets.UTF_8);
                _textReceivedProperty.setValue(str);
                StringBuilder sb = new StringBuilder()
                        .append("Data type: ")
                        .append(event.dataType)
                        .append(" from: ")
                        .append(event.fromSessionID)
                        .append("\r\n")
                        .append(str);
                //_messageProperty.setValue(sb.toString());
            }
        });

    }

    @Override
    public void debugMessages(String message) {
        System.out.println("Debug: " + message);
        // delegate across to the UI thread - necessary whenever we want to manipulate the user interface.
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                addStatusMessage(message);
            }
        });
    }

    @Override
    public void error(String errorCode) {
        System.out.println("Error: " + errorCode);
    }

    private BooleanProperty _isConnected = new SimpleBooleanProperty(false);

    private StringProperty _userNameProperty = new SimpleStringProperty("hubert@robospace.co.nz");
    private StringProperty _networkPasswordProperty = new SimpleStringProperty("hrj123");
    private StringProperty _sessionIdProperty = new SimpleStringProperty("sessionid");
    private IntegerProperty _requestCountProperty = new SimpleIntegerProperty(0);
    private IntegerProperty _sendCountProperty = new SimpleIntegerProperty(0);
    private BooleanProperty _useEncryptionProperty = new SimpleBooleanProperty(true);
    private BooleanProperty _enableDebugMessagesProperty = new SimpleBooleanProperty(true);
    private StringProperty _dataTypeProperty = new SimpleStringProperty("test data");
    private StringProperty _textToSendProperty = new SimpleStringProperty("test message");

    private BooleanProperty _addCarriageReturnProperty = new SimpleBooleanProperty(false);
    private BooleanProperty _addLineFeedProperty = new SimpleBooleanProperty(false);

    private BooleanProperty _addSendCountToStringProperty = new SimpleBooleanProperty(true);

    private StringProperty _sendToSessionIds = new SimpleStringProperty();
    private ListProperty<String> _selectedNetworkNames = new SimpleListProperty<String>();
    private ListProperty<String> _selectedNetworkNamesSelection = new SimpleListProperty<String>();

    private StringProperty _textReceivedProperty = new SimpleStringProperty("textreceived");
    private ListProperty<String> _messageProperty = new SimpleListProperty<String>(FXCollections.observableArrayList());
    private IntegerProperty _messageIndexProperty = new SimpleIntegerProperty();

    private StringProperty _connMethodName = new SimpleStringProperty("longpolling");
    private StringProperty _sendToMethodName = new SimpleStringProperty("default");

    public String getUserName() {
        return _userNameProperty.get();
    }

    public void setUserName(String userName) {
        this._userNameProperty.set(userName);
    }

    private Stage _primaryStage;
    public void setStage(Stage stage)
    {
        _primaryStage = stage;
    }




    // Button Handlers
    @FXML
    protected void handleSetNetworkNames(ActionEvent event) throws IOException {
        Stage childStage = new Stage();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/NetworkNames.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root);
        childStage.setScene(scene);


        NetworkNamesController controller = (NetworkNamesController)loader.getController();
        controller.setStage(childStage);
        controller.setNetworkNames(_selectedNetworkNames);

        childStage.showAndWait();

        if (!controller.getWasCanceled())
            _selectedNetworkNames.setValue(FXCollections.observableArrayList( controller.getNetworkNames()));
    }

    @FXML
    protected void handleStart(ActionEvent event) throws UnsupportedEncodingException {
        if ( null == _cloud) {
            _cloud = new CywCloudInterface(_userNameProperty.getValue(), _networkPasswordProperty.getValue(), "network 1");
            _cloud.addConnectionListener(this);
            _cloud.setEnableDebugMessages(_enableDebugMessagesProperty.getValue());
            _cloud.setUseEncryption(_useEncryptionProperty.getValue());
            _cloud.startService();
        } else {
            _cloud.closeConnection();
            _cloud = null;
        }
    }

    @FXML
    protected void handleStop(ActionEvent event) {

        _cloud.closeConnection();
    }

    @FXML
    protected void handleSend(ActionEvent event) {
        CywDataToSend data = new CywDataToSend();
        try {
            String textToSend = _textToSendProperty.get();
            if (_addSendCountToStringProperty.get())
                textToSend += (_sendCountProperty.get() + 1);

            if (_addCarriageReturnProperty.get())
            {
                textToSend += "\r";
            }
            if (_addLineFeedProperty.get())
            {
                textToSend += "\n";
            }

            data.convertStringForSending(textToSend);


            if (_sendToMethodName.getValue() == "default") {
                // not implemented
            } else if (_sendToMethodName.getValue() == "sessions") {
                String sessionIds = _sendToSessionIds.get();
                if ( null != sessionIds) {
                    String[] sessionIdArray = _sendToSessionIds.getValue().split(",");
                    for (String sessionId : sessionIdArray)
                        data.toSessionIDs.add(Integer.parseInt(sessionId));
                }
            } else if (_sendToMethodName.getValue() == "networks") {
                // not implemented
                if ( null != _selectedNetworkNamesSelection.get())
                    for(String networkName : _selectedNetworkNamesSelection.get())
                        data.toNetworks.add(networkName);
            }

            data.dataType = _dataTypeProperty.getValue();

            _cloud.sendData(data);


            _sendCountProperty.setValue(_cloud.getHttpUploadRequestCount());

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    @FXML
    protected void handleSend10(ActionEvent event) {
    }

    @FXML
    protected void handleClearTextReceived(ActionEvent event) {
        _textReceivedProperty.setValue(null);
    }

    @FXML
    protected void handleClearMessage(ActionEvent event) {
        _messageProperty.setValue(null);
    }



    // Control fields

    @FXML
    public TextField userNameTextField;
    @FXML
    public TextField networkPasswordTextField;
    @FXML
    public TextField dataTypeTextField;
    @FXML
    public TextArea textToSendTextField;

    @FXML
    public Label sessionIdLabel;
    @FXML
    public Label requestCountLabel;
    @FXML
    public Label sendCountLabel;

    @FXML
    public CheckBox useEncryptionCheckBox;
    @FXML
    public CheckBox enableDebugMessagesCheckBox;
    @FXML
    public CheckBox addSendCountToStringCheckBox;


    @FXML
    public TextArea textReceivedTextField;
    @FXML
    public ListView messageListView;

    @FXML
    public CheckBox addCarriageReturnCheckBox;
    @FXML
    public CheckBox addLineFeedCheckBox;



    @FXML
    public RadioButton radioConnMethodAuto;
    @FXML
    public RadioButton radioConnMethodWebSocket;
    @FXML
    public RadioButton radioConnMethodLongPolling;

    private ToggleGroupValue toggleGroupConnMethod = new ToggleGroupValue();



    @FXML
    public RadioButton radioSendToDefaultNetworks;
    @FXML
    public RadioButton radioSendToSessionIDs;
    @FXML
    public RadioButton radioSendToSelectedNetworks;

    private ToggleGroupValue toggleGroupSendTo = new ToggleGroupValue();

    @FXML
    public TextField sendToSessionIdsTextField;
    @FXML
    public ListView selectedNetworkNamesListView;

    @FXML
    public Button buttonStart;
}
