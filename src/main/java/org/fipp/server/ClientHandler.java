package org.fipp.server;

import org.fipp.model.User;
import org.fipp.repository.UserRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private String clientName;
    private User loggedUser;
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

            loggedUser = authenticate(input);

            if (loggedUser != null) {
                clientName = loggedUser.fullName();
                UserRepository.updateStatus(loggedUser.id(), "online");
                ChatServer.addClient(this);

                output.println("Conectado ao chat como " + clientName + ".");
                output.println("Digite suas mensagens ou 'sair' para encerrar.");

                ChatServer.broadcast(clientName + " entrou no chat.", this);
                receiveMessages(input);
            }
        } catch (Exception e) {
            System.out.println("Erro ao manipular " + clientName + ": " + e.getMessage());
        } finally {
            if (loggedUser != null) {
                UserRepository.updateStatus(loggedUser.id(), "offline");
                ChatServer.removeClient(this);
                ChatServer.broadcast(clientName + " saiu do chat.", this);
            }

            closeClientSocket();
        }
    }

    private User authenticate(BufferedReader input) throws IOException {
        User user = null;
        boolean isChoosing = true;

        while (isChoosing && user == null) {
            showAuthenticationMenu();
            String option = input.readLine();

            if (option == null || option.equalsIgnoreCase("sair")) {
                isChoosing = false;
                output.println("Conexão encerrada.");
            } else if (option.equals("1")) {
                user = login(input);
            } else if (option.equals("2")) {
                user = register(input);
            } else {
                output.println("Opção inválida. Digite 1 para login, 2 para cadastrar ou 'sair' para encerrar.");
            }
        }

        return user;
    }

    private void showAuthenticationMenu() {
        output.println("Bem-vindo ao chat.");
        output.println("1 - Fazer login");
        output.println("2 - Cadastrar usuário");
        output.println("Digite sua opção:");
    }

    private User login(BufferedReader input) throws IOException {
        String username = readRequiredField(input, "Login:");
        String password = null;
        User user = null;

        if (username != null) {
            password = readRequiredField(input, "Senha:");
        }

        if (username != null && password != null) {
            user = UserRepository.findByUsernameAndPassword(username, password);
            if (user == null) {
                output.println("Login ou senha inválidos.");
            }
        }

        return user;
    }

    private User register(BufferedReader input) throws IOException {
        String fullName = readRequiredField(input, "Nome completo:");
        String username = null;
        String email = null;
        String password = null;
        User user = null;

        if (fullName != null) {
            username = readRequiredField(input, "Login:");
        }

        if (username != null) {
            email = readRequiredField(input, "Email:");
        }

        if (email != null) {
            password = readRequiredField(input, "Senha:");
        }

        if (fullName != null && username != null && email != null && password != null) {
            user = UserRepository.register(fullName, username, email, password);
            if (user == null) {
                output.println("Não foi possível cadastrar. Confira se nome, login ou email já estão em uso.");
            } else {
                output.println("Cadastro realizado com sucesso.");
            }
        }

        return user;
    }

    private String readRequiredField(BufferedReader input, String prompt) throws IOException {
        String value = null;
        boolean isReading = true;

        while (isReading && value == null) {
            output.println(prompt);
            String typedValue = input.readLine();

            if (typedValue == null || typedValue.equalsIgnoreCase("sair")) {
                isReading = false;
                output.println("Operação cancelada.");
            } else if (typedValue.isBlank()) {
                output.println("Este campo é obrigatório.");
            } else {
                value = typedValue.trim();
            }
        }

        return value;
    }

    private void receiveMessages(BufferedReader input) throws IOException {
        String message;
        boolean isConnected = true;

        while ((message = input.readLine()) != null && isConnected) {
            if (message.equalsIgnoreCase("sair")) {
                output.println("Você saiu do chat.");
                isConnected = false;
            } else {
                System.out.println(clientName + " enviou: " + message);
                String formattedMessage = clientName + ": " + message;
                ChatServer.broadcast(formattedMessage, this);

                output.println("Mensagem enviada.");
            }
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
