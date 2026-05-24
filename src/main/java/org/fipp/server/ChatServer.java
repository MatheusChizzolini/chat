package org.fipp.server;

import org.fipp.database.DatabaseInitializer;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ChatServer {
    private static final int PORT = 12345;
    private static final ConcurrentMap<Integer, ClientHandler> onlineClients = new ConcurrentHashMap<>();
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

                System.out.println(temporaryName + " conectado: " + clientSocket.getInetAddress());

                Thread thread = new Thread(clientHandler);
                thread.start();
            }

        } catch (Exception e) {
            System.out.println("Erro no servidor: " + e.getMessage());
        }
    }

    public static void addClient(ClientHandler clientHandler) {
        int loggedUserId = clientHandler.getLoggedUserId();
        if (loggedUserId > 0) {
            onlineClients.put(loggedUserId, clientHandler);
        }
    }

    public static ClientHandler findOnlineClient(int userId) {
        return onlineClients.get(userId);
    }

    public static void removeClient(ClientHandler clientHandler) {
        int loggedUserId = clientHandler.getLoggedUserId();
        if (loggedUserId > 0) {
            onlineClients.remove(loggedUserId, clientHandler);
        }

        System.out.println(clientHandler.getClientName() + " removido da lista de clientes conectados.");
    }
}
