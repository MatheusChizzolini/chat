package org.fipp.server;

import org.fipp.model.User;
import org.fipp.repository.UserRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private static final String STATUS_ONLINE = "online";
    private static final String STATUS_OFFLINE = "offline";
    private static final String STATUS_BUSY = "busy";
    private static final String ACTION_EXIT = "exit";
    private static final String ACTION_LOGOUT = "logout";
    private static final String ACTION_STATUS = "status";

    private final Socket clientSocket;
    private String clientName;
    private User loggedUser;
    private PrintWriter output;
    private boolean isAvailableInChat;

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

            boolean isClientConnected = true;
            while (isClientConnected) {
                loggedUser = authenticate(input);

                if (loggedUser == null) {
                    isClientConnected = false;
                } else {
                    clientName = loggedUser.fullName();
                    isClientConnected = handleLoggedUser(input);
                    loggedUser = null;
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao manipular " + clientName + ": " + e.getMessage());
        } finally {
            disconnectLoggedUser();
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
            } else if (option.equals("3")) {
                recoverPassword(input);
            } else {
                output.println("Opção inválida. Digite 1 para login, 2 para cadastrar, 3 para recuperar senha ou 'sair' para encerrar.");
            }
        }

        return user;
    }

    private void showAuthenticationMenu() {
        output.println("Bem-vindo ao chat.");
        output.println("1 - Fazer login");
        output.println("2 - Cadastrar usuário");
        output.println("3 - Recuperar senha");
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

    private void recoverPassword(BufferedReader input) throws IOException {
        String email = readRequiredField(input, "Email cadastrado:");

        if (email != null) {
            String password = UserRepository.findPasswordByEmail(email);
            if (password == null) {
                output.println("Nenhum usuário encontrado com esse email.");
            } else {
                output.println("Senha cadastrada: " + password);
            }
        }
    }

    private boolean handleLoggedUser(BufferedReader input) throws IOException {
        boolean isClientConnected = true;
        boolean isLoggedIn = true;
        String currentStatus = STATUS_ONLINE;

        while (isClientConnected && isLoggedIn) {
            String action;

            if (currentStatus.equals(STATUS_ONLINE)) {
                action = startOnlineChat(input);
            } else {
                action = keepUserInactive(input, currentStatus);
            }

            if (action.equals(ACTION_LOGOUT)) {
                isLoggedIn = false;
                UserRepository.updateStatus(loggedUser.id(), STATUS_OFFLINE);
                output.println("Logout realizado.");
            } else if (action.equals(ACTION_EXIT)) {
                isClientConnected = false;
                isLoggedIn = false;
                UserRepository.updateStatus(loggedUser.id(), STATUS_OFFLINE);
            } else if (action.equals(ACTION_STATUS)) {
                String selectedStatus = selectStatus(input);

                if (selectedStatus == null) {
                    isClientConnected = false;
                    isLoggedIn = false;
                    UserRepository.updateStatus(loggedUser.id(), STATUS_OFFLINE);
                    output.println("Conexão encerrada.");
                } else {
                    currentStatus = selectedStatus;
                }
            }
        }

        return isClientConnected;
    }

    private String startOnlineChat(BufferedReader input) throws IOException {
        UserRepository.updateStatus(loggedUser.id(), STATUS_ONLINE);
        ChatServer.addClient(this);
        isAvailableInChat = true;

        output.println("Conectado ao chat como " + clientName + ".");
        output.println("Digite 'ajuda' para ver os comandos disponíveis.");
        output.println("Digite uma mensagem:");

        ChatServer.broadcast(clientName + " entrou no chat.", this);
        String action = receiveMessages(input);
        removeFromOnlineChat();

        return action;
    }

    private String keepUserInactive(BufferedReader input, String status) throws IOException {
        UserRepository.updateStatus(loggedUser.id(), status);
        output.println("Status definido como " + translateStatus(status) + ".");
        output.println("Apenas usuários online podem enviar e receber mensagens.");
        output.println("Digite 'ajuda' para ver os comandos disponíveis.");
        output.println("Digite um comando:");

        String action = null;
        boolean isWaiting = true;

        while (isWaiting && action == null) {
            String command = input.readLine();

            if (command == null || command.equalsIgnoreCase("sair")) {
                action = ACTION_EXIT;
            } else if (command.equalsIgnoreCase("logout")) {
                action = ACTION_LOGOUT;
            } else if (command.equalsIgnoreCase("status")) {
                action = ACTION_STATUS;
            } else if (command.equalsIgnoreCase("ajuda")) {
                showHelp();
                output.println("Digite um comando:");
            } else {
                output.println("Comando inválido. Digite 'ajuda' para ver os comandos disponíveis.");
                output.println("Digite um comando:");
            }
        }

        return action;
    }

    private String receiveMessages(BufferedReader input) throws IOException {
        String action = ACTION_EXIT;
        String message;
        boolean isReceiving = true;

        while ((message = input.readLine()) != null && isReceiving) {
            if (message.equalsIgnoreCase("sair")) {
                output.println("Você saiu do chat.");
                action = ACTION_EXIT;
                isReceiving = false;
            } else if (message.equalsIgnoreCase("logout")) {
                action = ACTION_LOGOUT;
                isReceiving = false;
            } else if (message.equalsIgnoreCase("status")) {
                action = ACTION_STATUS;
                isReceiving = false;
            } else if (message.equalsIgnoreCase("ajuda")) {
                showHelp();
                output.println("Digite uma mensagem:");
            } else {
                System.out.println(clientName + " enviou: " + message);
                String formattedMessage = clientName + ": " + message;
                ChatServer.broadcast(formattedMessage, this);

                output.println("Mensagem enviada.");
            }
        }

        return action;
    }

    private String selectStatus(BufferedReader input) throws IOException {
        String status = null;
        boolean isChoosing = true;

        while (isChoosing && status == null) {
            output.println("Defina seu status:");
            output.println("1 - Online");
            output.println("2 - Offline");
            output.println("3 - Ocupado");
            output.println("Digite sua opção:");

            String option = input.readLine();

            if (option == null || option.equalsIgnoreCase("sair")) {
                isChoosing = false;
            } else if (option.equals("1")) {
                status = STATUS_ONLINE;
            } else if (option.equals("2")) {
                status = STATUS_OFFLINE;
            } else if (option.equals("3")) {
                status = STATUS_BUSY;
            } else {
                output.println("Opção inválida. Digite 1, 2 ou 3.");
            }
        }

        return status;
    }

    private void showHelp() {
        output.println("Comandos disponíveis:");
        output.println("ajuda - Exibe esta lista de comandos.");
        output.println("status - Permite alterar seu status para online, offline ou ocupado.");
        output.println("logout - Sai da conta atual e volta para o menu inicial.");
        output.println("sair - Encerra o cliente e desconecta do servidor.");
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

    private String translateStatus(String status) {
        String translatedStatus = "offline";

        if (status.equals(STATUS_ONLINE)) {
            translatedStatus = "online";
        } else if (status.equals(STATUS_BUSY)) {
            translatedStatus = "ocupado";
        }

        return translatedStatus;
    }

    private void removeFromOnlineChat() {
        if (isAvailableInChat) {
            ChatServer.removeClient(this);
            ChatServer.broadcast(clientName + " saiu do chat.", this);
            isAvailableInChat = false;
        }
    }

    private void disconnectLoggedUser() {
        if (loggedUser != null) {
            UserRepository.updateStatus(loggedUser.id(), STATUS_OFFLINE);
            removeFromOnlineChat();
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
