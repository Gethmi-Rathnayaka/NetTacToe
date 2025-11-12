package shared;

/**
 * Defines the command keywords and protocol constants used
 * for communication between the server and clients.
 */
public final class Protocol {

    private Protocol() {
        // utility
    }

    public static final String DELIMITER = " ";

    // Connection / lifecycle
    public static final String JOIN = "JOIN";
    public static final String WELCOME = "WELCOME";
    public static final String ASSIGN = "ASSIGN";
    public static final String WAITING = "WAITING";
    public static final String START = "START";
    public static final String QUIT = "QUIT";
    public static final String ERROR = "ERROR";
    public static final String PING = "PING";
    public static final String PONG = "PONG";

    // Gameplay
    public static final String MOVE = "MOVE";
    public static final String UPDATE = "UPDATE";
    public static final String YOUR_TURN = "YOUR_TURN";
    public static final String OPPONENT_TURN = "OPPONENT_TURN";
    public static final String WIN = "WIN";
    public static final String LOSE = "LOSE";
    public static final String DRAW = "DRAW";
    public static final String FORFEIT = "FORFEIT";
    public static final String SESSION_END = "SESSION_END";
    public static final String MESSAGE = "MESSAGE";
    public static final String LOG = "LOG";
    public static final String RESTART = "RESTART";
    public static final String BOARD_STATE = "BOARD_STATE";
    public static final String GAME_RESET = "GAME_RESET";

    // Persistence / utilities
    public static final String GET_SCORES = "GET_SCORES";
    public static final String SCORES = "SCORES";

    // Symbols
    public static final String SYMBOL_X = "X";
    public static final String SYMBOL_O = "O";
}
