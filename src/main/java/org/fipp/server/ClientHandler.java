package org.fipp.server;

import org.fipp.model.GroupInvitation;
import org.fipp.model.GroupJoinVote;
import org.fipp.model.User;
import org.fipp.repository.UserRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;

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
    private PrivateChatService privateChatService;
    private GroupService groupService;
    private boolean isAvailableInChat;
    private boolean hasActiveSession;

    public ClientHandler(Socket clientSocket, String clientName) {
        this.clientSocket = clientSocket;
        this.clientName = clientName;
    }

    public String getClientName() {
        return clientName;
    }

    public int getLoggedUserId() {
        if (loggedUser == null) {
            return 0;
        }

        return loggedUser.id();
    }

    @Override
    public void run() {
        try {
            BufferedReader input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            output = new PrintWriter(clientSocket.getOutputStream(), true);
            privateChatService = new PrivateChatService(this, output);
            groupService = new GroupService(this, output);

            boolean isClientConnected = true;
            while (isClientConnected) {
                loggedUser = authenticate(input);

                if (loggedUser == null) {
                    isClientConnected = false;
                } else {
                    clientName = loggedUser.username();
                    if (ChatServer.startSession(this)) {
                        hasActiveSession = true;
                        isClientConnected = handleLoggedUser(input);
                        closeCurrentSession();
                    } else {
                        output.println("Este usuario ja esta conectado em outra sessao.");
                    }
                    loggedUser = null;
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao manipular " + clientName + ": " + e.getMessage());
        } finally {
            closeCurrentSession();
            closeClientSocket();
        }
    }

    private User authenticate(BufferedReader input) throws IOException {
        User user = null;
        boolean isChoosing = true;

        while (isChoosing && user == null) {
            showAuthenticationMenu();
            String option = input.readLine();
            String normalizedOption = ChatCommandParser.normalize(option);

            if (option == null || normalizedOption.equals("sair")) {
                isChoosing = false;
                output.println("Conexao encerrada.");
            } else if (normalizedOption.equals("1")) {
                user = login(input);
            } else if (normalizedOption.equals("2")) {
                user = register(input);
            } else if (normalizedOption.equals("3")) {
                recoverPassword(input);
            } else {
                output.println("Opcao invalida. Digite 1 para login, 2 para cadastrar, 3 para recuperar senha ou 'sair' para encerrar.");
            }
        }

        return user;
    }

    private void showAuthenticationMenu() {
        output.println("Bem-vindo ao chat.");
        output.println("1 - Fazer login");
        output.println("2 - Cadastrar usuario");
        output.println("3 - Recuperar senha");
        output.println("Digite sua opcao:");
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
                output.println("Login ou senha invalidos.");
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
                output.println("Nao foi possivel cadastrar. O nome de usuario ou email ja estao em uso.");
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
                output.println("Nenhum usuario encontrado com esse email.");
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
            String action = currentStatus.equals(STATUS_ONLINE)
                    ? startOnlineChat(input)
                    : keepUserInactive(input, currentStatus);

            if (action.equals(ACTION_LOGOUT)) {
                isLoggedIn = false;
                output.println("Logout realizado.");
            } else if (action.equals(ACTION_EXIT)) {
                isClientConnected = false;
                isLoggedIn = false;
            } else if (action.equals(ACTION_STATUS)) {
                String selectedStatus = selectStatus(input);

                if (selectedStatus == null) {
                    isClientConnected = false;
                    isLoggedIn = false;
                    output.println("Conexao encerrada.");
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
        privateChatService.deliverQueuedMessages(loggedUser);
        groupService.deliverQueuedMessages(loggedUser);
        groupService.deliverResolvedJoinRequests(loggedUser);
        privateChatService.loadPendingPrivateRequests(loggedUser);
        groupService.loadPendingInvitations(loggedUser, !privateChatService.hasPendingRequests());
        groupService.loadPendingJoinVotes(
                loggedUser,
                !privateChatService.hasPendingRequests() && !groupService.hasPendingInvitations()
        );
        output.println("Digite 'ajuda' para ver os comandos disponiveis.");
        output.println("Digite uma mensagem:");

        String action = receiveMessages(input);
        removeFromOnlineChat();

        return action;
    }

    private String keepUserInactive(BufferedReader input, String status) throws IOException {
        UserRepository.updateStatus(loggedUser.id(), status);
        output.println("Status definido como " + translateStatus(status) + ".");
        output.println("Apenas usuarios online podem enviar e receber mensagens.");
        output.println("Digite 'ajuda' para ver os comandos disponiveis.");
        output.println("Digite um comando:");

        String action = null;
        boolean isWaiting = true;

        while (isWaiting && action == null) {
            String command = input.readLine();
            String normalizedCommand = ChatCommandParser.normalize(command);

            if (command == null || normalizedCommand.equals("sair")) {
                action = ACTION_EXIT;
            } else if (normalizedCommand.equals("logout")) {
                action = ACTION_LOGOUT;
            } else if (normalizedCommand.equals("status")) {
                action = ACTION_STATUS;
            } else if (normalizedCommand.equals("ajuda")) {
                showHelp();
                output.println("Digite um comando:");
            } else {
                output.println("Comando invalido. Digite 'ajuda' para ver os comandos disponiveis.");
                output.println("Digite um comando:");
            }
        }

        return action;
    }

    private String receiveMessages(BufferedReader input) throws IOException {
        String action = null;
        String message;
        boolean isReceiving = true;

        while ((message = input.readLine()) != null && isReceiving) {
            ChatCommand command = ChatCommandParser.parse(message);

            if (privateChatService.handlePendingPrivateResponse(command.normalizedText(), loggedUser)) {
                if (!privateChatService.hasPendingRequests()) {
                    groupService.promptNextPendingResponse();
                }
                output.println("Digite uma mensagem:");
            } else if (groupService.handlePendingInvitationResponse(command.normalizedText(), loggedUser)) {
                output.println("Digite uma mensagem:");
            } else if (groupService.handlePendingJoinVoteResponse(command.normalizedText(), loggedUser)) {
                output.println("Digite uma mensagem:");
            } else {
                action = handleChatCommand(command);
                isReceiving = action == null;
            }
        }

        if (action == null) {
            action = ACTION_EXIT;
        }

        return action;
    }

    private String handleChatCommand(ChatCommand command) {
        String action = null;

        switch (command.type()) {
            case EXIT -> {
                output.println("Voce saiu do chat.");
                action = ACTION_EXIT;
            }
            case LOGOUT -> action = ACTION_LOGOUT;
            case STATUS -> action = ACTION_STATUS;
            case HELP -> showHelp();
            case LIST_USERS -> listOnlineUsers();
            case LIST_GROUPS -> groupService.listGroups();
            case CREATE_GROUP -> groupService.createGroup(command, loggedUser);
            case INVITE_TO_GROUP -> groupService.inviteMembers(command, loggedUser);
            case REQUEST_GROUP_ENTRY -> groupService.requestEntry(command, loggedUser);
            case LEAVE_GROUP -> groupService.leaveGroup(command, loggedUser);
            case GROUP_MESSAGE -> groupService.handleGroupMessage(command, loggedUser);
            case DIRECT_MESSAGE -> privateChatService.handleDirectMessage(command, loggedUser);
            case INVALID -> output.println("Mensagem invalida. Digite 'ajuda'.");
        }

        if (action == null) {
            output.println("Digite uma mensagem:");
        }

        return action;
    }

    public void receivePrivateRequest(User requester) {
        if (privateChatService != null) {
            privateChatService.receivePrivateRequest(requester);
        }
    }

    public void receiveGroupInvitation(GroupInvitation invitation) {
        if (groupService != null) {
            groupService.receiveInvitation(invitation, !privateChatService.hasPendingRequests());
        }
    }

    public void receiveGroupJoinVote(GroupJoinVote vote) {
        if (groupService != null) {
            groupService.receiveJoinVote(vote, !privateChatService.hasPendingRequests());
        }
    }

    public void cancelGroupJoinVote(int requestId, String groupName) {
        if (groupService != null) {
            groupService.cancelJoinVote(requestId, groupName, !privateChatService.hasPendingRequests());
        }
    }

    private void listOnlineUsers() {
        List<User> users = UserRepository.findOnlineUsers();

        if (users.isEmpty()) {
            output.println("Nenhum usuario online.");
        } else {
            output.println("Usuarios online:");
            for (User user : users) {
                String currentUserSuffix = user.id() == loggedUser.id() ? " (voce)" : "";
                output.println("- " + user.username() + currentUserSuffix);
            }
        }
    }

    private String selectStatus(BufferedReader input) throws IOException {
        String status = null;
        boolean isChoosing = true;

        while (isChoosing && status == null) {
            output.println("Defina seu status:");
            output.println("1 - Online");
            output.println("2 - Offline");
            output.println("3 - Ocupado");
            output.println("Digite sua opcao:");

            String option = input.readLine();
            String normalizedOption = ChatCommandParser.normalize(option);

            if (option == null || normalizedOption.equals("sair")) {
                isChoosing = false;
            } else if (normalizedOption.equals("1")) {
                status = STATUS_ONLINE;
            } else if (normalizedOption.equals("2")) {
                status = STATUS_OFFLINE;
            } else if (normalizedOption.equals("3")) {
                status = STATUS_BUSY;
            } else {
                output.println("Opcao invalida. Digite 1, 2 ou 3.");
            }
        }

        return status;
    }

    private void showHelp() {
        output.println("");
        output.println("=========================== AJUDA ===========================");
        output.println("[GERAIS]");
        printHelpCommand("ajuda", "Exibe esta lista de comandos.");
        printHelpCommand("status", "Altera seu status: online, offline ou ocupado.");
        printHelpCommand("logout", "Sai da conta e volta ao menu inicial.");
        printHelpCommand("sair", "Encerra o cliente e desconecta do servidor.");

        printHelpSection("CONTATOS E MENSAGENS");
        printHelpCommand("listausuarios", "Lista os usuarios online.");
        printHelpCommand("@usuario: mensagem", "Envia uma mensagem privada.");

        printHelpSection("GRUPOS");
        printHelpCommand("listagrupos", "Lista os grupos existentes.");
        printHelpCommand("novogrupo nomegrupo", "Cria um grupo e inclui voce.");
        printHelpCommand("inserir &grupo@usuario1,@usuario2", "Convida usuarios para um grupo.");
        printHelpCommand("entrar &grupo", "Solicita entrada aos participantes.");
        printHelpCommand("sair &grupo", "Sai do grupo e avisa os participantes.");
        printHelpCommand("&grupo: mensagem", "Envia mensagem para todo o grupo.");
        printHelpCommand("&grupo@usuario1,@usuario2: mensagem", "Envia apenas aos membros informados.");
        output.println("=============================================================");
    }

    private void printHelpSection(String title) {
        output.println("");
        output.println("[" + title + "]");
    }

    private void printHelpCommand(String command, String description) {
        output.printf("  %-38s %s%n", command, description);
    }

    private String readRequiredField(BufferedReader input, String prompt) throws IOException {
        String value = null;
        boolean isReading = true;

        while (isReading && value == null) {
            output.println(prompt);
            String typedValue = input.readLine();
            String normalizedValue = ChatCommandParser.normalize(typedValue);

            if (typedValue == null || normalizedValue.equals("sair")) {
                isReading = false;
                output.println("Operacao cancelada.");
            } else if (typedValue.isBlank()) {
                output.println("Este campo e obrigatorio.");
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
            isAvailableInChat = false;
        }
    }

    private void closeCurrentSession() {
        if (loggedUser != null && hasActiveSession) {
            privateChatService.clearPendingRequests();
            groupService.clearPendingInvitations();
            UserRepository.updateStatus(loggedUser.id(), STATUS_OFFLINE);
            ChatServer.endSession(this);
            isAvailableInChat = false;
            hasActiveSession = false;
        }
    }

    public boolean sendMessage(String message) {
        if (output != null) {
            output.println(message);
            return !output.checkError();
        }

        return false;
    }

    private void closeClientSocket() {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }

            System.out.println("Cliente desconectado.");
        } catch (Exception e) {
            System.out.println("Erro ao fechar conexao com cliente: " + e.getMessage());
        }
    }
}
