package org.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.*;

public class Server {
    public static final String DATABASE_URL = "jdbc:postgresql://localhost:5432/bank";
    public static final String DATABASE_CONFIG = "C:\\Users\\agnis\\IdeaProjects\\TestTask\\dbconfig.cfg";
    public static final int PORT = 8080;
    public static final String HASHING_ALGORITHM = "SHA-1";
    private static final Logger rootLogger = LogManager.getLogger(Server.class);


    public static void main(String[] args) {
        rootLogger.info("--------------------------------------------------------------------");
        rootLogger.info("-----------------START SERVER-------------------------------------");
        rootLogger.info("--------------------------------------------------------------------");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            rootLogger.info("Server listening on port: {}", PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new Client(clientSocket)).start();
            }
        } catch (IOException e) {
            rootLogger.fatal("Server can not be started", e);
            throw new RuntimeException();
        }
    }

}