package org.example.server;

import org.example.database.DatabaseInitializer;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChatServer {
    private static final int PORT = 12345;
    private static final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private static int clientCounter = 1;

    public static void main(String[] args) {
        DatabaseInitializer.initialize();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Servidor iniciado na porta " + PORT + ".");
            System.out.println("Aguardando conexões de clientes...");

            while (true) {
                Socket clientSocket = serverSocket.accept();

                String temporaryName = "Cliente " + clientCounter;
                clientCounter++;
                ClientHandler clientHandler = new ClientHandler(clientSocket, temporaryName);
                clients.add(clientHandler);

                System.out.println(temporaryName + " conectado: " + clientSocket.getInetAddress());

                Thread thread = new Thread(clientHandler);
                thread.start();
            }

        } catch (Exception e) {
            System.out.println("Erro no servidor: " + e.getMessage());
        }
    }

    public static void broadcast(String message, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender) {
                client.sendMessage(message);
            }
        }
    }

    public static void removeClient(ClientHandler clientHandler) {
        clients.remove(clientHandler);
        System.out.println(clientHandler.getClientName() + " removido da lista de clientes conectados.");
    }
}
