package org.fipp.client;

import java.io.BufferedReader;
import java.util.Set;
import java.util.regex.Pattern;

public class ReceiveThread implements Runnable {
    private static final String LEAVE_CONFIRMATION = "Voce saiu do chat.";
    private static final Set<String> INPUT_PROMPTS = Set.of(
            "Digite sua opcao:",
            "Digite uma mensagem:",
            "Digite um comando:",
            "Digite sim ou nao:",
            "Digite (sim/nao):",
            "Aceitar? (sim/nao):",
            "Aceitar convite do grupo? (sim/nao):",
            "Aceitar entrada no grupo? (sim/nao):",
            "Login:",
            "Senha:",
            "Nome completo:",
            "Email:",
            "Email cadastrado:"
    );
    private static final Pattern CHAT_MESSAGE = Pattern.compile("^.* \\(\\d{2}:\\d{2}\\): .+$");

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
        return INPUT_PROMPTS.contains(message) || CHAT_MESSAGE.matcher(message).matches();
    }
}
