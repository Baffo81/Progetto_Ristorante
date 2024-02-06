package org.progetto_ristorante.progetto_ristorante;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.Duration;

import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.concurrent.*;

public class CustomerController implements MenuObserver {
    
    @FXML
    private Text billText,
            tableNumber,
            waitingMessage,
            loginError,
            registerError,
            unavailableReceptionist,
            menuUpdateMessage,
            unavailableWaiter;

    @FXML
    private ListView<String> totalOrderedArea;

    @FXML
    private ListView<Order> menu;

    @FXML
    private TextField loginUsername,
            registerUsername,
            requiredSeatsField;

    @FXML
    private PasswordField loginPassword,
            registerPassword,
            confirmPassword;

    @FXML
    private Button stopButton;

    @FXML
    private VBox seatsBox,
            waitingBox;

    private final CustomerModel model;
    private BufferedReader getWaitingTime; // used to get how much time has the customer to wait if there aren't available seats
    private int waitingTime;               // time the customer has to wait if there aren't available seats
    private float bill = 0.0f;             // customer's total bill
    private int table;                     // customer's table's number
    private static MenuObserverManager menuObserverManager;
    protected MenuContext menuContext = new MenuContext();

    public CustomerController() { // constructor
        model = CustomerModel.getInstance();
    }

    public static void setMenuObserverManager(MenuObserverManager manager) {
        CustomerController.menuObserverManager = manager;
    }

    public void updateMenu(boolean isMenuUpdated) {
        System.out.println("Setto il messaggio");
        if (isMenuUpdated) {
            menuUpdateMessage.setText("Menu non del giorno.");
        } else {
            menuUpdateMessage.setText("Menu del giorno.");
        }
        menuUpdateMessage.setVisible(true);
    }

    public void notifyMenuNotUpdate() {
        // Aggiorna l'interfaccia del cliente per notificare che il menu non è stato aggiornato
        System.out.println("Setto il messaggio menu non modificato");
        menuUpdateMessage.setText("Menu non del giorno.");
        menuUpdateMessage.setVisible(true);
    }

    @FXML
    private void login() throws SQLException, NoSuchAlgorithmException, IOException { // manages customer's login
        String username = loginUsername.getText(); // gets username from the interface
        String password = loginPassword.getText(); // gets password from the interface
        if (username.isEmpty() || password.isEmpty()) { // checks if the customer has entered null values
            loginError.setText("Credenziali incomplete");
            loginError.setVisible(true);
        } else if (model.loginUser(username, password)) { // if the login works, sends the customer to next interface
            menuObserverManager.addObserver(this);
            showSeatsInterface();
        } else { // if the login doesn't work, shows an error message
            loginError.setText("Credenziali errate, riprovare");
            loginError.setVisible(true);
        }
    }

    @FXML
    private void register() throws SQLException, IOException, NoSuchAlgorithmException { // manages customer's registration
        String username = registerUsername.getText(); // gets username from the interface
        String password = registerPassword.getText(); // gets password from the interface
        String confirmedPassword = confirmPassword.getText(); // gets confirmed password from the interface
        if (username.isEmpty() || password.isEmpty() || confirmedPassword.isEmpty()) { // checks if the customer has entered null values
            registerError.setText("Credenziali incomplete");
            registerError.setVisible(true);
        } else if (!validPassword(password)) { // checks if the password doesn't respect the standard
            registerError.setText("La password deve contenere almeno 8 caratteri, una lettera maiuscola, un carattere speciale e un numero");
            registerError.setVisible(true);
        } else if (!confirmedPassword.equals(password)) { // checks if the password isn't correctly confirmed
            registerError.setText("Conferma password errata");
            registerError.setVisible(true);
        } else if (model.registerUser(username, password)) { // if the register works, sends the user to the login
            showLoginInterface();
        } else { // checks if the username is available
            registerError.setText("Username non disponibile");
            registerError.setVisible(true);
        }
    }

    private boolean validPassword(String password) { // checks if the password respects the standard
        String regex = "^(?=.*[0-9])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\",.<>?])(?=.*[a-z])(?=.*[A-Z]).{8,}$";
        return password.matches(regex);
    }

    @FXML
    private void getRequiredSeats() { // manages the request of seats by a customer
        final int RECEPTIONIST_PORT = 1313; // port to communicate with the receptionist
        try (SocketHandler receptionSocket = new SocketProxy(new Socket(InetAddress.getLocalHost(), RECEPTIONIST_PORT))){ // creates a socket to communicate with the receptionist
            unavailableReceptionist.setVisible(false);
            table = getTable(receptionSocket); // says how many seats he needs to the receptionist and gets a table
            if (table > 0) { // if there are available seats, the customer takes them
                showOrderInterface(); // shows second interface's elements
            } else { // otherwise, opens a second socket
                try (SocketHandler receptionSocket2 = new SocketProxy(new Socket(InetAddress.getLocalHost(), RECEPTIONIST_PORT))) {
                    seatsBox.setVisible(false); // hides the interface's element to get required seats
                    waitingBox.setVisible(true); // shows the interface's element to get customer's answer about waiting or not
                    getWaitingTime = receptionSocket2.getReader(); // used to read the time to wait communicated by the receptionist
                    waitingTime = Integer.parseInt(getWaitingTime.readLine()); // read the time to wait from the socket and parses it to integer
                    waitingMessage.setText("Non ci sono abbastanza posti disponibili, vuoi attendere " + waitingTime + " minuti ?"); // shows the waiting time message
                    waitingMessage.setVisible(true);
                }
            }
        } catch (IOException exc) { // if receptionist is unreachable, shows an error message
            unavailableReceptionist.setText("Receptionist non disponibile al momento");
            unavailableReceptionist.setVisible(true);
        }
    }

