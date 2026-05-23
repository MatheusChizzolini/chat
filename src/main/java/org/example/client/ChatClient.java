package org.example.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ChatClient {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);) {
            System.out.println("Conectado ao servidor.");
            BufferedReader keyboardInput = new BufferedReader(new InputStreamReader(System.in));
            BufferedReader serverInput = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter serverOutput = new PrintWriter(socket.getOutputStream(), true);

            Thread receiveThread = new Thread(new ReceiveThread(serverInput));
            Thread sendThread = new Thread(new SendThread(keyboardInput, serverOutput));

            receiveThread.start();
            sendThread.start();
            sendThread.join();

            socket.close();
            System.out.println("Cliente encerrado.");
        } catch (Exception e) {
            System.out.println("Erro no cliente: " + e.getMessage());
        }
    }
}
