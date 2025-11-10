// package client;

import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;

/**
 * GameClient with live board visualization implementation for Member 4.
 * Handles client-side networking and provides interactive Swing UI for Tic-Tac-Toe.
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
    
    // Board constants (copied locally)
    private static final int BOARD_SIZE = 3;
    private static final char EMPTY = ' ';
    private static final char X = 'X';
    private static final char O = 'O';
    
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    
    // UI Components
    private JFrame mainFrame;
    private JPanel boardPanel;
    private JButton[][] boardButtons;
    private JLabel statusLabel;
    private JLabel playerInfoLabel;
    private JTextField nameField;
    private JButton connectButton;
    private JButton restartButton;
    private JTextArea logArea;
    
    // Game State
    private String playerName;
    private String assignedSymbol;
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
     * Member 4: Initialize the Swing UI with live board visualization
     */
    private void initializeUI() {
        mainFrame = new JFrame("NetTacToe Client - Live Board");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
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
        
        return panel;
    }
    
    /**
     * Member 4: Create the interactive 3x3 game board with JButtons
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
                
                boardButtons[row][col] = button;
                panel.add(button);
            }
        }
        
        return panel;
    }
    
    private JPanel createInfoPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 1));
        panel.setBorder(BorderFactory.createTitledBorder("Game Info"));
        
        playerInfoLabel = new JLabel("Not connected");
        playerInfoLabel.setFont(new Font("Arial", Font.BOLD, 14));
        panel.add(playerInfoLabel);
        
        statusLabel = new JLabel("Connect to start playing");
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        panel.add(statusLabel);
        
        return panel;
    }
    
    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Game Log"));
        
        logArea = new JTextArea(20, 25);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 10));
        JScrollPane scrollPane = new JScrollPane(logArea);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Member 4: Handle cell click events for making moves
     */
    private void handleCellClick(int row, int col) {
        if (!connected.get() || !isMyTurn) {
            return;
        }
        
        if (gameBoard[row][col] != EMPTY) {
            appendLog("Invalid move at (" + row + "," + col + ")");
            return;
        }
        
        // Send move to server
        sendMessage(String.join(DELIMITER, MOVE, String.valueOf(row), String.valueOf(col)));
        appendLog("Sent move: (" + row + "," + col + ")");
    }
    
    /**
     * Member 4: Update the visual board display based on current board state
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
            writer = new PrintWriter(socket.getOutputStream(), true);
            
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
     * Member 4: Listen for server messages and handle board updates
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
     * Member 4: Handle incoming server messages including board updates
     */
    private void handleServerMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            appendLog("Received: " + message);
            
            String[] parts = message.split(DELIMITER);
            String command = parts[0];
            
            switch (command) {
                case WELCOME -> handleWelcome(parts);
                case START -> handleGameStart(parts);
                case UPDATE -> handleBoardUpdate(parts);
                case YOUR_TURN -> handleYourTurn();
                case OPPONENT_TURN -> handleOpponentTurn();
                case WIN -> handleGameEnd("You Won!", Color.GREEN);
                case LOSE -> handleGameEnd("You Lost!", Color.RED);
                case DRAW -> handleGameEnd("Draw!", Color.ORANGE);
                case FORFEIT -> handleGameEnd("Opponent Forfeited - You Win!", Color.GREEN);
                case SESSION_END -> handleSessionEnd(parts);
                case GAME_RESET -> handleGameReset(parts);
                case ERROR -> handleError(parts);
                default -> appendLog("Unknown command: " + command);
            }
        });
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
    
    /**
     * Member 4: Handle board update messages from server
     */
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
                char symbol = cellStr.isEmpty() ? EMPTY : cellStr.charAt(0);
                gameBoard[i][j] = symbol;
            }
        }
    }
    
    private void handleYourTurn() {
        isMyTurn = true;
        statusLabel.setText("Your turn - Click a cell to make your move");
        statusLabel.setForeground(Color.BLUE);
        updateBoardDisplay();
    }
    
    private void handleOpponentTurn() {
        isMyTurn = false;
        statusLabel.setText("Opponent's turn - Please wait");
        statusLabel.setForeground(Color.RED);
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
     * Member 4: Handle game reset message
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
    }
    
    private void sendMessage(String message) {
        if (connected.get() && writer != null) {
            writer.println(message);
        }
    }
    
    private void sendRestart() {
        sendMessage(RESTART);
        appendLog("Restart requested");
    }
    
    private void disconnect() {
        connected.set(false);
        try {
            if (socket != null) socket.close();
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
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + java.time.LocalTime.now().toString().substring(0, 8) + "] " + text + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
    
    public void show() {
        mainFrame.setVisible(true);
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Use default look and feel
            GameClient client = new GameClient();
            client.show();
        });
    }
}