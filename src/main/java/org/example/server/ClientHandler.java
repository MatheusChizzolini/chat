package org.example.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final String clientName;
    private PrintWriter output;

    public ClientHandler(Socket clientSocket, String clientName) {
        this.clientSocket = clientSocket;
        this.clientName = clientName;
    }

    public String getClientName() {
        return clientName;
    }

    @Override
    public void run() {
        try {
            BufferedReader input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            output = new PrintWriter(clientSocket.getOutputStream(), true);

            output.println("Conectado ao servidor como " + clientName + ".");
            output.println("Digite suas mensagens ou 'sair' para encerrar.");

            ChatServer.broadcast(clientName + " entrou no chat.", this);

            String message;
            boolean leaveMessage = false;
            while ((message = input.readLine()) != null && !leaveMessage) {
                if (message.equalsIgnoreCase("sair")) {
                    output.println("Você saiu do chat.");
                    leaveMessage = true;
                }

                System.out.println(clientName + " enviou: " + message);
                String formattedMessage = clientName + ": " + message;
                ChatServer.broadcast(formattedMessage, this);

                output.println("Mensagem enviada.");
            }
        } catch (Exception e) {
            System.out.println("Erro ao manipular " + clientName + ": " + e.getMessage());
        } finally {
            ChatServer.removeClient(this);
            ChatServer.broadcast(clientName + " saiu do chat.", this);
            closeClientSocket();
        }
    }

    public void sendMessage(String message) {
        if (output != null) {
            output.println(message);
        }
    }

    private void closeClientSocket() {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }

            System.out.println("Cliente desconectado.");
        } catch (Exception e) {
            System.out.println("Erro ao fechar conexão com cliente: " + e.getMessage());
        }
    }
}
