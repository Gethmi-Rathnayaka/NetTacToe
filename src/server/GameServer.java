package server;

import shared.Protocol;
import utils.Scoreboard;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Entry point for the Net-Tac-Toe server component. Handles client connections,
 * matchmaking, and delegates gameplay to {@link GameSession} instances.
 */
public class GameServer {

    private final int port;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<String, ClientHandler> activePlayers = new ConcurrentHashMap<>();
    private final GameSessionManager sessionManager;
    private final ServerUI ui;
    private final Scoreboard scoreboard;

    private ExecutorService clientExecutor;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    public GameServer(int port) {
        this.port = port;
        this.ui = new ServerUI(this);
        this.sessionManager = new GameSessionManager(this);
        Path scoreboardPath = Paths.get("server", "scoreboard.txt");
        this.scoreboard = new Scoreboard(scoreboardPath);
    }

    public static void main(String[] args) {
        GameServer server = new GameServer(5000);
        SwingUtilities.invokeLater(server.getUi()::show);
    }

    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            log("Server already running.");
            return;
        }

        clientExecutor = Executors.newCachedThreadPool();
        acceptThread = new Thread(this::acceptLoop, "accept-loop");
        acceptThread.start();
        ui.setServerStatus("Running on port " + port);
        log("Server started on port " + port + "...");
    }

    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        log("Stopping server...");
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                log("Error closing server socket: " + e.getMessage());
            }
        }

        if (clientExecutor != null) {
            clientExecutor.shutdownNow();
        }

        activePlayers.values().forEach(ClientHandler::shutdown);
        activePlayers.clear();

        sessionManager.getActiveSessionsSnapshot()
                .forEach(session -> session.finish(GameSession.Result.aborted("Server stopped")));

        refreshPlayerView();
        refreshSessionView();

        ui.setServerStatus("Stopped");
    }

    private void acceptLoop() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            this.serverSocket = serverSocket;
            while (running.get()) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket, this);
                clientExecutor.submit(handler);
            }
        } catch (IOException e) {
            if (running.get()) {
                log("Server accept loop error: " + e.getMessage());
            } else {
                log("Server socket closed.");
            }
        } finally {
            stop();
        }
    }

    public void handleMessage(ClientHandler client, String message) {
        if (message == null || message.isBlank()) {
            return;
        }

        String[] parts = message.trim().split(Protocol.DELIMITER);
        String command = parts[0];

        switch (command) {
            case Protocol.JOIN -> handleJoin(client, parts);
            case Protocol.MOVE -> handleMove(client, parts);
            case Protocol.RESTART -> handleRestart(client);
            case Protocol.QUIT -> handleQuit(client);
            case Protocol.PING -> client.send(Protocol.PONG);
            case Protocol.GET_SCORES -> handleScoreRequest(client);
            default -> client.send(String.join(Protocol.DELIMITER, Protocol.ERROR, "Unknown command: " + command));
        }
    }

    private void handleJoin(ClientHandler client, String[] parts) {
        if (parts.length < 2) {
            client.send(String.join(Protocol.DELIMITER, Protocol.ERROR, "JOIN requires name"));
            return;
        }
        String requestedName = parts[1];
        if (requestedName.isBlank()) {
            client.send(String.join(Protocol.DELIMITER, Protocol.ERROR, "Name cannot be blank"));
            return;
        }
        if (activePlayers.containsKey(requestedName)) {
            client.send(String.join(Protocol.DELIMITER, Protocol.ERROR, "Name already taken"));
            return;
        }

        client.setPlayerName(requestedName);
        activePlayers.put(requestedName, client);
        client.send(String.join(Protocol.DELIMITER, Protocol.WELCOME, requestedName));
        log(requestedName + " joined the lobby.");

        refreshPlayerView();
        sessionManager.enqueue(client);
        refreshSessionView();
    }

    private void handleMove(ClientHandler client, String[] parts) {
        if (parts.length < 3) {
            client.send(String.join(Protocol.DELIMITER, Protocol.ERROR, "MOVE requires row and column"));
            return;
        }
        try {
            int row = Integer.parseInt(parts[1]);
            int col = Integer.parseInt(parts[2]);
            sessionManager.handleMove(client, row, col);
        } catch (NumberFormatException ex) {
            client.send(String.join(Protocol.DELIMITER, Protocol.ERROR, "Invalid MOVE coordinates"));
        }
    }

    private void handleRestart(ClientHandler client) {
        GameSession session = client.getCurrentSession();
        if (session == null) {
            client.send(String.join(Protocol.DELIMITER, Protocol.ERROR, "No active session to restart"));
            return;
        }
        // This will be expanded when Member 4 implements board reset logic.
        log("Restart requested by " + client.getSafeName() + " for session " + session.getSessionId());
    }

    private void handleQuit(ClientHandler client) {
        log(client.getSafeName() + " requested quit.");
        sessionManager.handleDisconnect(client);
        client.shutdown();
    }

    private void handleScoreRequest(ClientHandler client) {
        List<String> recent = scoreboard.readRecent(10);
        String payload = recent.stream()
                .collect(Collectors.joining(";"));
        client.send(String.join(Protocol.DELIMITER, Protocol.SCORES, payload));
    }

    public void handleDisconnect(ClientHandler client) {
        if (client == null) {
            return;
        }

        String name = client.getPlayerName();
        if (name != null) {
            activePlayers.remove(name);
            log(name + " disconnected.");
        } else {
            log("Client disconnected: " + client.getSocket().getRemoteSocketAddress());
        }

        sessionManager.handleDisconnect(client);
        refreshPlayerView();
        refreshSessionView();
    }

    public void onMoveRequested(GameSession session, ClientHandler player, int row, int col) {
        // Placeholder until Member 4 implements full board logic.
        log("Move request from " + player.getSafeName() + " at (" + row + "," + col + ") in session " + session.getSessionId());
        ClientHandler opponent = session.getOpponent(player);
        if (opponent != null && opponent.isActive()) {
            opponent.send(String.join(Protocol.DELIMITER, Protocol.UPDATE,
                    player.getAssignedSymbol(), String.valueOf(row), String.valueOf(col)));
            opponent.send(Protocol.YOUR_TURN);
            player.send(Protocol.OPPONENT_TURN);
            session.setNextTurn(opponent);
        }
    }

    public void onSessionCompleted(GameSession session, GameSession.Result result) {
        sessionManager.terminateSession(session);
        log("Session " + session.getSessionId() + " completed: " + result.getSummary());

        Instant endedAt = session.getEndedAt();
        switch (result.getType()) {
            case WIN, FORFEIT -> scoreboard.recordResult(
                    result.getWinner() != null ? result.getWinner().getSafeName() : null,
                    result.getLoser() != null ? result.getLoser().getSafeName() : null,
                    result.getSummary(),
                    endedAt);
            case DRAW -> scoreboard.recordResult("DRAW", "DRAW", result.getSummary(), endedAt);
            case ABORTED -> {
                // no-op
            }
        }

        sessionManager.requeueIfActive(session.getPlayerX());
        sessionManager.requeueIfActive(session.getPlayerO());
        refreshSessionView();
    }

    public void log(String text) {
        ui.appendLog(text);
        System.out.println(text);
    }

    public void refreshPlayerView() {
        ui.updatePlayers(activePlayers.keySet());
    }

    public void refreshSessionView() {
        ui.updateSessions(sessionManager.getActiveSessionsSnapshot());
    }

    public ServerUI getUi() {
        return ui;
    }

    public boolean isRunning() {
        return running.get();
    }
}
