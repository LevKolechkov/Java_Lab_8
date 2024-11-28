package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ThirdClient implements Runnable {

    private Socket ThirdClient;
    private BufferedReader in;
    private PrintWriter out;
    private boolean done;

    @Override
    public void run() {
        try {
            ThirdClient = new Socket("127.0.0.1", 9999);
            out = new PrintWriter(ThirdClient.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(ThirdClient.getInputStream()));

            InputHandler inHandler = new InputHandler();
            Thread t = new Thread(inHandler);
            t.start();

            String inMessage;
            while ((inMessage = in.readLine()) != null) {
                System.out.println(inMessage);
            }
        } catch (IOException e) {
            // ignore
        }
    }

    public void shutdown() {
        done = true;
        try {
            in.close();
            out.close();
            if (!ThirdClient.isClosed()) {
                ThirdClient.close();
            }
        } catch (IOException e) {
            // ignore
        }
    }

    class InputHandler implements Runnable {
        @Override
        public void run() {
            try {
                BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));
                System.out.println("Enter your message (/private <user> <message> for private, /quit to exit):");
                while (!done) {
                    String message = inReader.readLine();
                    if (message.equals("/quit")) {
                        out.println(message);
                        inReader.close();
                        shutdown();
                    } else {
                        out.println(message);
                    }
                }
            } catch (IOException e) {
                // ignore
            }
        }
    }

    public static void main(String[] args) {
        ThirdClient ThirdClient = new ThirdClient();
        ThirdClient.run();
    }
}
