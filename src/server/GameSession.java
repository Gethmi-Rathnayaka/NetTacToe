package server;

import shared.Protocol;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a single game session between two connected players.
 * <p>
 * NOTE: This class provides only the lifecycle scaffolding. Member 4
 * is expected to extend the gameplay logic (board state, win checks, etc.).
 */
public class GameSession implements Runnable {

    private final String sessionId;
    private final ClientHandler playerX;
    private final ClientHandler playerO;
    private final GameServer server;
    private final AtomicReference<SessionState> state = new AtomicReference<>(SessionState.PENDING);
    private final AtomicReference<ClientHandler> currentTurn = new AtomicReference<>();
    private final AtomicBoolean active = new AtomicBoolean(false);
    private volatile Instant startedAt;
    private volatile Instant endedAt;

    public GameSession(String sessionId, ClientHandler playerX, ClientHandler playerO, GameServer server) {
        this.sessionId = sessionId;
        this.playerX = playerX;
        this.playerO = playerO;
        this.server = server;
    }

    @Override
    public void run() {
        if (!active.compareAndSet(false, true)) {
            return;
        }

        startedAt = Instant.now();
        playerX.setAssignedSymbol(Protocol.SYMBOL_X);
        playerO.setAssignedSymbol(Protocol.SYMBOL_O);

        playerX.send(String.join(Protocol.DELIMITER, Protocol.START, Protocol.SYMBOL_X, getSessionId()));
        playerO.send(String.join(Protocol.DELIMITER, Protocol.START, Protocol.SYMBOL_O, getSessionId()));

        server.log("Session " + sessionId + " started between "
                + playerX.getSafeName() + " and " + playerO.getSafeName());
        state.set(SessionState.ACTIVE);

        playerX.setCurrentSession(this);
        playerO.setCurrentSession(this);
        currentTurn.set(playerX);
        server.refreshSessionView();

        // Placeholder notification for Member 3 & 4.
        playerX.send(Protocol.YOUR_TURN);
        playerO.send(Protocol.OPPONENT_TURN);
    }

    public String getSessionId() {
        return sessionId;
    }

    public ClientHandler getPlayerX() {
        return playerX;
    }

    public ClientHandler getPlayerO() {
        return playerO;
    }

    public boolean involves(ClientHandler handler) {
        return handler == playerX || handler == playerO;
    }

    public ClientHandler getOpponent(ClientHandler handler) {
        if (handler == playerX) {
            return playerO;
        }
        if (handler == playerO) {
            return playerX;
        }
        return null;
    }

    public boolean isActive() {
        return active.get();
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public SessionState getState() {
        return state.get();
    }

    public ClientHandler getCurrentTurn() {
        return currentTurn.get();
    }

    public void setNextTurn(ClientHandler next) {
        currentTurn.set(next);
    }

    public void handleMove(ClientHandler player, int row, int col) {
        if (!active.get()) {
            player.send(String.join(Protocol.DELIMITER, Protocol.ERROR, "Session inactive"));
            return;
        }
        if (player != currentTurn.get()) {
            player.send(String.join(Protocol.DELIMITER, Protocol.ERROR, "Not your turn"));
            return;
        }

        // Delegate to Member 4's gameplay implementation.
        server.onMoveRequested(this, player, row, col);
    }

    public void onPlayerDisconnected(ClientHandler player) {
        if (!active.get()) {
            return;
        }
        ClientHandler opponent = getOpponent(player);
        if (opponent != null && opponent.isActive()) {
            opponent.send(String.join(Protocol.DELIMITER, Protocol.FORFEIT, player.getSafeName()));
        }
        finish(Result.forfeit(opponent, player));
    }

    public void finish(Result result) {
        boolean transitioned = state.compareAndSet(SessionState.ACTIVE, SessionState.COMPLETED)
                || state.compareAndSet(SessionState.PENDING, SessionState.COMPLETED);
        if (!transitioned) {
            return;
        }
        active.set(false);
        endedAt = Instant.now();

        playerX.setCurrentSession(null);
        playerO.setCurrentSession(null);

        if (playerX.isActive()) {
            playerX.send(String.join(Protocol.DELIMITER,
                    Protocol.SESSION_END,
                    result.getType().name(),
                    result.getSummary()));
        }
        if (playerO.isActive()) {
            playerO.send(String.join(Protocol.DELIMITER,
                    Protocol.SESSION_END,
                    result.getType().name(),
                    result.getSummary()));
        }

        server.onSessionCompleted(this, result);
    }

    @Override
    public String toString() {
        return playerX.getSafeName() + " vs " + playerO.getSafeName();
    }

    public enum SessionState {
        PENDING,
        ACTIVE,
        COMPLETED
    }

    public enum ResultType {
        WIN,
        DRAW,
        FORFEIT,
        ABORTED
    }

    public static final class Result {
        private final ResultType type;
        private final ClientHandler winner;
        private final ClientHandler loser;
        private final String summary;

        private Result(ResultType type, ClientHandler winner, ClientHandler loser, String summary) {
            this.type = type;
            this.winner = winner;
            this.loser = loser;
            this.summary = summary != null ? summary : type.name();
        }

        public static Result win(ClientHandler winner, ClientHandler loser, String summary) {
            return new Result(ResultType.WIN, winner, loser, summary);
        }

        public static Result draw(String summary) {
            return new Result(ResultType.DRAW, null, null, summary);
        }

        public static Result forfeit(ClientHandler winner, ClientHandler forfeiter) {
            String text = forfeiter != null
                    ? forfeiter.getSafeName() + " forfeited"
                    : "Forfeit";
            return new Result(ResultType.FORFEIT, winner, forfeiter, text);
        }

        public static Result aborted(String summary) {
            return new Result(ResultType.ABORTED, null, null, summary);
        }

        public ResultType getType() {
            return type;
        }

        public ClientHandler getWinner() {
            return winner;
        }

        public ClientHandler getLoser() {
            return loser;
        }

        public String getSummary() {
            return summary;
        }
    }
}

