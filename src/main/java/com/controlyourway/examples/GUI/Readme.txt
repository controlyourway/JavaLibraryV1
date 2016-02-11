Program.java

    This is the main entrypoint file for the GUI application.


MainController.java

    This is the controller class for the Main dialog. It has the most code in it...
    It basically defines all the business logic of the application.

MainView.fxml

    This is the graphical layout for the main window.
    The contents of this file describe controls, positions etc. It also states
    1. What Java class is the controller class - the runtime will construct this controller for us.
    2. The Fields in the controller which are linked to controls in the View. This is how we can manipulate Controls at runtime.


NetworkNamesController.java

    This is the controller class for the Network Names dialog.


NetworkNamesView.fxml

    This is the graphical layout for the Network Names dialog.