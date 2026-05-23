package org.example.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable{
    private final Socket clientSocket;

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try {
            BufferedReader input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter output = new PrintWriter(clientSocket.getOutputStream(), true);
            output.println("Conectado ao servidor. Digite 'sair' para encerrar.");

            String message;
            boolean leaveMessage = false;
            while ((message = input.readLine()) != null && !leaveMessage) {
                System.out.println("Mensagem recebida: " + message);
                if (message.equalsIgnoreCase("sair")) {
                    output.println("Conexão encerrada pelo cliente.");
                    leaveMessage = true;
                }

                output.println("Servidor recebeu: " + message);
            }
        } catch (Exception e) {
            System.out.println("Erro ao manipular cliente: " + e.getMessage());
        } finally {
            closeClientSocket();
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
