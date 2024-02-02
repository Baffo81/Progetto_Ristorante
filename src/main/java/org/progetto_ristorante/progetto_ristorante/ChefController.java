package org.progetto_ristorante.progetto_ristorante;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.util.Callback;

import java.net.URL;
import java.sql.*;
import java.util.ResourceBundle;

public class ChefController implements Initializable {

    // FXML annotations for injecting UI elements
    @FXML
    private TextField menuOrderField,
            orderPriceField;

    @FXML
    private ListView<Order> menuArea;

    @FXML
    private Text invalidData;

    @FXML
    private VBox order,
                 menu;

    private final ChefModel chefModel = new ChefModel();
    private final OrderFactory orderFactory = new SimpleOrderFactory();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) { // shows current menu when the interface is loaded and sets the action to perform when the customer clicks on buttons
        showMenu(); // shows the menu
        menuArea.setOnMouseClicked(event -> { // adds an event manager to get the order the chef wants to remove from the menu
            Order order = menuArea.getSelectionModel().getSelectedItem(); // gets chef's clicked order

            // shows a window to get chef confirm
            Alert confirmationDialog = new Alert(Alert.AlertType.CONFIRMATION);
            confirmationDialog.setTitle("Conferma eliminazione ordine");
            confirmationDialog.setHeaderText(null);
            confirmationDialog.setGraphic(null);
            confirmationDialog.setContentText("Sei sicuro di voler eliminare " + order.getName() + " dal menu?");

            confirmationDialog.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL); // adds confirm and deny buttons
            confirmationDialog.showAndWait().ifPresent(response -> { // waits chef's response
                if (response == ButtonType.OK) { // if chef confirms, deletes the order from the menu and shows the updated menu
                    try {
                        deleteOrder(order.getName());
                        showMenu();
                    } catch (SQLException exc) {
                        throw new RuntimeException(exc);
                    }
                }
            });
        });
    }

    @FXML
    private void cook() { // hides interface's elements and starts chef thread
        hideInterface();
        chefModel.startServer();
    }

    @FXML
    private void addOrder() throws SQLException { // adds an order into the menu
        showMenu(); // shows current menu
        String order = menuOrderField.getText(); // gets the order to add to the menu from the interface
        String inputPrice = orderPriceField.getText(); // gets the order's price from the interface
        if (!order.isEmpty() && !inputPrice.isEmpty()) { // checks if the chef has entered a valid order and a valid price
            inputPrice = inputPrice.replace(',', '.'); // replaces ',' with '.'
            Order order1 = orderFactory.createOrder(order, Float.parseFloat(inputPrice));
            try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/RISTORANTE", "root", "Gaetano22")) { // connection to the database
                String selectQuery = "SELECT * FROM ORDINI WHERE NOME = ?"; // query to check if the order is already into the menu
                try (PreparedStatement selectStatement = connection.prepareStatement(selectQuery)) { // substitutes ? with order's name
                    selectStatement.setString(1, order1.getName());
                    try (ResultSet resultSet = selectStatement.executeQuery()) { // performs the query
                        if (resultSet.next()) { // if the order is already into the menu, shows an error message
                            invalidData.setText("Ordine gia presente nel menu");
                            invalidData.setVisible(true);
                        } else { // otherwise, adds the order into the menu
                            invalidData.setVisible(false);
                            String insertQuery = "INSERT INTO ORDINI (NOME, PREZZO) VALUES (?, ?)"; // query to insert the order into the menu
                            try (PreparedStatement insertStatement = connection.prepareStatement(insertQuery)) { // substitutes ? with username and password
                                insertStatement.setString(1, order1.getName());
                                insertStatement.setFloat(2, order1.getPrice());
                                insertStatement.executeUpdate(); // performs the insert
                                menuOrderField.setText(""); // clears order's field
                                orderPriceField.setText(""); // clears price's field
                            }
                        }
                    }
                }
            }
        } else {
            invalidData.setText("Ordine o prezzo mancante");
            invalidData.setVisible(true);
        }
        showMenu(); // shows updated menu
    }

    private void deleteOrder(String name) throws SQLException { // deletes an order from the menu
        try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/RISTORANTE", "root", "Gaetano22")) { // connection to the database
            String deleteQuery = "DELETE FROM ORDINI WHERE NOME = ?"; // query to delete the order from the database
            try (PreparedStatement deleteStatement = connection.prepareStatement(deleteQuery)) { // substitutes ? with order's name
                deleteStatement.setString(1, name);
                deleteStatement.executeUpdate(); // executes the query
            }
        }
    }

    @FXML
    private void showMenu() { // shows the menu in real time
        try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/RISTORANTE", "root", "Gaetano22")) { // connection to the database
            String selectQuery = "SELECT * FROM ORDINI"; // query to get each menu's order
            try (PreparedStatement preparedStatement = connection.prepareStatement(selectQuery)) { // performs the select
                ResultSet resultSet = preparedStatement.executeQuery();

                // makes the menu viewable as list of Order elements (name-price)
                ObservableList<Order> menuItems = FXCollections.observableArrayList();
                while (resultSet.next()) {
                    String name = resultSet.getString("NOME");
                    float price = resultSet.getFloat("PREZZO");
                    Order order = new Order(name, price);
                    menuItems.add(order);
                }

                // applies a border to each menu's order
                menuArea.setCellFactory(new Callback<>() {
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
                                    setText(item.getName() + " - €" + item.getPrice());
                                    setStyle("-fx-border-color: #F5DEB3");
                                }
                            }
                        };
                    }
                });
                menuArea.setItems(menuItems);
            }
        } catch (SQLException exc) {
            throw new RuntimeException(exc);
        }
    }

    @FXML
    private void hideInterface() { // hides the interface once the chef has finished to write the menu

        // shows a window to get chef confirm
        Alert confirmationDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmationDialog.setTitle("Conferma menu");
        confirmationDialog.setHeaderText(null);
        confirmationDialog.setGraphic(null);
        confirmationDialog.setContentText("Sei sicuro di voler confermare il menu?");

        confirmationDialog.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);  // adds confirm and deny buttons
        confirmationDialog.showAndWait().ifPresent(response -> { // waits chef's response
            if (response == ButtonType.OK) { // if chef confirms, confirms the menu and hides interface's elements
                order.setVisible(false);
                menu.setVisible(false);
                invalidData.setVisible(false);
            }
        });
    }
}


