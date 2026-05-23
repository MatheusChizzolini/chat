package org.example.client;

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
            boolean leaveMessage = false;
            while ((message = keyboardInput.readLine()) != null && !leaveMessage) {
                serverOutput.println(message);
                if (message.equalsIgnoreCase("sair")) {
                    leaveMessage = true;
                }

                System.out.print("> ");
            }
        } catch (Exception e) {
            System.out.println("Erro ao enviar mensagem: " + e.getMessage());
        }
    }
}
