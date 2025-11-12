package client;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;

/**
 * GameClient with live board visualization implementation.
 * Handles client-side networking and provides interactive Swing UI for
 * Tic-Tac-Toe.
 */
public class GameClient {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5000;

    // Protocol constants (copied locally to avoid import issues)
    private static final String DELIMITER = " ";
    private static final String JOIN = "JOIN";
    private static final String WELCOME = "WELCOME";
    private static final String START = "START";
    private static final String MOVE = "MOVE";
    private static final String UPDATE = "UPDATE";
    private static final String YOUR_TURN = "YOUR_TURN";
    private static final String OPPONENT_TURN = "OPPONENT_TURN";
    private static final String WIN = "WIN";
    private static final String LOSE = "LOSE";
    private static final String DRAW = "DRAW";
    private static final String FORFEIT = "FORFEIT";
    private static final String SESSION_END = "SESSION_END";
    private static final String GAME_RESET = "GAME_RESET";
    private static final String RESTART = "RESTART";
    private static final String ERROR = "ERROR";
    private static final String GET_SCORES = "GET_SCORES";
    private static final String SCORES = "SCORES";
    private static final String LOG = "LOG";
    private static final String MESSAGE = "MESSAGE";

    // Board constants (copied locally)
    private static final int BOARD_SIZE = 3;
    private static final char EMPTY = ' ';
    private static final char X = 'X';
    private static final char O = 'O';

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private final Object writerLock = new Object();
    private final AtomicBoolean connected = new AtomicBoolean(false);

    // UI Components
    private JFrame mainFrame;
    private JPanel boardPanel;
    private JButton[][] boardButtons;
    private JLabel statusLabel;
    private JLabel playerInfoLabel;
    private JLabel turnIndicatorLabel;
    private JTextField nameField;
    private JButton connectButton;
    private JButton restartButton;
    private JButton leaderboardButton;
    // Chat UI
    private JTextArea chatArea;
    private JTextField chatInput;
    private JButton sendChatButton;

    // Game State
    private String playerName;
    private String assignedSymbol;
    private char assignedSymbolChar = EMPTY;
    private boolean isMyTurn = false;
    private final char[][] gameBoard;
    private String sessionId;
    private int lastMoveRow = -1;
    private int lastMoveCol = -1;

    public GameClient() {
        gameBoard = new char[BOARD_SIZE][BOARD_SIZE];
        initializeBoard();
        initializeUI();
    }

