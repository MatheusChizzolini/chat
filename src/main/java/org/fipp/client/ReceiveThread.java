package org.fipp.client;

import java.io.BufferedReader;

public class ReceiveThread implements Runnable {
    private static final String LEAVE_CONFIRMATION = "Você saiu do chat.";

    private final BufferedReader serverInput;

    public ReceiveThread(BufferedReader serverInput) {
        this.serverInput = serverInput;
    }

    @Override
    public void run() {
        try {
            String message;
            boolean isReceiving = true;

            while ((message = serverInput.readLine()) != null && isReceiving) {
                System.out.println(message);

                if (message.equalsIgnoreCase(LEAVE_CONFIRMATION)) {
                    isReceiving = false;
                } else {
                    if (shouldShowPrompt(message)) {
                        System.out.print("> ");
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Conexão com o servidor encerrada.");
        }
    }

    private boolean shouldShowPrompt(String message) {
        return message.endsWith(":")
                || message.endsWith("para encerrar.")
                || message.equals("Mensagem enviada.")
                || message.contains(": ");
    }
}
