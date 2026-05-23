package org.example.client;

import java.io.BufferedReader;

public class ReceiveThread implements Runnable {
    private final BufferedReader serverInput;

    public ReceiveThread(BufferedReader serverInput) {
        this.serverInput = serverInput;
    }

    @Override
    public void run() {
        try {
            String message;

            while ((message = serverInput.readLine()) != null) {
                System.out.println("\n" + message);
                System.out.print("> ");
            }

        } catch (Exception e) {
            System.out.println("Conexão com o servidor encerrada.");
        }
    }
}
