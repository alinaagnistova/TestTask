package org.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.db.DatabaseHandler;
import org.example.utils.JWTUtil;
import org.example.utils.User;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class Client implements Runnable {
    private String jwt;
    private Socket socket;
    private static final Logger clientLogger = LogManager.getLogger(Client.class);


    public Client(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
            String line = reader.readLine();
            if (line == null) {
                sendHttpResponse(writer, 400, "Bad Request", "Empty request line", null);
                return;
            }
            StringTokenizer tokenizer = new StringTokenizer(line);
            String method = tokenizer.nextToken();
            String path = tokenizer.nextToken();
            Map<String, String> headers = parseHeaders(reader);
            String contentLength = headers.get("Content-Length");
            if (contentLength == null || Integer.parseInt(contentLength) == 0) {
                sendHttpResponse(writer, 400, "Bad Request", "No Content-Length or Content-Length equals 0", null);
                return;
            }
            int length = Integer.parseInt(contentLength);
            JSONObject params = readBody(reader, length);
            jwt = extractTokenFromHeaders(headers);
            switch (method) {
                case "POST":
                    postRequest(path, writer, params, headers);
                    break;
                case "GET":
                    getRequest(path, writer, headers);
                    break;
                default:
                    sendHttpResponse(writer, 405, "Method Not Allowed", "Allowed methods: GET, POST", null);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void postRequest(String path, PrintWriter writer, JSONObject jsonBody, Map<String, String> headers) throws IOException {
        switch (path) {
            case "/signup":
                String login = jsonBody.getString("login");
                String password = jsonBody.getString("password");
                try {
                    DatabaseHandler.getDatabaseManager().addUser(new User(login, password));
                    jwt = JWTUtil.generateToken(login);
                    sendHttpResponse(writer, 200, "OK", "User registered " + login, jwt);
                    clientLogger.info("User {} registered", login);
                } catch (SQLException e) {
                    sendHttpResponse(writer, 500, "Internal Server Error", "Failed to register user. Probably, user already exists", null);
                }
                break;
            case "/signin":
                login = jsonBody.getString("login");
                password = jsonBody.getString("password");
                if (DatabaseHandler.getDatabaseManager().confirmUser(new User(login, password))) {
                    jwt = JWTUtil.generateToken(login);
                    sendHttpResponse(writer, 200, "OK", "Welcome, " + login, jwt);
                    clientLogger.info("User {} signed in", login);
                } else {
                    sendHttpResponse(writer, 401, "Unauthorized", "Incorrect username or password", null);
                }
                break;
            case "/money":
                String recipient = jsonBody.getString("to");
                double amount = jsonBody.getDouble("amount");
                String sender = JWTUtil.getUsernameFromToken(extractTokenFromHeaders(headers));
                if (jwt == null || jwt.isEmpty()) {
                    sendHttpResponse(writer, 401, "Unauthorized", "No JWT token provided", null);
                    return;
                } else if (sender == null) {
                    sendHttpResponse(writer, 401, "Unauthorized", "Invalid JWT token provided", null);
                    return;
                } else {
                    int res = DatabaseHandler.getDatabaseManager().updateBalance(recipient, amount);
                    if (res != -1) {
                        sendHttpResponse(writer, 200, "OK", "You send " + amount + " to " + recipient, jwt);
                        clientLogger.info("User {} was sent {} to the user {}", sender, amount, recipient);
                    } else {
                        sendHttpResponse(writer, 404, "Not Found", "Problems during money transfer", null);
                    }
                }
                break;
            default:
                sendHttpResponse(writer, 404, "Not Found", "Invalid path", null);
        }
    }

    private void getRequest(String path, PrintWriter writer, Map<String, String> headers) {
        String login = JWTUtil.getUsernameFromToken(extractTokenFromHeaders(headers));
        if (path.equals("/money")) {
            if (jwt == null || jwt.isEmpty()) {
                sendHttpResponse(writer, 401, "Unauthorized", "No JWT token provided", null);
                return;
            } else if (login == null) {
                sendHttpResponse(writer, 401, "Unauthorized", "Invalid JWT token provided", null);
                return;
            }
            double money = DatabaseHandler.getDatabaseManager().getBalance(login);
            if (money == -1) {
                sendHttpResponse(writer, 500, "Internal Server Error", "Problem during getting balance", null);
                return;
            }
            sendHttpResponse(writer, 200, "OK", String.valueOf(money), jwt);
            clientLogger.info("User {} balance {}", login, money);
        } else {
            sendHttpResponse(writer, 404, "Not Found", "Invalid path", null);
        }
    }


    private void sendHttpResponse(PrintWriter writer, int statusCode, String statusText, String body, String jwtToken) {
        writer.println("HTTP/1.1 " + statusCode + " " + statusText);
        writer.println("Content-Type: text/plain; charset=utf-8");
        if (jwtToken != null && !jwtToken.isEmpty()) {
            writer.println("Authorization: Bearer " + jwtToken);
        }
        writer.println();
        writer.println(body);
    }

    private HashMap<String, String> parseHeaders(BufferedReader reader) throws IOException {
        HashMap<String, String> headersMap = new HashMap<>();
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            int colonIndex = line.indexOf(":");
            if (colonIndex != -1) {
                String headerName = line.substring(0, colonIndex).trim();
                String headerValue = line.substring(colonIndex + 1).trim();
                headersMap.put(headerName, headerValue);
            }
        }
        return headersMap;
    }

    private JSONObject readBody(BufferedReader reader, int length) throws IOException {
        char[] bodyChars = new char[length];
        reader.read(bodyChars);
        String body = new String(bodyChars);
        return new JSONObject(body);
    }


    private String extractTokenFromHeaders(Map<String, String> headers) {
        String authorizationHeader = headers.get("Authorization");
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }
        return null;
    }

}
