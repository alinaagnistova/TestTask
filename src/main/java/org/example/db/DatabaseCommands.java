package org.example.db;

public class DatabaseCommands {
    public static final String tableCreation = """
                 CREATE TABLE IF NOT EXISTS users(
                 id SERIAL PRIMARY KEY,
                 login TEXT NOT NULL,
                 password TEXT NOT NULL,
                 salt TEXT
                 );
                 CREATE TABLE IF NOT EXISTS bank_account(
                 id SERIAL PRIMARY KEY,
                 owner_login TEXT NOT NULL UNIQUE,
                 balance NUMERIC 
                 )
            """;

    public static final String addUser = """
            INSERT INTO users(login, password, salt) VALUES (?, ?, ?);""";

    public static final String addBankAccount = """
            INSERT INTO bank_account(owner_login, balance) VALUES (?, 0);""";

    public static final String getUser = """
            SELECT * FROM users WHERE (login = ?);""";

    public static final String getBalance = """
            SELECT balance FROM bank_account WHERE owner_login = ?;""";

    public static final String updateBalance = """
            UPDATE bank_account SET balance = balance + ? WHERE owner_login = ?;
            """;
}
