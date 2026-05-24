package org.fipp.client;

import java.io.BufferedReader;
import java.io.PrintWriter;

public class SendThread implements Runnable {
    private final BufferedReader keyboardInput;
    private final PrintWriter serverOutput;

    public SendThread(BufferedReader keyboardInput, PrintWriter serverOutput) {
        this.keyboardInput = keyboardInput;
        this.serverOutput = serverOutput;
    }

    @Override
    public void run() {
        try {
            String message;
            boolean isSending = true;
            while ((message = keyboardInput.readLine()) != null && isSending) {
                serverOutput.println(message);
                if (message.equalsIgnoreCase("sair")) {
                    isSending = false;
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao enviar mensagem: " + e.getMessage());
        }
    }
}
