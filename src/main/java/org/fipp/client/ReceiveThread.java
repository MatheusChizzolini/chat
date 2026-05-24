package org.fipp.client;

import java.io.BufferedReader;

public class ReceiveThread implements Runnable {
    private static final String LEAVE_CONFIRMATION = "Voce saiu do chat.";

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
                } else if (shouldShowPrompt(message)) {
                    System.out.print("> ");
                }
            }
        } catch (Exception e) {
            System.out.println("Conexao com o servidor encerrada.");
        }
    }

    private boolean shouldShowPrompt(String message) {
        return message.equals("Digite sua opcao:")
                || message.equals("Digite uma mensagem:")
                || message.equals("Digite um comando:")
                || message.equals("Digite sim ou nao:")
                || message.equals("Digite (sim/nao):")
                || message.equals("Aceitar? (sim/nao):")
                || message.equals("Login:")
                || message.equals("Senha:")
                || message.equals("Nome completo:")
                || message.equals("Email:")
                || message.equals("Email cadastrado:")
                || message.contains(": ");
    }
}
