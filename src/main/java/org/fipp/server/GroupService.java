package org.fipp.server;

import org.fipp.model.Group;
import org.fipp.model.GroupInvitation;
import org.fipp.model.GroupJoinRequest;
import org.fipp.model.GroupJoinVote;
import org.fipp.model.GroupMessage;
import org.fipp.model.User;
import org.fipp.repository.DirectMessageRepository;
import org.fipp.repository.GroupInvitationRepository;
import org.fipp.repository.GroupJoinRequestRepository;
import org.fipp.repository.GroupMessageRepository;
import org.fipp.repository.GroupRepository;
import org.fipp.repository.UserRepository;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GroupService {
    private static final String RESPONSE_YES = "sim";
    private static final String RESPONSE_NO = "nao";

    private final ClientHandler owner;
    private final PrintWriter output;
    private final Queue<GroupInvitation> pendingInvitations = new ConcurrentLinkedQueue<>();
    private final Queue<GroupJoinVote> pendingJoinVotes = new ConcurrentLinkedQueue<>();
    private PendingResponse currentPendingResponse = PendingResponse.NONE;

    public GroupService(ClientHandler owner, PrintWriter output) {
        this.owner = owner;
        this.output = output;
    }

    public void createGroup(ChatCommand command, User creator) {
        String groupName = command.groupName();

        if (groupName == null || groupName.isBlank()) {
            output.println("Informe o nome do grupo. Exemplo: novogrupo redes2");
        } else if (containsReservedCharacter(groupName)) {
            output.println("O nome do grupo nao pode conter &, @, : ou virgula.");
        } else if (GroupRepository.findByName(groupName) != null) {
            output.println("Ja existe um grupo com o nome " + groupName + ".");
        } else {
            Group group = GroupRepository.create(groupName, creator.id());

            if (group == null) {
                output.println("Nao foi possivel criar o grupo.");
            } else {
                output.println("Grupo " + group.name() + " criado. Voce ja faz parte dele.");
            }
        }
    }

    public void listGroups() {
        List<Group> groups = GroupRepository.findAll();

        if (groups.isEmpty()) {
            output.println("Nenhum grupo cadastrado.");
        } else {
            output.println("Grupos cadastrados:");
            for (Group group : groups) {
                output.println("- " + group.name() + " (criado por " + group.creatorUsername() + ")");
            }
        }
    }

    public void inviteMembers(ChatCommand command, User inviter) {
        if (command.groupName() == null || command.groupName().isBlank()
                || command.memberUsernames() == null || command.memberUsernames().isEmpty()) {
            output.println("Use: inserir &nomegrupo@usuario1,@usuario2");
            return;
        }

        Group group = GroupRepository.findByName(command.groupName());
        if (group == null) {
            output.println("Grupo nao encontrado: " + command.groupName());
        } else if (!GroupRepository.isMember(group.id(), inviter.id())) {
            output.println("Voce precisa pertencer ao grupo para convidar novos membros.");
        } else {
            Set<String> invitedUsernames = new LinkedHashSet<>();
            for (String username : command.memberUsernames()) {
                if (invitedUsernames.add(username.toLowerCase(Locale.ROOT))) {
                    inviteMember(group, inviter, username);
                }
            }
        }
    }

    public void requestEntry(ChatCommand command, User requester) {
        if (command.groupName() == null || command.groupName().isBlank()) {
            output.println("Use: entrar &nomegrupo");
            return;
        }

        Group group = GroupRepository.findByName(command.groupName());
        if (group == null) {
            output.println("Grupo nao encontrado: " + command.groupName());
        } else if (GroupRepository.isMember(group.id(), requester.id())) {
            output.println("Voce ja pertence ao grupo " + group.name() + ".");
        } else {
            GroupJoinRequest existingRequest = GroupJoinRequestRepository.findByGroupAndRequester(
                    group.id(),
                    requester.id()
            );
            if (existingRequest != null
                    && GroupJoinRequestRepository.STATUS_PENDING.equals(existingRequest.status())) {
                output.println("Sua solicitacao para entrar no grupo " + group.name() + " ainda esta pendente.");
                return;
            }

            List<User> members = GroupRepository.findMembers(group.id());
            if (members.isEmpty()) {
                if (GroupRepository.addMember(group.id(), requester.id())) {
                    output.println("Voce entrou no grupo " + group.name() + ", que nao tinha participantes.");
                } else {
                    output.println("Nao foi possivel entrar no grupo " + group.name() + ".");
                }
                return;
            }

            GroupJoinRequest request = GroupJoinRequestRepository.create(group, requester, members);
            if (request == null) {
                output.println("Nao foi possivel solicitar entrada no grupo " + group.name() + ".");
            } else {
                for (User member : members) {
                    notifyJoinVoter(request, member);
                }
                output.println("Solicitacao para entrar no grupo " + group.name() + " enviada aos participantes.");
            }
        }
    }

    public void leaveGroup(ChatCommand command, User member) {
        if (command.groupName() == null || command.groupName().isBlank()) {
            output.println("Use: sair &nomegrupo");
            return;
        }

        Group group = GroupRepository.findByName(command.groupName());
        if (group == null) {
            output.println("Grupo nao encontrado: " + command.groupName());
        } else if (!GroupRepository.isMember(group.id(), member.id())) {
            output.println("Voce nao pertence ao grupo " + group.name() + ".");
        } else if (GroupRepository.removeMember(group.id(), member.id())) {
            output.println("Voce saiu do grupo " + group.name() + ".");
            notifyGroupExit(group, member);
        } else {
            output.println("Nao foi possivel sair do grupo " + group.name() + ".");
        }
    }

    public void handleGroupMessage(ChatCommand command, User sender) {
        if (command.groupName() == null || command.groupName().isBlank()) {
            output.println("Use: &nomegrupo: mensagem ou &nomegrupo@usuario1,@usuario2: mensagem");
            return;
        }

        Group group = GroupRepository.findByName(command.groupName());
        if (group == null) {
            output.println("Grupo nao encontrado: " + command.groupName());
        } else if (!GroupRepository.isMember(group.id(), sender.id())) {
            output.println("Voce precisa pertencer ao grupo para enviar mensagens.");
        } else if (command.content() == null || command.content().isBlank()) {
            output.println("A mensagem nao pode ficar vazia.");
        } else {
            List<User> receivers = command.memberUsernames() == null
                    ? findBroadcastReceivers(group, sender)
                    : findSelectedReceivers(group, sender, command.memberUsernames());

            if (receivers.isEmpty()) {
                output.println("Nenhum participante valido para receber a mensagem.");
            } else {
                for (User receiver : receivers) {
                    sendGroupMessage(group, sender, receiver, command.content());
                }
            }
        }
    }

    public void deliverQueuedMessages(User receiver) {
        List<GroupMessage> messages = GroupMessageRepository.findQueuedForReceiver(receiver.id());

        if (!messages.isEmpty()) {
            output.println("Mensagens pendentes de grupos:");
            for (GroupMessage message : messages) {
                if (owner.sendMessage(formatGroupMessage(message))) {
                    GroupMessageRepository.markDelivered(message.id());
                } else {
                    break;
                }
            }
        }
    }

    public void deliverResolvedJoinRequests(User requester) {
        for (GroupJoinRequest request : GroupJoinRequestRepository.findUnnotifiedResolvedForRequester(requester.id())) {
            output.println(formatJoinResolution(request));
            GroupJoinRequestRepository.markNotified(request.id());
        }
    }

    public void receiveInvitation(GroupInvitation invitation, boolean showPrompt) {
        boolean alreadyQueued = pendingInvitations.stream()
                .anyMatch(pending -> pending.id() == invitation.id());

        if (!alreadyQueued) {
            pendingInvitations.add(invitation);

            if (showPrompt && currentPendingResponse == PendingResponse.NONE) {
                promptNextPendingResponse();
            }
        }
    }

    public void loadPendingInvitations(User receiver, boolean showPrompt) {
        List<GroupInvitation> invitations = GroupInvitationRepository.findPendingForReceiver(receiver.id());
        boolean canPrompt = showPrompt;

        for (GroupInvitation invitation : invitations) {
            receiveInvitation(invitation, canPrompt);
            canPrompt = false;
        }
    }

    public boolean handlePendingInvitationResponse(String normalizedMessage, User receiver) {
        if (currentPendingResponse != PendingResponse.INVITATION
                || pendingInvitations.isEmpty()
                || (!normalizedMessage.equals(RESPONSE_YES) && !normalizedMessage.equals(RESPONSE_NO))) {
            return false;
        }

        GroupInvitation invitation = pendingInvitations.poll();
        if (invitation == null || invitation.receiverId() != receiver.id()) {
            return true;
        }

        if (normalizedMessage.equals(RESPONSE_YES)) {
            acceptInvitation(invitation, receiver);
        } else {
            rejectInvitation(invitation, receiver);
        }

        currentPendingResponse = PendingResponse.NONE;
        promptNextPendingResponse();
        return true;
    }

    public void clearPendingInvitations() {
        pendingInvitations.clear();
        pendingJoinVotes.clear();
        currentPendingResponse = PendingResponse.NONE;
    }

    public boolean hasPendingInvitations() {
        return !pendingInvitations.isEmpty();
    }

    public void receiveJoinVote(GroupJoinVote vote, boolean showPrompt) {
        boolean alreadyQueued = pendingJoinVotes.stream()
                .anyMatch(pending -> pending.requestId() == vote.requestId());

        if (!alreadyQueued) {
            pendingJoinVotes.add(vote);
            if (showPrompt && currentPendingResponse == PendingResponse.NONE) {
                promptNextPendingResponse();
            }
        }
    }

    public void loadPendingJoinVotes(User voter, boolean showPrompt) {
        List<GroupJoinVote> votes = GroupJoinRequestRepository.findPendingVotesForVoter(voter.id());
        for (GroupJoinVote vote : votes) {
            receiveJoinVote(vote, false);
        }

        if (showPrompt) {
            promptNextPendingResponse();
        }
    }

    public boolean handlePendingJoinVoteResponse(String normalizedMessage, User voter) {
        if (currentPendingResponse != PendingResponse.JOIN_VOTE
                || pendingJoinVotes.isEmpty()
                || (!normalizedMessage.equals(RESPONSE_YES) && !normalizedMessage.equals(RESPONSE_NO))) {
            return false;
        }

        GroupJoinVote vote = pendingJoinVotes.poll();
        if (vote != null && vote.voterId() == voter.id()) {
            registerJoinVote(vote, normalizedMessage.equals(RESPONSE_YES));
        }

        currentPendingResponse = PendingResponse.NONE;
        promptNextPendingResponse();
        return true;
    }

    public void promptNextPendingResponse() {
        if (currentPendingResponse == PendingResponse.INVITATION) {
            printInvitationPrompt(pendingInvitations.peek());
            return;
        }
        if (currentPendingResponse == PendingResponse.JOIN_VOTE) {
            printJoinVotePrompt(pendingJoinVotes.peek());
            return;
        }

        GroupInvitation invitation = pendingInvitations.peek();
        if (invitation != null) {
            currentPendingResponse = PendingResponse.INVITATION;
            printInvitationPrompt(invitation);
            return;
        }

        GroupJoinVote vote = pendingJoinVotes.peek();
        if (vote != null) {
            currentPendingResponse = PendingResponse.JOIN_VOTE;
            printJoinVotePrompt(vote);
        }
    }

    private boolean containsReservedCharacter(String groupName) {
        return groupName.contains("&")
                || groupName.contains("@")
                || groupName.contains(":")
                || groupName.contains(",");
    }

    private void inviteMember(Group group, User inviter, String receiverUsername) {
        User receiver = UserRepository.findByUsername(receiverUsername);

        if (receiver == null) {
            output.println("Usuario nao encontrado: " + receiverUsername + ".");
        } else if (receiver.id() == inviter.id()) {
            output.println("Voce ja pertence ao grupo " + group.name() + ".");
        } else if (GroupRepository.isMember(group.id(), receiver.id())) {
            output.println(receiver.username() + " ja pertence ao grupo " + group.name() + ".");
        } else {
            GroupInvitation existingInvitation = GroupInvitationRepository.findByGroupAndReceiver(group.id(), receiver.id());

            if (existingInvitation != null
                    && GroupInvitationRepository.STATUS_PENDING.equals(existingInvitation.status())) {
                output.println(receiver.username() + " ja possui um convite pendente para " + group.name() + ".");
            } else {
                GroupInvitation invitation = GroupInvitationRepository.invite(group.id(), inviter.id(), receiver.id());
                if (invitation == null) {
                    output.println("Nao foi possivel convidar " + receiver.username() + ".");
                } else {
                    notifyInvitationReceiver(invitation);
                    output.println("Convite para " + receiver.username() + " enviado.");
                }
            }
        }
    }

    private void acceptInvitation(GroupInvitation invitation, User receiver) {
        if (GroupInvitationRepository.accept(invitation)) {
            output.println("Voce entrou no grupo " + invitation.groupName() + ".");
            notifyInviter(
                    invitation,
                    receiver.username() + " aceitou o convite para " + invitation.groupName() + "."
            );
        } else {
            output.println("Nao foi possivel aceitar o convite para " + invitation.groupName() + ".");
        }
    }

    private void rejectInvitation(GroupInvitation invitation, User receiver) {
        if (GroupInvitationRepository.reject(invitation)) {
            output.println("Convite para o grupo " + invitation.groupName() + " recusado.");
            notifyInviter(invitation, receiver.username() + " recusou o convite para " + invitation.groupName() + ".");
        } else {
            output.println("Nao foi possivel recusar o convite para " + invitation.groupName() + ".");
        }
    }

    private void notifyInvitationReceiver(GroupInvitation invitation) {
        ClientHandler receiverHandler = ChatServer.findOnlineClient(invitation.receiverId());
        if (receiverHandler != null) {
            receiverHandler.receiveGroupInvitation(invitation);
        }
    }

    private void notifyInviter(GroupInvitation invitation, String message) {
        ClientHandler inviterHandler = ChatServer.findOnlineClient(invitation.invitedById());
        if (inviterHandler != null && inviterHandler.sendMessage(message)) {
            return;
        }

        DirectMessageRepository.save(
                invitation.receiverId(),
                invitation.invitedById(),
                message,
                DirectMessageRepository.STATUS_QUEUED
        );
    }

    private void printInvitationPrompt(GroupInvitation invitation) {
        if (invitation != null) {
            output.println(invitation.inviterUsername() + " convidou voce para entrar no grupo " + invitation.groupName() + ".");
            output.println("Aceitar convite do grupo? (sim/nao):");
        }
    }

    private void notifyJoinVoter(GroupJoinRequest request, User voter) {
        ClientHandler voterHandler = ChatServer.findOnlineClient(voter.id());
        if (voterHandler != null) {
            voterHandler.receiveGroupJoinVote(new GroupJoinVote(
                    request.id(),
                    request.groupId(),
                    request.groupName(),
                    request.requesterId(),
                    request.requesterUsername(),
                    voter.id(),
                    GroupJoinRequestRepository.STATUS_PENDING
            ));
        }
    }

    private void registerJoinVote(GroupJoinVote vote, boolean accepted) {
        GroupJoinRequestRepository.VoteResult result = GroupJoinRequestRepository.respond(vote, accepted);
        if (!result.processed() || result.request() == null) {
            output.println("Nao foi possivel registrar sua resposta para o grupo " + vote.groupName() + ".");
            return;
        }

        if (!accepted) {
            output.println("Entrada de " + vote.requesterUsername() + " no grupo " + vote.groupName() + " recusada.");
        } else if (GroupJoinRequestRepository.STATUS_PENDING.equals(result.request().status())) {
            output.println("Sua aprovacao foi registrada. A solicitacao ainda depende dos demais participantes.");
        } else {
            output.println("Entrada de " + vote.requesterUsername() + " no grupo " + vote.groupName() + " aprovada.");
        }

        if (!GroupJoinRequestRepository.STATUS_PENDING.equals(result.request().status())) {
            notifyJoinResolution(result.request());
            cancelRemainingJoinVotes(result.request(), vote.voterId());
        }
    }

    private void notifyJoinResolution(GroupJoinRequest request) {
        ClientHandler requesterHandler = ChatServer.findOnlineClient(request.requesterId());
        if (requesterHandler != null && requesterHandler.sendMessage(formatJoinResolution(request))) {
            GroupJoinRequestRepository.markNotified(request.id());
        }
    }

    private String formatJoinResolution(GroupJoinRequest request) {
        if (GroupJoinRequestRepository.STATUS_ACCEPTED.equals(request.status())) {
            return "Sua entrada no grupo " + request.groupName() + " foi aceita.";
        }

        return "Sua entrada no grupo " + request.groupName() + " foi recusada.";
    }

    private void printJoinVotePrompt(GroupJoinVote vote) {
        if (vote != null) {
            output.println(vote.requesterUsername() + " solicitou entrada no grupo " + vote.groupName() + ".");
            output.println("Aceitar entrada no grupo? (sim/nao):");
        }
    }

    public void cancelJoinVote(int requestId, String groupName, boolean canPromptNext) {
        GroupJoinVote currentVote = pendingJoinVotes.peek();
        boolean wasCurrent = currentPendingResponse == PendingResponse.JOIN_VOTE
                && currentVote != null
                && currentVote.requestId() == requestId;
        boolean removed = pendingJoinVotes.removeIf(vote -> vote.requestId() == requestId);

        if (removed) {
            output.println("A solicitacao de entrada no grupo " + groupName + " ja foi resolvida.");
            if (wasCurrent) {
                currentPendingResponse = PendingResponse.NONE;
                if (canPromptNext) {
                    promptNextPendingResponse();
                }
            }
        }
    }

    private void cancelRemainingJoinVotes(GroupJoinRequest request, int respondingVoterId) {
        for (GroupJoinVote vote : GroupJoinRequestRepository.findVotesForRequest(request.id())) {
            if (vote.voterId() != respondingVoterId) {
                ClientHandler handler = ChatServer.findOnlineClient(vote.voterId());
                if (handler != null) {
                    handler.cancelGroupJoinVote(request.id(), request.groupName());
                }
            }
        }
    }

    private void notifyGroupExit(Group group, User departedMember) {
        String message = "saiu do grupo.";

        for (User remainingMember : GroupRepository.findMembers(group.id())) {
            GroupMessage groupMessage = GroupMessageRepository.save(
                    group.id(),
                    departedMember.id(),
                    remainingMember.id(),
                    message
            );

            if (groupMessage == null) {
                continue;
            }

            ClientHandler handler = ChatServer.findOnlineClient(remainingMember.id());
            if (handler != null && handler.sendMessage(formatGroupMessage(groupMessage))) {
                GroupMessageRepository.markDelivered(groupMessage.id());
            }
        }
    }

    private List<User> findBroadcastReceivers(Group group, User sender) {
        List<User> receivers = new ArrayList<>();

        for (User member : GroupRepository.findMembers(group.id())) {
            if (member.id() != sender.id()) {
                receivers.add(member);
            }
        }

        return receivers;
    }

    private List<User> findSelectedReceivers(Group group, User sender, List<String> usernames) {
        List<User> receivers = new ArrayList<>();
        Set<String> selectedUsernames = new LinkedHashSet<>();

        for (String username : usernames) {
            if (!selectedUsernames.add(username.toLowerCase(Locale.ROOT))) {
                continue;
            }

            User receiver = UserRepository.findByUsername(username);
            if (receiver == null) {
                output.println("Usuario nao encontrado: " + username + ".");
            } else if (receiver.id() == sender.id()) {
                output.println("Voce nao pode enviar mensagem de grupo apenas para si mesmo.");
            } else if (!GroupRepository.isMember(group.id(), receiver.id())) {
                output.println(receiver.username() + " nao pertence ao grupo " + group.name() + ".");
            } else {
                receivers.add(receiver);
            }
        }

        return receivers;
    }

    private void sendGroupMessage(Group group, User sender, User receiver, String content) {
        GroupMessage message = GroupMessageRepository.save(group.id(), sender.id(), receiver.id(), content);

        if (message == null) {
            output.println("Nao foi possivel salvar a mensagem para " + receiver.username() + ".");
            return;
        }

        ClientHandler receiverHandler = ChatServer.findOnlineClient(receiver.id());
        if (receiverHandler != null && receiverHandler.sendMessage(formatGroupMessage(message))) {
            GroupMessageRepository.markDelivered(message.id());
            output.println("Mensagem do grupo " + group.name() + " recebida por " + receiver.username() + ".");
        } else {
            output.println(receiver.username() + " nao esta online. A mensagem do grupo sera entregue depois.");
        }
    }

    private String formatGroupMessage(GroupMessage message) {
        return "&" + message.groupName() + " - " + message.senderUsername()
                + " (" + formatMessageTime(message.createdAt()) + "): " + message.content();
    }

    private String formatMessageTime(String createdAt) {
        if (createdAt != null && createdAt.length() >= 16) {
            return createdAt.substring(11, 16);
        }

        return "--:--";
    }

    private enum PendingResponse {
        NONE,
        INVITATION,
        JOIN_VOTE
    }
}
