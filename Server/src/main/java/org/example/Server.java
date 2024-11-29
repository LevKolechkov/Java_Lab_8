package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(Server.class);

    private ArrayList<ConnectionHandler> connections;
    private HashMap<String, ConnectionHandler> users;
    private ServerSocket server;
    private boolean done;
    private ExecutorService pool;

    private String host;
    private int port;

    public Server() {
        connections = new ArrayList<>();
        users = new HashMap<>();
        done = false;

        loadConfig();
    }

    private void loadConfig() {
        Properties properties = new Properties();
        try (var input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                throw new IOException("Configuration file 'application.properties' not found in resources.");
            }
            properties.load(input);

            host = properties.getProperty("server.host", "127.0.0.1");
            port = Integer.parseInt(properties.getProperty("server.port", "9999"));

            logger.info("Configuration loaded: host={}, port={}", host, port);
        } catch (IOException e) {
            logger.error("Failed to load configuration: ", e);
            System.exit(1);
        }
    }

    @Override
    public void run() {
        try {
            server = new ServerSocket(port);
            logger.info("Server has been started. Host: {}, Port: {}", host, port);
            pool = Executors.newCachedThreadPool();
            while (!done) {
                Socket client = server.accept();
                ConnectionHandler handler = new ConnectionHandler(client);
                connections.add(handler);
                pool.execute(handler);
            }
        } catch (IOException e) {
            logger.error("Error starting server: ", e);
            shutdown();
        }
    }

    public void broadcast(String message) {
        logger.info("Broadcast message: {}", message);
        for (ConnectionHandler ch : connections) {
            if (ch != null) {
                ch.sendMessage(message);
            }
        }
    }

    public void privateMessage(String recipient, String message, String sender) {
        ConnectionHandler ch = users.get(recipient);
        if (ch != null) {
            logger.info("Private message from {} to {}: {}", sender, recipient, message);
            ch.sendMessage("[Private] " + sender + ": " + message);
        } else {
            logger.warn("User {} not found. Sender: {}", recipient, sender);
            ConnectionHandler senderHandler = users.get(sender);
            if (senderHandler != null) {
                senderHandler.sendMessage("User " + recipient + " not found.");
            }
        }
    }

    public void shutdown() {
        try {
            done = true;
            pool.shutdown();
            if (!server.isClosed()) {
                server.close();
            }
            for (ConnectionHandler ch : connections) {
                ch.shutdown();
            }
            logger.info("Server has been stopped.");
        } catch (Exception e) {
            logger.error("Error shutting down the server: ", e);
        }
    }

    class ConnectionHandler implements Runnable {

        private Socket client;
        private BufferedReader in;
        private PrintWriter out;
        private String nickname;

        public ConnectionHandler(Socket client) {
            this.client = client;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(client.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                out.println("Please enter a nickname:");
                nickname = in.readLine();
                synchronized (users) {
                    users.put(nickname, this);
                }
                logger.info("User {} connected.", nickname);
                broadcast(nickname + " joined the chat!");
                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("/nick ")) {
                        String[] messageSplit = message.split(" ", 2);
                        if (messageSplit.length == 2) {
                            String oldNick = nickname;
                            synchronized (users) {
                                users.remove(nickname);
                                nickname = messageSplit[1];
                                users.put(nickname, this);
                            }
                            logger.info("User {} renamed to {}", oldNick, nickname);
                            broadcast(oldNick + " renamed themselves to " + nickname);
                        } else {
                            out.println("No nickname provided");
                        }
                    } else if (message.startsWith("/quit")) {
                        broadcast(nickname + " left the chat");
                        shutdown();
                    } else if (message.startsWith("/private ")) {
                        String[] splitMessage = message.split(" ", 3);
                        if (splitMessage.length == 3) {
                            String recipient = splitMessage[1];
                            privateMessage(recipient, splitMessage[2], nickname);
                        } else {
                            out.println("Usage: /private <recipient> <message>");
                        }
                    } else {
                        broadcast(nickname + ": " + message);
                    }
                }
            } catch (IOException e) {
                logger.error("Error working with client: ", e);
                shutdown();
            }
        }

        public void shutdown() {
            try {
                in.close();
                out.close();
                if (!client.isClosed()) {
                    client.close();
                }
                synchronized (users) {
                    users.remove(nickname);
                }
                logger.info("User {} disconnected.", nickname);
            } catch (IOException e) {
                logger.error("Error shutting down client: ", e);
            }
        }

        public void sendMessage(String message) {
            out.println(message);
        }
    }

    public static void main(String[] args) {
        Server server = new Server();
        server.run();
    }
}