    private void initializeBoard() {
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                gameBoard[i][j] = EMPTY;
            }
        }
    }

    /**
     * Initialize the Swing UI with live board visualization
     */
    private void initializeUI() {
        mainFrame = new JFrame("NetTacToe Client - Live Board");
        // Do a clean disconnect when the window is closed
        mainFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        mainFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // ensure socket and threads are stopped cleanly
                disconnect();
                mainFrame.dispose();
                System.exit(0);
            }
        });
        mainFrame.setLayout(new BorderLayout());

        // Connection panel
        JPanel connectionPanel = createConnectionPanel();
        mainFrame.add(connectionPanel, BorderLayout.NORTH);

        // Game board panel
        boardPanel = createBoardPanel();
        mainFrame.add(boardPanel, BorderLayout.CENTER);

        // Status and info panel
        JPanel infoPanel = createInfoPanel();
        mainFrame.add(infoPanel, BorderLayout.SOUTH);

        // Log panel
        JPanel logPanel = createLogPanel();
        mainFrame.add(logPanel, BorderLayout.EAST);

        mainFrame.setSize(800, 600);
        mainFrame.setLocationRelativeTo(null);
        updateBoardDisplay();
    }

    private JPanel createConnectionPanel() {
        JPanel panel = new JPanel(new FlowLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Connection"));

        panel.add(new JLabel("Name:"));
        nameField = new JTextField(15);
        panel.add(nameField);

        connectButton = new JButton("Connect");
        connectButton.addActionListener(e -> handleConnect());
        panel.add(connectButton);

        restartButton = new JButton("Restart Game");
        restartButton.addActionListener(e -> sendRestart());
        restartButton.setEnabled(false);
        panel.add(restartButton);

        leaderboardButton = new JButton("View Leaderboard");
        leaderboardButton.addActionListener(e -> requestLeaderboard());
        panel.add(leaderboardButton);

        return panel;
    }

    /**
     * Create the interactive 3x3 game board with JButtons
     */
    private JPanel createBoardPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 3, 2, 2));
        panel.setBorder(BorderFactory.createTitledBorder("Game Board"));
        panel.setBackground(Color.BLACK);

        boardButtons = new JButton[BOARD_SIZE][BOARD_SIZE];

        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                JButton button = new JButton();
                button.setFont(new Font("Arial", Font.BOLD, 48));
                button.setPreferredSize(new Dimension(100, 100));
                button.setBackground(Color.WHITE);
                button.setForeground(Color.BLACK); // Ensure text is visible
                button.setOpaque(true); // Make sure background is painted
                button.setEnabled(false);

                final int r = row, c = col;
                button.addActionListener(e -> handleCellClick(r, c));
                // soft hover highlight for empty, enabled buttons
                button.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseEntered(java.awt.event.MouseEvent evt) {
                        if (button.isEnabled() && button.getText().isEmpty()) {
                            button.setBackground(new Color(220, 220, 255)); // soft highlight
                        }
                    }

                    @Override
                    public void mouseExited(java.awt.event.MouseEvent evt) {
                        if (button.isEnabled() && button.getText().isEmpty()) {
                            button.setBackground(Color.WHITE);
                        }
                    }
                });

                boardButtons[row][col] = button;
                panel.add(button);
            }
        }

        return panel;
    }

    private JPanel createInfoPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 1));
        panel.setBorder(BorderFactory.createTitledBorder("Game Info"));

        playerInfoLabel = new JLabel("Not connected");
        playerInfoLabel.setFont(new Font("Arial", Font.BOLD, 14));
        panel.add(playerInfoLabel);

        statusLabel = new JLabel("Connect to start playing");
        statusLabel.setFont(new Font("Arial", Font.BOLD, 13));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(Color.LIGHT_GRAY);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        panel.add(statusLabel);

        // Prominent turn indicator banner
        turnIndicatorLabel = new JLabel("Waiting...", SwingConstants.CENTER);
        turnIndicatorLabel.setFont(new Font("Arial", Font.BOLD, 16));
        turnIndicatorLabel.setOpaque(true);
        turnIndicatorLabel.setBackground(new Color(220, 220, 220));
        turnIndicatorLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        panel.add(turnIndicatorLabel);

        return panel;
    }

    private JPanel createLogPanel() {
        // Right-side chat panel (no local game log)
        JPanel panel = new JPanel(new BorderLayout());
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBorder(BorderFactory.createTitledBorder("Chat (to opponent)"));
        chatArea = new JTextArea(20, 25);
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane chatScroll = new JScrollPane(chatArea);

        JPanel inputRow = new JPanel(new BorderLayout(4, 4));
        chatInput = new JTextField();
        sendChatButton = new JButton("Send");
        sendChatButton.addActionListener(e -> sendChat());
        chatInput.addActionListener(e -> sendChat());
        inputRow.add(chatInput, BorderLayout.CENTER);
        inputRow.add(sendChatButton, BorderLayout.EAST);

        chatPanel.add(chatScroll, BorderLayout.CENTER);
        chatPanel.add(inputRow, BorderLayout.SOUTH);

        panel.add(chatPanel, BorderLayout.CENTER);
        return panel;
    }

    private void sendChat() {
        String text = chatInput.getText();
        if (text == null || text.isBlank() || !connected.get())
            return;
        // Send MESSAGE <text> to server; server will forward to opponent
        sendMessage(String.join(DELIMITER, MESSAGE, text));
        // locally echo
        appendChat(playerName != null ? playerName : "Me", text);
        chatInput.setText("");
    }

    private void appendChat(String sender, String text) {
        SwingUtilities.invokeLater(() -> {
            String time = java.time.LocalTime.now().toString().substring(0, 8);
            chatArea.append("[" + time + "] " + sender + ": " + text + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    /**
     * Handle cell click events for making moves
     */
    private void handleCellClick(int row, int col) {
        if (!connected.get() || !isMyTurn) {
            return;
        }

        if (gameBoard[row][col] != EMPTY) {
            appendLog("Invalid move at (" + row + "," + col + ")");
            return;
        }

        // Optimistically update UI with the player's symbol and disable further moves
        char symbolToPlace = assignedSymbolChar != EMPTY ? assignedSymbolChar
                : (assignedSymbol != null && !assignedSymbol.isEmpty() ? assignedSymbol.charAt(0) : EMPTY);
        if (symbolToPlace != EMPTY) {
            gameBoard[row][col] = symbolToPlace;
            lastMoveRow = row;
            lastMoveCol = col;
            // disable local turn until server confirms
            isMyTurn = false;
            updateBoardDisplay();
        }

        // Send move to server
        sendMessage(String.join(DELIMITER, MOVE, String.valueOf(row), String.valueOf(col)));
        appendLog("Sent move: (" + row + "," + col + ")");
    }

    /**
     * Update the visual board display based on current board state
     */
    private void updateBoardDisplay() {
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                char cell = gameBoard[row][col];
                JButton button = boardButtons[row][col];

                if (cell == EMPTY || cell == ' ') {
                    button.setText("");
                    button.setBackground(Color.WHITE);
                } else {
                    button.setText(String.valueOf(cell));
                    button.setForeground(Color.BLACK); // Ensure text is visible
                    Color bgColor = (cell == X) ? Color.LIGHT_GRAY : Color.CYAN;
                    button.setBackground(bgColor);
                }
                button.repaint(); // Force UI update

                // Highlight last move
                if (row == lastMoveRow && col == lastMoveCol) {
                    button.setBorder(BorderFactory.createLineBorder(Color.RED, 3));
                } else {
                    button.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
                }
            }
        }

        // Enable/disable buttons based on turn and game state
        boolean enableButtons = connected.get() && isMyTurn && !isGameOver();
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                boolean shouldEnable = enableButtons && (gameBoard[row][col] == EMPTY || gameBoard[row][col] == ' ');
                boardButtons[row][col].setEnabled(shouldEnable);
            }
        }
    }

    private boolean isGameOver() {
        return checkWinner() != EMPTY || isBoardFull();
    }

    private char checkWinner() {
        // Check rows
        for (int i = 0; i < BOARD_SIZE; i++) {
            if (gameBoard[i][0] != EMPTY &&
                    gameBoard[i][0] == gameBoard[i][1] &&
                    gameBoard[i][1] == gameBoard[i][2]) {
                return gameBoard[i][0];
            }
        }

        // Check columns
        for (int j = 0; j < BOARD_SIZE; j++) {
            if (gameBoard[0][j] != EMPTY &&
                    gameBoard[0][j] == gameBoard[1][j] &&
                    gameBoard[1][j] == gameBoard[2][j]) {
                return gameBoard[0][j];
            }
        }

        // Check diagonals
        if (gameBoard[0][0] != EMPTY &&
                gameBoard[0][0] == gameBoard[1][1] &&
                gameBoard[1][1] == gameBoard[2][2]) {
            return gameBoard[0][0];
        }

        if (gameBoard[0][2] != EMPTY &&
                gameBoard[0][2] == gameBoard[1][1] &&
                gameBoard[1][1] == gameBoard[2][0]) {
            return gameBoard[0][2];
        }

        return EMPTY;
    }

    private boolean isBoardFull() {
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (gameBoard[i][j] == EMPTY) {
                    return false;
                }
            }
        }
        return true;
    }

    private void handleConnect() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame, "Please enter your name");
            return;
        }

        try {
            socket = new Socket(DEFAULT_HOST, DEFAULT_PORT);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter pw = new PrintWriter(socket.getOutputStream(), true);
            synchronized (writerLock) {
                writer = pw;
            }

            connected.set(true);
            connectButton.setEnabled(false);
            nameField.setEnabled(false);

            // Start message listening thread
            Thread messageThread = new Thread(this::listenForMessages, "message-listener");
            messageThread.setDaemon(true);
            messageThread.start();

            // Send JOIN command
            sendMessage(String.join(DELIMITER, JOIN, name));
            appendLog("Connecting as " + name + "...");

        } catch (IOException e) {
            appendLog("Connection failed: " + e.getMessage());
            connected.set(false);
            connectButton.setEnabled(true);
            nameField.setEnabled(true);
        }
    }

    /**
     * Listen for server messages and handle board updates
     */
    private void listenForMessages() {
        try {
            String message;
            while (connected.get() && (message = reader.readLine()) != null) {
                handleServerMessage(message);
            }
        } catch (IOException e) {
            if (connected.get()) {
                appendLog("Connection lost: " + e.getMessage());
            }
        } finally {
            disconnect();
        }
    }

    /**
     * Handle incoming server messages including board updates
     */
    private void handleServerMessage(String message) {
        // parse and dispatch outside EDT; UI updates will be scheduled on EDT where
        // needed
        appendLog("Received: " + message);

        if (message == null || message.isEmpty()) {
            appendLog("Ignoring empty message");
            return;
        }

        // split into at most 3 parts: COMMAND, payload (may contain spaces), extra
        String[] parts = message.split(DELIMITER, 3);
        if (parts.length == 0) {
            appendLog("Malformed message (no parts): " + message);
            return;
        }

        String command = parts[0];

        try {
            // UI updates must run on the Event Dispatch Thread
            SwingUtilities.invokeLater(() -> {
                try {
                    switch (command) {
                        case WELCOME -> handleWelcome(parts);
                        case START -> handleGameStart(parts);
                        case UPDATE -> handleBoardUpdate(parts);
                        case MESSAGE -> handleChatMessage(parts);
                        case YOUR_TURN -> handleYourTurn();
                        case OPPONENT_TURN -> handleOpponentTurn();
                        case WIN -> handleGameEnd("You Won!", Color.GREEN);
                        case LOSE -> handleGameEnd("You Lost!", Color.RED);
                        case DRAW -> handleGameEnd("Draw!", Color.ORANGE);
                        case FORFEIT -> handleGameEnd("Opponent Forfeited - You Win!", Color.GREEN);
                        case SESSION_END -> handleSessionEnd(parts);
                        case GAME_RESET -> handleGameReset(parts);
                        case SCORES -> handleScores(parts);
                        case ERROR -> handleError(parts);
                        default -> appendLog("Unknown command: " + command);
                    }
                } catch (Exception inner) {
                    appendLog("Error applying server message [" + message + "]: " + inner.getMessage());
                }
            });
        } catch (Exception ex) {
            appendLog("Error handling server message [" + message + "]: " + ex.getMessage());
        }
    }

    private void handleWelcome(String[] parts) {
        if (parts.length > 1) {
            playerName = parts[1];
            playerInfoLabel.setText("Player: " + playerName);
            statusLabel.setText("Waiting for opponent...");
        }
    }

    private void handleGameStart(String[] parts) {
        if (parts.length > 2) {
            assignedSymbol = parts[1];
            // store assigned symbol as char for quick checks
            assignedSymbolChar = assignedSymbol != null && assignedSymbol.length() > 0 ? assignedSymbol.charAt(0)
                    : EMPTY;
            sessionId = parts[2];
            playerInfoLabel.setText("Player: " + playerName + " (" + assignedSymbol + ")");
            statusLabel.setText("Game started! Session: " + sessionId);
            restartButton.setEnabled(true);

            // Reset board for new game
            initializeBoard();
            lastMoveRow = -1;
            lastMoveCol = -1;
            updateBoardDisplay();
        }
    }

    private void handleChatMessage(String[] parts) {
        if (parts.length < 3)
            return; // expected: MESSAGE <sender> <text>
        String sender = parts[1];
        String text = parts[2];
        appendChat(sender, text);
    }

    private void handleBoardUpdate(String[] parts) {
        if (parts.length > 2) {
            // parts[1] contains serialized board state
            String boardData = parts[1];
            deserializeBoard(boardData);

            // parts[2] contains last move info (row,col,symbol)
            if (parts[2].contains(",")) {
                String[] moveInfo = parts[2].split(",");
                if (moveInfo.length == 3) {
                    try {
                        lastMoveRow = Integer.parseInt(moveInfo[0]);
                        lastMoveCol = Integer.parseInt(moveInfo[1]);
                    } catch (NumberFormatException e) {
                        // Invalid move info, ignore
                    }
                }
            }

            // parts[3] (optional) contains the symbol whose turn it is ("X" or "O")
            if (parts.length > 3 && parts[3] != null && !parts[3].isBlank()) {
                String currentTurnSymbol = parts[3].trim();
                if (assignedSymbol != null && assignedSymbol.equals(currentTurnSymbol)) {
                    // It's our turn according to server board update
                    handleYourTurn();
                } else {
                    handleOpponentTurn();
                }
            }

            updateBoardDisplay();
            appendLog("Board updated - Last move: (" + lastMoveRow + "," + lastMoveCol + ")");
        }
    }

    private void deserializeBoard(String serialized) {
        String[] cells = serialized.split(",");

        if (cells.length != BOARD_SIZE * BOARD_SIZE) {
            return;
        }

        int index = 0;
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                String cellStr = cells[index++];
                char symbol = EMPTY;
                if (cellStr != null && !cellStr.isEmpty()) {
                    char c = cellStr.charAt(0);
                    // underscore is the placeholder for empty cells from server
                    if (c == '_' || c == ' ') {
                        symbol = EMPTY;
                    } else {
                        symbol = c;
                    }
                }
                gameBoard[i][j] = symbol;
            }
        }
    }

    private void handleYourTurn() {
        isMyTurn = true;
        statusLabel.setText("Your turn - Click a cell to make your move");
        statusLabel.setForeground(Color.BLACK);
        statusLabel.setBackground(new Color(200, 255, 200)); // pale green
        if (turnIndicatorLabel != null) {
            turnIndicatorLabel.setText("YOUR TURN");
            turnIndicatorLabel.setBackground(new Color(180, 230, 180));
        }
        updateBoardDisplay();
    }

    private void handleOpponentTurn() {
        isMyTurn = false;
        statusLabel.setText("Opponent's turn - Please wait");
        statusLabel.setForeground(Color.BLACK);
        statusLabel.setBackground(new Color(255, 230, 230)); // pale red
        if (turnIndicatorLabel != null) {
            turnIndicatorLabel.setText("Waiting for Opponent");
            turnIndicatorLabel.setBackground(new Color(240, 240, 240));
        }
        updateBoardDisplay();
    }

    private void handleGameEnd(String message, Color color) {
        isMyTurn = false;
        statusLabel.setText(message);
        statusLabel.setForeground(color);
        updateBoardDisplay();

        // Show popup with game result
        JOptionPane.showMessageDialog(mainFrame, message, "Game Over", JOptionPane.INFORMATION_MESSAGE);
    }

    private void handleSessionEnd(String[] parts) {
        if (parts.length > 2) {
            statusLabel.setText("Session ended: " + parts[2]);
            restartButton.setEnabled(false);
        }
    }

    /**
     * Handle game reset message
     */
    private void handleGameReset(String[] parts) {
        initializeBoard();
        lastMoveRow = -1;
        lastMoveCol = -1;
        updateBoardDisplay();
        statusLabel.setText("Game restarted!");
        statusLabel.setForeground(Color.BLUE);

        String message = parts.length > 1 ? parts[1] : "Game restarted";
        appendLog("Game reset: " + message);
    }

    private void handleError(String[] parts) {
        String error = parts.length > 1 ? parts[1] : "Unknown error";
        statusLabel.setText("Error: " + error);
        statusLabel.setForeground(Color.RED);
        // Show a popup so the user immediately notices the error (e.g., "Not your
        // turn")
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(mainFrame,
                error,
                "Server Error",
                JOptionPane.WARNING_MESSAGE));
    }

    private void requestLeaderboard() {
        if (!connected.get()) {
            appendLog("Not connected to server.");
            JOptionPane.showMessageDialog(mainFrame, "Connect to the server first.", "Info",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        sendMessage(GET_SCORES);
    }

    private void handleScores(String[] parts) {
        // Original message format: SCORES <line1>; <line2>; ...
        // Spaces may exist inside each line (timestamp + outcome), so rebuild payload
        // from full raw message.
        String raw = String.join(" ", parts); // includes command again at start
        int firstSpace = raw.indexOf(' ');
        String payload = firstSpace >= 0 ? raw.substring(firstSpace + 1) : "";
        java.util.Map<String, PlayerStats> map = aggregateScores(payload);
        java.util.List<PlayerStats> list = new java.util.ArrayList<>(map.values());
        list.sort((a, b) -> {
            int c = Integer.compare(b.wins, a.wins);
            if (c != 0)
                return c;
            c = Integer.compare(b.draws, a.draws);
            if (c != 0)
                return c;
            c = Integer.compare(b.games(), a.games());
            if (c != 0)
                return c;
            return a.name.compareToIgnoreCase(b.name);
        });

        SwingUtilities.invokeLater(() -> {
            java.util.List<LeaderboardDialog.Row> rows = new java.util.ArrayList<>();
            for (PlayerStats ps : list) {
                rows.add(new LeaderboardDialog.Row(ps.name, ps.wins, ps.losses, ps.draws, ps.games()));
            }
            LeaderboardDialog dlg = new LeaderboardDialog(mainFrame, rows, playerName);
            dlg.setVisible(true);
        });
    }

    private static class PlayerStats {
        final String name;
        int wins;
        int losses;
        int draws;

        PlayerStats(String name) {
            this.name = name;
        }

        int games() {
            return wins + losses + draws;
        }
    }

    private java.util.Map<String, PlayerStats> aggregateScores(String payload) {
        java.util.Map<String, PlayerStats> map = new java.util.HashMap<>();
        if (payload == null || payload.isBlank())
            return map;
        String[] lines = payload.split(";");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty())
                continue;
            String[] parts = trimmed.split("\\|", 4);
            if (parts.length < 4)
                continue;
            String p1 = parts[1].trim();
            String p2 = parts[2].trim();
            String outcome = parts[3].trim();

            PlayerStats s1 = map.computeIfAbsent(p1, PlayerStats::new);
            PlayerStats s2 = map.computeIfAbsent(p2, PlayerStats::new);

            if (outcome.endsWith(" wins")) {
                String winner = outcome.substring(0, outcome.length() - 5).trim();
                if (winner.equals(p1)) {
                    s1.wins++;
                    s2.losses++;
                } else if (winner.equals(p2)) {
                    s2.wins++;
                    s1.losses++;
                }
            } else if (outcome.endsWith(" forfeited")) {
                String forfeiter = outcome.substring(0, outcome.length() - " forfeited".length()).trim();
                if (forfeiter.equals(p1)) {
                    s2.wins++;
                    s1.losses++;
                } else if (forfeiter.equals(p2)) {
                    s1.wins++;
                    s2.losses++;
                }
            } else if (outcome.startsWith("Draw")) {
                // With updated server, p1 and p2 will be actual names for draws
                s1.draws++;
                s2.draws++;
            }
        }
        // Remove synthetic DRAW key if present
        map.remove("DRAW");
        return map;
    }

    private void sendMessage(String message) {
        if (!connected.get())
            return;
        PrintWriter w;
        synchronized (writerLock) {
            w = writer;
        }
        if (w == null)
            return;
        try {
            w.println(message);
            w.flush();
        } catch (Exception e) {
            appendLog("Failed to send message: " + e.getMessage());
        }
    }

    private void sendRestart() {
        sendMessage(RESTART);
        appendLog("Restart requested");
    }

    private void disconnect() {
        connected.set(false);
        try {
            if (socket != null)
                socket.close();
        } catch (IOException e) {
            // Ignore
        }

        SwingUtilities.invokeLater(() -> {
            connectButton.setEnabled(true);
            nameField.setEnabled(true);
            restartButton.setEnabled(false);
            statusLabel.setText("Disconnected");
            statusLabel.setForeground(Color.BLACK);
            playerInfoLabel.setText("Not connected");

            // Disable board
            for (int row = 0; row < BOARD_SIZE; row++) {
                for (int col = 0; col < BOARD_SIZE; col++) {
                    boardButtons[row][col].setEnabled(false);
                }
            }
        });
    }

    private void appendLog(String text) {
        String line = "[" + java.time.LocalTime.now().toString().substring(0, 8) + "] " + text;
        // Print to local console
        System.out.println(line);
        // Forward to server UI if connected
        if (connected.get()) {
            try {
                sendMessage(String.join(DELIMITER, LOG, line));
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }

    public void show() {
        mainFrame.setVisible(true);
    }

    public static void main(String[] args) {
        try {
            // Use your system’s native look
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Failed to set look and feel: " + e.getMessage());
        }

        // Launch your Swing app safely on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            GameClient client = new GameClient();
            client.show();
        });
    }

}