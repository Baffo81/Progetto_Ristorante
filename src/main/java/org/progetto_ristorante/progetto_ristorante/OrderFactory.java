package org.progetto_ristorante.progetto_ristorante;

public interface OrderFactory {
    Order createOrder(String name,float price);
}