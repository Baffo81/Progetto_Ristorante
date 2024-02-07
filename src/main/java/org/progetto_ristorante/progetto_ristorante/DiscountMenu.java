package org.progetto_ristorante.progetto_ristorante;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.*;

public class DiscountMenu implements MenuState {
    protected ObservableList<Order> menu; // menu

    public void changeMenuState(MenuContext menuContext) { // changes menu's state from full price version to discounted
        try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/RISTORANTE", "root", "Gaetano22")) { // connection to the database
            String selectQuery = "SELECT * FROM ORDINI"; // query to get each menu's orders
            try (PreparedStatement preparedStatement = connection.prepareStatement(selectQuery)) { // performs the query
                ResultSet resultSet = preparedStatement.executeQuery();
                ObservableList<Order> menu = FXCollections.observableArrayList();// makes the menu viewable
                while (resultSet.next()) { // gets each order's name and price
                    String name = resultSet.getString("NOME");
                    float price = resultSet.getFloat("PREZZO");
                    Order order = new Order(name, price - price * 0.1f); // calls the constructor to build an Order object
                    menu.add(order); // adds the order to the menu
                }
                this.menu = menu; // updates the menu
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public ObservableList<Order> getMenu () { // returns the menu
        return menu;
    }
}