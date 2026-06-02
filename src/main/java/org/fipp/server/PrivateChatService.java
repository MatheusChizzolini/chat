package org.fipp.server;

import org.fipp.model.DirectMessage;
import org.fipp.model.User;
import org.fipp.repository.AuthorizedConnectionRepository;
import org.fipp.repository.DirectMessageRepository;
import org.fipp.repository.UserRepository;

import java.io.PrintWriter;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PrivateChatService {
    private static final String RESPONSE_YES = "sim";
    private static final String RESPONSE_NO = "nao";

    private final ClientHandler owner;
    private final PrintWriter output;
    private final Queue<PendingPrivateRequest> pendingPrivateRequests = new ConcurrentLinkedQueue<>();

    public PrivateChatService(ClientHandler owner, PrintWriter output) {
        this.owner = owner;
        this.output = output;
    }

    public void clearPendingRequests() {
        pendingPrivateRequests.clear();
    }

    public boolean hasPendingRequests() {
        return !pendingPrivateRequests.isEmpty();
    }

    public void handleDirectMessage(ChatCommand command, User sender) {
        String receiverUsername = command.receiverUsername();
        String content = command.content();

        if (receiverUsername.isBlank()) {
            output.println("Informe o nome de usuario do destinatario.");
        } else if (receiverUsername.contains(",")) {
            output.println("Envio privado para varios usuarios sera tratado na etapa de grupos.");
        } else if (content.isBlank()) {
            output.println("A mensagem nao pode ficar vazia.");
        } else {
            User receiver = UserRepository.findByUsername(receiverUsername);

            if (receiver == null) {
                output.println("Usuario nao encontrado: " + receiverUsername);
            } else if (receiver.id() == sender.id()) {
                output.println("Voce nao pode enviar mensagem privada para si mesmo.");
            } else {
                sendOrRequestPrivateMessage(sender, receiver, content);
            }
        }
    }

    public void receivePrivateRequest(User requester) {
        boolean alreadyQueued = pendingPrivateRequests.stream()
                .anyMatch(request -> request.requesterId() == requester.id());

        if (!alreadyQueued) {
            pendingPrivateRequests.add(new PendingPrivateRequest(requester.id(), requester.username()));
            output.println(requester.username() + " quer enviar mensagens para voce.");
        } else {
            output.println("Voce ainda tem uma solicitacao privada pendente de " + requester.username() + ".");
        }

        output.println("Aceitar? (sim/nao):");
    }

    public boolean handlePendingPrivateResponse(String normalizedMessage, User receiver) {
        if (pendingPrivateRequests.isEmpty()) {
            return false;
        }

        if (!normalizedMessage.equals(RESPONSE_YES) && !normalizedMessage.equals(RESPONSE_NO)) {
            return false;
        }

        PendingPrivateRequest request = pendingPrivateRequests.poll();
        if (request == null) {
            return true;
        }

        if (normalizedMessage.equals(RESPONSE_YES)) {
            acceptPrivateRequest(request, receiver);
        } else {
            rejectPrivateRequest(request, receiver);
        }

        PendingPrivateRequest nextRequest = pendingPrivateRequests.peek();
        if (nextRequest != null) {
            output.println("Proxima solicitacao: " + nextRequest.requesterUsername() + ". Aceitar? (sim/nao):");
        }

        return true;
    }

    public void deliverQueuedMessages(User receiver) {
        List<DirectMessage> messages = DirectMessageRepository.findQueuedMessagesForReceiver(receiver.id());

        if (!messages.isEmpty()) {
            output.println("Mensagens pendentes:");
            for (DirectMessage message : messages) {
                if (owner.sendMessage(formatDirectMessage(message))) {
                    DirectMessageRepository.markDelivered(message.id());
                } else {
                    break;
                }
            }
        }
    }

    public void loadPendingPrivateRequests(User receiver) {
        List<User> requesters = AuthorizedConnectionRepository.findPendingRequestersForReceiver(receiver.id());

        for (User requester : requesters) {
            receivePrivateRequest(requester);
        }
    }

    private void sendOrRequestPrivateMessage(User sender, User receiver, String content) {
        String authorizationStatus = AuthorizedConnectionRepository.findStatusBetween(sender.id(), receiver.id());

        if (AuthorizedConnectionRepository.STATUS_ACCEPTED.equals(authorizationStatus)) {
            sendAuthorizedDirectMessage(sender, receiver, content);
        } else if (AuthorizedConnectionRepository.STATUS_PENDING.equals(authorizationStatus)) {
            handlePendingAuthorization(sender, receiver, content);
        } else {
            requestNewPrivateConversation(sender, receiver, content);
        }
    }

    private void handlePendingAuthorization(User sender, User receiver, String content) {
        if (AuthorizedConnectionRepository.hasPendingRequest(receiver.id(), sender.id())) {
            receivePrivateRequest(receiver);
            output.println("Responda (sim/nao) para a solicitacao de " + receiver.username() + ".");
        } else {
            DirectMessageRepository.save(
                    sender.id(),
                    receiver.id(),
                    content,
                    DirectMessageRepository.STATUS_PENDING_AUTHORIZATION
            );
            notifyPrivateRequest(sender, receiver);
            output.println("Aguarde o usuario " + receiver.username() + " responder sua solicitacao.");
        }
    }

    private void requestNewPrivateConversation(User sender, User receiver, String content) {
        AuthorizedConnectionRepository.requestConnection(sender.id(), receiver.id());
        DirectMessageRepository.save(
                sender.id(),
                receiver.id(),
                content,
                DirectMessageRepository.STATUS_PENDING_AUTHORIZATION
        );
        notifyPrivateRequest(sender, receiver);
        output.println("Solicitacao enviada para " + receiver.username() + ".");
    }

    private void sendAuthorizedDirectMessage(User sender, User receiver, String content) {
        ClientHandler receiverHandler = ChatServer.findOnlineClient(receiver.id());

        if (receiverHandler != null) {
            DirectMessage message = DirectMessageRepository.save(
                    sender.id(),
                    receiver.id(),
                    content,
                    DirectMessageRepository.STATUS_QUEUED
            );

            if (message == null) {
                output.println("Nao foi possivel salvar a mensagem.");
            } else if (receiverHandler.sendMessage(formatDirectMessage(message))) {
                DirectMessageRepository.markDelivered(message.id());
                output.println("Mensagem recebida por " + receiver.username() + ".");
            } else {
                output.println(receiver.username() + " nao recebeu a mensagem agora. A mensagem sera entregue quando ficar online.");
            }
        } else {
            DirectMessageRepository.save(
                    sender.id(),
                    receiver.id(),
                    content,
                    DirectMessageRepository.STATUS_QUEUED
            );
            output.println(receiver.username() + " esta offline ou ocupado. A mensagem sera entregue quando ficar online.");
        }
    }

    private void notifyPrivateRequest(User sender, User receiver) {
        ClientHandler receiverHandler = ChatServer.findOnlineClient(receiver.id());
        if (receiverHandler != null) {
            receiverHandler.receivePrivateRequest(sender);
        }
    }

    private void acceptPrivateRequest(PendingPrivateRequest request, User receiver) {
        boolean accepted = AuthorizedConnectionRepository.accept(request.requesterId(), receiver.id());

        if (accepted) {
            int deliveredMessages = deliverPendingAuthorizationMessages(request.requesterId(), receiver);
            output.println("Conversa privada com " + request.requesterUsername() + " aceita.");
            notifyOnlineUser(
                    request.requesterId(),
                    receiver.username() + " aceitou sua conversa privada. Mensagens entregues: " + deliveredMessages + "."
            );
        } else {
            output.println("Nao foi possivel aceitar a solicitacao privada.");
        }
    }

    private void rejectPrivateRequest(PendingPrivateRequest request, User receiver) {
        boolean rejected = AuthorizedConnectionRepository.reject(request.requesterId(), receiver.id());
        int rejectedMessages = DirectMessageRepository.rejectPendingAuthorizationMessages(request.requesterId(), receiver.id());

        if (rejected) {
            output.println("Conversa privada com " + request.requesterUsername() + " recusada.");
            notifyOnlineUser(
                    request.requesterId(),
                    receiver.username() + " recusou sua conversa privada. Mensagens rejeitadas: " + rejectedMessages + "."
            );
        } else {
            output.println("Nao foi possivel recusar a solicitacao privada.");
        }
    }

    private int deliverPendingAuthorizationMessages(int senderId, User receiver) {
        List<DirectMessage> messages = DirectMessageRepository.findPendingAuthorizationMessages(senderId, receiver.id());
        int deliveredMessages = 0;

        for (DirectMessage message : messages) {
            if (owner.sendMessage(formatDirectMessage(message))) {
                DirectMessageRepository.markDelivered(message.id());
                deliveredMessages++;
            } else {
                DirectMessageRepository.markQueued(message.id());
            }
        }

        return deliveredMessages;
    }

    private void notifyOnlineUser(int userId, String message) {
        ClientHandler requesterHandler = ChatServer.findOnlineClient(userId);
        if (requesterHandler != null) {
            requesterHandler.sendMessage(message);
        }
    }

    private String formatDirectMessage(DirectMessage message) {
        return message.senderUsername() + " (" + formatMessageTime(message.createdAt()) + "): " + message.content();
    }

    private String formatMessageTime(String createdAt) {
        if (createdAt != null && createdAt.length() >= 16) {
            return createdAt.substring(11, 16);
        }

        return "--:--";
    }

    private record PendingPrivateRequest(int requesterId, String requesterUsername) {
    }
}
