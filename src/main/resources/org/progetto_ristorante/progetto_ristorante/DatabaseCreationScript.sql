/* Script to create the database */

CREATE DATABASE IF NOT EXISTS RISTORANTE;

USE RISTORANTE;

CREATE TABLE IF NOT EXISTS UTENTI (
    USERNAME VARCHAR(30)  PRIMARY KEY,
    PASSWORD VARCHAR(256) NOT NULL,
    EMAIL    VARCHAR(50) UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS ORDINI (
    NOME   VARCHAR(50)    PRIMARY KEY,
    PREZZO DECIMAL(10, 2) NOT NULL
)