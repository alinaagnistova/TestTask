package org.example.db;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.Server;
import org.example.utils.User;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.Properties;
import java.util.Random;

public class DatabaseManager {
    private Connection connection;
    private MessageDigest md;
    private static final String PEPPER = "[g$J*(l;";
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrs" +
            "tuvwxyz0123456789<>?:@{!$%^&*()_+£$";
    private static final Logger rootLogger = LogManager.getLogger(DatabaseManager.class);


    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            rootLogger.error("Could not load org.postgresql.Driver", e);
            throw new RuntimeException();
        }
    }

    public DatabaseManager() {
        try {
            md = MessageDigest.getInstance(Server.HASHING_ALGORITHM);
            this.connection = connect();
            this.createDatabase();
        } catch (SQLException e) {
            rootLogger.error("Could not create database", e);
        } catch (NoSuchAlgorithmException e) {
            rootLogger.error("Could not use hashing algorithm", e);
        }
    }

    public static Connection connect() throws SQLException {
        Properties props = new Properties();
        try {
            props.load(new FileInputStream(Server.DATABASE_CONFIG));
        } catch (IOException e) {
            rootLogger.error("Could not load database configuration", e);
            throw new RuntimeException();
        }
        return DriverManager.getConnection(Server.DATABASE_URL, props);
    }


    public void createDatabase() {
        try {
            connection.prepareStatement(DatabaseCommands.tableCreation).execute();
        } catch (SQLException e) {
            rootLogger.error("Could not create database", e);
            throw new RuntimeException();
        }
        rootLogger.info("Database created");
    }

    public void addUser(User user) throws SQLException {
        String login = user.name();
        String salt = generateRandomString();
        String pass = PEPPER + user.password() + salt;
        PreparedStatement ps = connection.prepareStatement(DatabaseCommands.addUser);
        if (checkExistUser(login)) {
            rootLogger.warn("User {} already exists", login);
            throw new SQLException();
        }
        ps.setString(1, login);
        ps.setString(2, getSHA1Hash(pass));
        ps.setString(3, salt);
        ps.execute();
        createBankAccount(login);
        rootLogger.info("User " + login + " added");
    }

    public void createBankAccount(String owner) {
        PreparedStatement ps = null;
        try {
            ps = connection.prepareStatement(DatabaseCommands.addBankAccount);
            ps.setString(1, owner);
            ps.execute();
            rootLogger.info("Bank account created {}", owner);
        } catch (SQLException e) {
            rootLogger.error("Could not create bank account", e);
            throw new RuntimeException();
        }
    }

    public boolean checkExistUser(String login) throws SQLException {
        PreparedStatement ps = null;
        ResultSet resultSet = null;
        try {
            ps = connection.prepareStatement(DatabaseCommands.getUser);
            ps.setString(1, login);
            resultSet = ps.executeQuery();
            return resultSet.next();
        } catch (SQLException e) {
            rootLogger.error("Could not check existing user", e);
            throw new RuntimeException();
        } finally {
            if (resultSet != null) {
                resultSet.close();
            }
            if (ps != null) {
                ps.close();
            }
        }
    }

    public boolean confirmUser(User inputUser) {
        try {
            String login = inputUser.name();
            PreparedStatement getUser = connection.prepareStatement(DatabaseCommands.getUser);
            getUser.setString(1, login);
            ResultSet resultSet = getUser.executeQuery();
            if (resultSet.next()) {
                String salt = resultSet.getString("salt");
                String toCheckPass = this.getSHA1Hash(PEPPER + inputUser.password() + salt);
                return toCheckPass.equals(resultSet.getString("password"));
            } else {
                return false;
            }
        } catch (SQLException e) {
            rootLogger.error("Could not confirm user", e);
            return false;
        }
    }

    public Double getBalance(String login) {
        PreparedStatement ps = null;
        try {
            ps = connection.prepareStatement(DatabaseCommands.getBalance);
            ps.setString(1, login);
            ResultSet resultSet = ps.executeQuery();
            if (resultSet.next()) {
                return resultSet.getDouble("balance");
            }
            rootLogger.warn("Could not get balance");
        } catch (SQLException e) {
            rootLogger.error("Database problems", e);
            throw new RuntimeException();
        }
        return -1D;
    }

    public int updateBalance(String login, Double balance) {
        PreparedStatement ps = null;
        try {
            ps = connection.prepareStatement(DatabaseCommands.updateBalance);
            ps.setDouble(1, balance);
            ps.setString(2, login);
            int res = ps.executeUpdate();
            rootLogger.info("Added user" + login + "balance by" + balance);
            return res;
        } catch (SQLException e) {
            rootLogger.error("Database problems", e);
            throw new RuntimeException();
        }
    }

    private String generateRandomString() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    private String getSHA1Hash(String input) {
        byte[] inputBytes = input.getBytes();
        md.update(inputBytes);
        byte[] hashBytes = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

}
