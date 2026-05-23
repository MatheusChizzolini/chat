package org.example.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ChatClient {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) {
        try (
                Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                BufferedReader keyboardInput = new BufferedReader(new InputStreamReader(System.in));
                BufferedReader serverInput = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter serverOutput = new PrintWriter(socket.getOutputStream(), true)
        ) {
            System.out.println("Conectado ao servidor.");

            String serverMessage = serverInput.readLine();
            System.out.println(serverMessage);

            String message;
            boolean leaveMessage = false;
            while (!leaveMessage) {
                System.out.print("> ");
                message = keyboardInput.readLine();

                serverOutput.println(message);

                String response = serverInput.readLine();
                if (response != null) {
                    System.out.println(response);
                }

                if (message.equalsIgnoreCase("sair")) {
                    leaveMessage = true;
                }
            }

            System.out.println("Cliente encerrado.");
        } catch (Exception e) {
            System.out.println("Erro no cliente: " + e.getMessage());
        }
    }
}