    @FXML
    private void waitButton() throws IOException { // manages the action when the customer clicks the "Wait" button
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1); // creates a scheduler to plan the periodic execution of tasks
        ScheduledFuture<?> waitTask = scheduler.schedule(this::onWaitComplete, waitingTime, TimeUnit.SECONDS); // plans which task execute after a waiting time, and specifies the time unit
        try { // waits for the task to complete (the estimated wait time)
            waitTask.get();
        } catch (InterruptedException | ExecutionException exc) {
            throw new RuntimeException(exc);
        } finally { // deallocates used resources
            scheduler.shutdown();
            getWaitingTime.close();
        }
    }

    private void onWaitComplete() { // manages the completion of the waiting time
        Platform.runLater(() -> {
            Timeline timeline = new Timeline( // after a certain period of time, hides the waiting components and sends the customer to the interface to take orders
                new KeyFrame(Duration.seconds(1), event -> {
                    try { showOrderInterface(); // shows the interface to get orders
                    } catch (IOException exc) {
                        throw new RuntimeException(exc);
                    }
                })
            );
            timeline.play();
        });
    }

    @FXML
    private void leave() { // closes the interface when the customer clicks the "Leave" button
        Stage stage = (Stage) requiredSeatsField.getScene().getWindow();
        stage.close();
    }

    @FXML
    private int getTable(SocketHandler receptionSocket) throws IOException { // allows the customer to specify how many seats they need and to get a table if available
        BufferedReader checkSeats = receptionSocket.getReader(); // used to gets a table from the receptionist
        PrintWriter sendSeats = receptionSocket.getWriter(); // used to say to the receptionist how much seats does him require
        String input = requiredSeatsField.getText(); // gets customer's required seats from the interface
        if (!input.matches("\\d+")) { // checks if the user's entered a number
            unavailableReceptionist.setText("Numero di posti non valido");
            unavailableReceptionist.setVisible(true);
        }
        requiredSeatsField.setText(""); // clears previous input
        int requiredSeats = Integer.parseInt(input); // parses to Integer
        sendSeats.println(requiredSeats); // says how many seats he requires to the receptionist
        int tableNumber = Integer.parseInt(checkSeats.readLine());  // gets the table number from the receptionist if it's possible
        checkSeats.close(); // closes used resources and connection
        sendSeats.close();
        receptionSocket.close();
        return tableNumber;
    }

    @FXML
    private void showMenu() { // shows the menu in real time
        LocalDate today = LocalDate.now(); // local's date
        DayOfWeek day = today.getDayOfWeek(); // local's day
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) { // shows discounted menu during the weekend
            menuContext.setMenuState(new DiscountMenu());
        } else { // shows full price menu during the week
            menuContext.setMenuState(new NotDiscountMenu());
        }
        menu.setCellFactory(new Callback<>() { // applies a border to each menu's order
            @Override
            public ListCell<Order> call(ListView<Order> param) {
                return new ListCell<>() {
                    @Override
                    protected void updateItem(Order item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                            setStyle(null);
                        } else {
                            HBox hbox = new HBox();
                            Label nameLabel = new Label(item.name());
                            Label priceLabel = new Label("€" + String.format("%.2f", item.price()));
                            Region spacer = new Region();
                            HBox.setHgrow(spacer, Priority.ALWAYS);
                            hbox.getChildren().addAll(nameLabel, spacer, priceLabel);
                            setText(null);
                            setGraphic(hbox);
                            setStyle("-fx-border-color: #F5DEB3; -fx-padding: 5px;");
                        }
                    }
                };
            }
        });
        menu.setItems(menuContext.getMenuState().getMenu()); // makes the menu viewable as a list of Order elements (name-price)
    }

    @FXML
    private void getOrder(String order, float price) { // allows a customer to get an order
        final int WAITER_PORT = 1316;  // used to communicate with the waiter
        try (SocketHandler waiterSocket = new SocketProxy(new Socket(InetAddress.getLocalHost(), WAITER_PORT))) { // creates a socket to communicate with the waiter
            unavailableWaiter.setVisible(false);
            BufferedReader eatOrder = waiterSocket.getReader(); // used to get the order from the receptionist
            PrintWriter takeOrder = waiterSocket.getWriter(); // used to send an order to the receptionist
            StringBuilder totalOrdered = new StringBuilder(); // contains each customer's order
            takeOrder.println(order); // sends the order to the waiter
            order = eatOrder.readLine(); // waits for the order and eats it
            totalOrdered.append(order).append("\n"); // adds the order to the customer's list and its price to the bill
            totalOrderedArea.setCellFactory(new Callback<>() { // applies a border to each customer's order
                @Override
                public ListCell<String> call(ListView<String> param) {
                    return new ListCell<>() {
                        @Override
                        protected void updateItem(String item, boolean empty) {
                            super.updateItem(item, empty);
                            if (empty || item == null) {
                                setText(null);
                                setStyle(null);
                            } else {
                                setText(item);
                                setStyle("-fx-border-color: #D2B48C; -fx-border-width: 1;");
                            }
                        }
                    };
                }
            });
            totalOrderedArea.getItems().add(totalOrdered.toString()); // shows orders and total bill
            bill += price; // updates customer's bill
            billText.setText("€" + String.format("%.2f", bill));
        } catch (IOException exc) { // if waiter is unreachable
            unavailableWaiter.setText("Nessun cameriere disponibile al momento");
            unavailableWaiter.setVisible(true);
        }
    }

    @FXML
    private void showLoginInterface() throws IOException { // switches the interface to the login
        FXMLLoader loader = new FXMLLoader(getClass().getResource("LoginInterface.fxml"));
        Parent parent = loader.load();
        Scene scene = new Scene(parent);
        Stage stage = (Stage) registerUsername.getScene().getWindow();
        stage.setScene(scene);
        stage.setMaximized(true); // sets fullscreen
        stage.show(); // shows the interface
    }

    @FXML
    private void showRegisterInterface() throws IOException { // switches the interface to the registration
        FXMLLoader loader = new FXMLLoader(getClass().getResource("RegisterInterface.fxml"));
        Parent parent = loader.load();
        Scene scene = new Scene(parent);
        Stage stage = (Stage) loginUsername.getScene().getWindow();
        stage.setScene(scene);
        stage.setMaximized(true); // sets fullscreen
        stage.show(); // shows the interface
    }

    @FXML
    private void showSeatsInterface() throws IOException { // switches the interface to require seats
        FXMLLoader loader = new FXMLLoader(getClass().getResource("GetSeatsInterface.fxml"));
        Parent parent = loader.load();
        Scene scene = new Scene(parent);
        Stage stage = (Stage) loginUsername.getScene().getWindow();
        stage.setScene(scene);
        stage.setMaximized(true); // sets fullscreen
        stage.show(); // shows the interface
    }

    private void showOrderInterface() throws IOException { // switches the interface to get orders
        FXMLLoader loader = new FXMLLoader(getClass().getResource("GetOrderInterface.fxml"));
        Parent parent = loader.load();
        Scene scene = new Scene(parent);
        Stage stage = (Stage) seatsBox.getScene().getWindow();
        stage.setScene(scene);
        stage.setMaximized(true);
        menu = (ListView<Order>) scene.lookup("#menu"); // initializes interface's elements
        totalOrderedArea = (ListView<String>) scene.lookup("#totalOrderedArea");
        tableNumber = (Text) scene.lookup("#tableNumber");
        tableNumber.setText(String.valueOf(table));
        billText = (Text) scene.lookup("#billText");
        billText.setText("€0");
        unavailableWaiter = (Text) scene.lookup("#unavailableWaiter");
        menuUpdateMessage = (Text) scene.lookup("#menuUpdateMessage");
        showMenu(); // shows the menu
        menu.setOnMouseClicked(event -> { // adds an event manager to get customer's order by clicking onto the menu
            Order order = menu.getSelectionModel().getSelectedItem(); // gets customer's clicked order
            Alert confirmationDialog = new Alert(Alert.AlertType.CONFIRMATION); // shows a window to get customers confirm
            confirmationDialog.setTitle("Conferma ordine");
            confirmationDialog.setHeaderText(null);
            confirmationDialog.setGraphic(null);
            confirmationDialog.setContentText("Sei sicuro di voler ordinare " + order.name() + " ?");
            confirmationDialog.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL); // adds confirm and deny buttons
            confirmationDialog.showAndWait().ifPresent(response -> { // waits for customer's response
                if (response == ButtonType.OK) { // if the customer confirms, sends the order to the waiter
                    getOrder(order.name(), order.price());
                }
            });
        });
        stage.show();
    }

    @FXML
    private void askBill() { // method to close customers' interface once they've done
        Alert confirmationDialog = new Alert(Alert.AlertType.CONFIRMATION); // shows a window to get customers confirm
        confirmationDialog.setTitle("Richiesta conto");
        confirmationDialog.setGraphic(null);
        confirmationDialog.setHeaderText(null);
        confirmationDialog.setContentText("Sei sicuro di voler chiedere il conto?");
        confirmationDialog.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL); // adds confirm and deny buttons
        confirmationDialog.showAndWait().ifPresent(response -> { // waits for customer's response
            if (response == ButtonType.OK) { // if customer confirms, closes the interface
                Stage stage = (Stage) stopButton.getScene().getWindow();
                menuObserverManager.removeObserver(this);
                stage.close();
            }
        });
    }
}