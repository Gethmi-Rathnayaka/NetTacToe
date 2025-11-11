package server;

import common.Board;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;
import shared.Protocol;

/**
 * Represents a single game session between two connected players.
 * <p>
 * NOTE: This class provides only the lifecycle scaffolding.
 * Gameplay logic (board state, win checks, etc.) is expected to be implemented elsewhere.
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
    
    // Game State Management
    private final Board gameBoard = new Board();
    // Use ReentrantLock to allow bounded waiting and avoid indefinite blocking
    private final ReentrantLock boardLock = new ReentrantLock();

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

    // Send initial board state and turn notifications
        broadcastBoardUpdate();
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
    
    /**
     * Broadcast the current board state to both players
     */
    private void broadcastBoardUpdate() {
        String boardData = gameBoard.serialize();
        String lastMoveInfo = gameBoard.getLastMoveRow() + "," + gameBoard.getLastMoveCol() + "," + gameBoard.getLastMoveSymbol();
        // Determine current turn symbol (X or O) if set
        String currentTurnSymbol = "";
        ClientHandler ct = currentTurn.get();
        if (ct != null && ct.getAssignedSymbol() != null) {
            currentTurnSymbol = ct.getAssignedSymbol();
        }

        // Send UPDATE message with board state, last move info, and current-turn symbol
        String updateMessage = String.join(Protocol.DELIMITER,
            Protocol.UPDATE,
            boardData,
            lastMoveInfo,
            currentTurnSymbol);
            
        if (playerX.isActive()) {
            playerX.send(updateMessage);
        }
        if (playerO.isActive()) {
            playerO.send(updateMessage);
        }
    }
    
    /**
     * Get the current board state (for debugging/admin purposes)
     */
    public Board getGameBoard() {
        boardLock.lock();
        try {
            return gameBoard;
        } finally {
            boardLock.unlock();
        }
    }
    
    /**
     * Reset the game board for restart functionality
     */
    public void resetBoard() {
        boardLock.lock();
        try {
            gameBoard.reset();
            currentTurn.set(playerX); // X always starts

            // Send game reset notification
            if (playerX.isActive()) {
                playerX.send(String.join(Protocol.DELIMITER, Protocol.GAME_RESET, "Game restarted"));
            }
            if (playerO.isActive()) {
                playerO.send(String.join(Protocol.DELIMITER, Protocol.GAME_RESET, "Game restarted"));
            }

            broadcastBoardUpdate();

            // Notify players of restart and turn
            if (playerX.isActive()) {
                playerX.send(Protocol.YOUR_TURN);
            }
            if (playerO.isActive()) {
                playerO.send(Protocol.OPPONENT_TURN);
            }
        } finally {
            boardLock.unlock();
        }
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

        // Game State Management Implementation - use a lock with timeout to avoid deadlocks
        boolean locked = false;
        try {
            try {
                locked = boardLock.tryLock(2, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                player.send(String.join(Protocol.DELIMITER, Protocol.ERROR, "Server interrupted"));
                return;
            }

            if (!locked) {
                player.send(String.join(Protocol.DELIMITER, Protocol.ERROR, "Server busy, try again"));
                return;
            }

            // Validate move
            if (!gameBoard.isValidMove(row, col)) {
                player.send(String.join(Protocol.DELIMITER, Protocol.ERROR, "Invalid move"));
                return;
            }

            // Get player's symbol
            char playerSymbol = player.getAssignedSymbol().charAt(0);

            // Make the move
            if (!gameBoard.makeMove(row, col, playerSymbol)) {
                player.send(String.join(Protocol.DELIMITER, Protocol.ERROR, "Move failed"));
                return;
            }

            // Broadcast board update to both players
            broadcastBoardUpdate();

            // Check for game end conditions
            char winner = gameBoard.checkWinner();
            if (winner != Board.EMPTY) {
                // Someone won
                ClientHandler winnerHandler = (winner == playerSymbol) ? player : getOpponent(player);
                ClientHandler loserHandler = (winner == playerSymbol) ? getOpponent(player) : player;

                // Send win/lose messages
                winnerHandler.send(String.join(Protocol.DELIMITER, Protocol.WIN, "You won!"));
                loserHandler.send(String.join(Protocol.DELIMITER, Protocol.LOSE, "You lost!"));

                // Finish the session
                finish(Result.win(winnerHandler, loserHandler, winnerHandler.getSafeName() + " wins"));
                return;
            }

            if (gameBoard.isFull()) {
                // Draw
                playerX.send(String.join(Protocol.DELIMITER, Protocol.DRAW, "Game is a draw"));
                playerO.send(String.join(Protocol.DELIMITER, Protocol.DRAW, "Game is a draw"));
                finish(Result.draw("Draw - Board full"));
                return;
            }

            // Switch turns
            ClientHandler opponent = getOpponent(player);
            if (opponent != null && opponent.isActive()) {
                setNextTurn(opponent);
                opponent.send(Protocol.YOUR_TURN);
                player.send(Protocol.OPPONENT_TURN);
            }

        } finally {
            if (locked) {
                boardLock.unlock();
            }
        }
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

