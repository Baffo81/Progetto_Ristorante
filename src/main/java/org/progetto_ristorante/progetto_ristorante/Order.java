package org.progetto_ristorante.progetto_ristorante;

/**
 * @param name  order's name
 * @param price order's price
 */
public record Order(String name, float price) {
    public String toString() { // returns the order as a string (override of the method toString)
        return String.format("%-30s%10s", name, STR."€\{price}");
    }
}
