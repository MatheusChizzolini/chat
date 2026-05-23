package org.example.server;

import org.example.database.DatabaseInitializer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class ChatServer {
    private static final int PORT = 12345;

    public static void main(String[] args) {
        DatabaseInitializer.initialize();

        try (ServerSocket serverSocket = new ServerSocket(PORT)){
            System.out.println("Servidor iniciado na porta: " + PORT + ".");
            System.out.println("Aguardando conexão de um cliente...");

            Socket clientSocket = serverSocket.accept();
            System.out.println("Cliente conectado: " + clientSocket.getInetAddress());

            BufferedReader input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter output = new PrintWriter(clientSocket.getOutputStream(), true);

            String message = input.readLine();
            System.out.println("Mensagem recebida do cliente: " + message);
            output.println("Servidor recebeu sua mensagem: " + message);

            clientSocket.close();
            System.out.println("Conexão encerrada.");
        } catch (Exception e) {
            System.out.println("Erro no servidor: "  + e.getMessage());
        }
    }
}
