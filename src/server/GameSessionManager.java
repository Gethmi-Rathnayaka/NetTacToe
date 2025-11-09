package server;

import shared.Protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Coordinates matchmaking and active game sessions on the server.
 */
public class GameSessionManager {

    private final GameServer server;
    private final Queue<ClientHandler> waitingPlayers = new ConcurrentLinkedQueue<>();
    private final List<GameSession> activeSessions =
            Collections.synchronizedList(new ArrayList<>());

    public GameSessionManager(GameServer server) {
        this.server = server;
    }

    public void enqueue(ClientHandler client) {
        if (client == null || !client.isActive()) {
            return;
        }
        if (waitingPlayers.contains(client)) {
            return;
        }
        waitingPlayers.offer(client);
        client.send(Protocol.WAITING);
        tryStartNextSession();
    }

    public void removeFromQueue(ClientHandler client) {
        waitingPlayers.remove(client);
    }

    public List<GameSession> getActiveSessionsSnapshot() {
        synchronized (activeSessions) {
            return List.copyOf(activeSessions);
        }
    }

    public void handleMove(ClientHandler client, int row, int col) {
        findSessionByClient(client).ifPresentOrElse(
                session -> session.handleMove(client, row, col),
                () -> client.send(String.join(Protocol.DELIMITER, Protocol.ERROR, "Not in an active session")));
    }

    public void handleDisconnect(ClientHandler client) {
        removeFromQueue(client);
        findSessionByClient(client).ifPresent(session -> {
            if (session.getState() == GameSession.SessionState.PENDING) {
                session.finish(GameSession.Result.aborted(client.getSafeName() + " left before start"));
            } else {
                session.onPlayerDisconnected(client);
            }
        });
    }

    public Optional<GameSession> findSessionByClient(ClientHandler client) {
        synchronized (activeSessions) {
            return activeSessions.stream()
                    .filter(session -> session.involves(client))
                    .findFirst();
        }
    }

    private void tryStartNextSession() {
        ClientHandler first = waitingPlayers.poll();
        ClientHandler second = waitingPlayers.poll();
        if (first == null || second == null) {
            if (first != null) {
                waitingPlayers.offer(first);
            }
            return;
        }

        GameSession session = createSession(first, second);
        synchronized (activeSessions) {
            activeSessions.add(session);
        }

        server.log("Matchmaking: " + first.getSafeName() + " vs " + second.getSafeName());
        new Thread(session, "session-" + session.getSessionId()).start();
        server.refreshSessionView();
    }

    private GameSession createSession(ClientHandler playerA, ClientHandler playerB) {
        return new GameSession(UUID.randomUUID().toString(), playerA, playerB, server);
    }

    public void terminateSession(GameSession session) {
        synchronized (activeSessions) {
            activeSessions.remove(session);
        }
        server.refreshSessionView();
    }

    public void requeueIfActive(ClientHandler client) {
        if (client != null && client.isActive()) {
            enqueue(client);
        }
    }

    public String describeSession(GameSession session) {
        return session.toString() + " (" + session.getSessionId().substring(0, 8) + ")";
    }
}

