package common;

import java.util.Arrays;

/**
 * Represents the game board state for Tic-Tac-Toe.
 * Handles move validation, board updates, and win/draw detection.
 * Implementation for game state management.
 */
public class Board {
    public static final int SIZE = 3;
    public static final char EMPTY = ' ';
    public static final char X = 'X';
    public static final char O = 'O';

    private final char[][] grid;
    private int moveCount;
    private char lastMoveSymbol;
    private int lastMoveRow;
    private int lastMoveCol;

    public Board() {
        this.grid = new char[SIZE][SIZE];
        this.moveCount = 0;
        this.lastMoveSymbol = EMPTY;
        this.lastMoveRow = -1;
        this.lastMoveCol = -1;
        initializeBoard();
    }

    /**
     * Initialize the board with empty spaces
     */
    private void initializeBoard() {
        for (int i = 0; i < SIZE; i++) {
            Arrays.fill(grid[i], EMPTY);
        }
    }

    /**
     * Check if a move is valid
     */
    public boolean isValidMove(int row, int col) {
        return row >= 0 && row < SIZE &&
                col >= 0 && col < SIZE &&
                grid[row][col] == EMPTY;
    }

    /**
     * Make a move on the board
     */
    public boolean makeMove(int row, int col, char symbol) {
        if (!isValidMove(row, col)) {
            return false;
        }

        grid[row][col] = symbol;
        lastMoveSymbol = symbol;
        lastMoveRow = row;
        lastMoveCol = col;
        moveCount++;
        return true;
    }

    /**
     * Check if there's a winner
     */
    public char checkWinner() {
        // Check rows
        for (int i = 0; i < SIZE; i++) {
            if (grid[i][0] != EMPTY &&
                    grid[i][0] == grid[i][1] &&
                    grid[i][1] == grid[i][2]) {
                return grid[i][0];
            }
        }

        // Check columns
        for (int j = 0; j < SIZE; j++) {
            if (grid[0][j] != EMPTY &&
                    grid[0][j] == grid[1][j] &&
                    grid[1][j] == grid[2][j]) {
                return grid[0][j];
            }
        }

        // Check diagonals
        if (grid[0][0] != EMPTY &&
                grid[0][0] == grid[1][1] &&
                grid[1][1] == grid[2][2]) {
            return grid[0][0];
        }

        if (grid[0][2] != EMPTY &&
                grid[0][2] == grid[1][1] &&
                grid[1][1] == grid[2][0]) {
            return grid[0][2];
        }

        return EMPTY; // No winner
    }

    /**
     * Check if the board is full (draw condition)
     */
    public boolean isFull() {
        return moveCount >= SIZE * SIZE;
    }

    /**
     * Check if the game is over (win or draw)
     */
    public boolean isGameOver() {
        return checkWinner() != EMPTY || isFull();
    }

    /**
     * Get the current state of a cell
     */
    public char getCell(int row, int col) {
        if (row >= 0 && row < SIZE && col >= 0 && col < SIZE) {
            return grid[row][col];
        }
        return EMPTY;
    }

    /**
     * Serialize the board to a string for network transmission
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                // use '_' as a clear placeholder for empty cells to avoid
                // ambiguity in text-based protocol (spaces break tokenization)
                sb.append(grid[i][j] == EMPTY ? '_' : grid[i][j]);
                if (i < SIZE - 1 || j < SIZE - 1) {
                    sb.append(",");
                }
            }
        }
        return sb.toString();
    }

    /**
     * Deserialize a board from a string
     */
    public static Board deserialize(String serialized) {
        Board board = new Board();
        String[] cells = serialized.split(",");

        if (cells.length != SIZE * SIZE) {
            return board; // Return empty board if invalid
        }

        int index = 0;
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                char symbol = cells[index++].charAt(0);
                // treat '_' or space as empty
                if (symbol == X || symbol == O) {
                    board.grid[i][j] = symbol;
                    board.moveCount++;
                } else {
                    board.grid[i][j] = EMPTY;
                }
            }
        }

        return board;
    }

    /**
     * Reset the board to initial state
     */
    public void reset() {
        initializeBoard();
        moveCount = 0;
        lastMoveSymbol = EMPTY;
        lastMoveRow = -1;
        lastMoveCol = -1;
    }

    /**
     * Get a copy of the current board state
     */
    public char[][] getGridCopy() {
        char[][] copy = new char[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++) {
            System.arraycopy(grid[i], 0, copy[i], 0, SIZE);
        }
        return copy;
    }

    // Getters for last move information
    public char getLastMoveSymbol() {
        return lastMoveSymbol;
    }

    public int getLastMoveRow() {
        return lastMoveRow;
    }

    public int getLastMoveCol() {
        return lastMoveCol;
    }

    public int getMoveCount() {
        return moveCount;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("  0 1 2\n");
        for (int i = 0; i < SIZE; i++) {
            sb.append(i).append(" ");
            for (int j = 0; j < SIZE; j++) {
                sb.append(grid[i][j]);
                if (j < SIZE - 1)
                    sb.append("|");
            }
            sb.append("\n");
            if (i < SIZE - 1)
                sb.append("  -----\n");
        }
        return sb.toString();
    }

}
