package test;

public class ClientDeserializationTest {
    private static final int BOARD_SIZE = 3;
    private static final char EMPTY = ' ';
    
    private static final char[][] gameBoard = new char[BOARD_SIZE][BOARD_SIZE];
    
    public static void main(String[] args) {
        System.out.println("=== Testing Client Deserialization ===");
        
        // Initialize board
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                gameBoard[i][j] = EMPTY;
            }
        }
        
        // Test with the actual serialization format from Board
        String testSerialization = "X, , , ,O, , , , ";
        System.out.println("Testing with: '" + testSerialization + "'");
        
        deserializeBoard(testSerialization);
        
        System.out.println("Resulting board:");
        printBoard();
        
        // Test what each cell contains
        System.out.println("\nBoard analysis:");
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                char cell = gameBoard[i][j];
                System.out.println("Board[" + i + "][" + j + "] = '" + cell + "' (ASCII: " + (int)cell + ")");
            }
        }
    }
    
    private static void deserializeBoard(String serialized) {
        System.out.println("DEBUG: Deserializing board: " + serialized);
        String[] cells = serialized.split(",");
        System.out.println("DEBUG: Split into " + cells.length + " cells");
        
        if (cells.length != BOARD_SIZE * BOARD_SIZE) {
            System.out.println("DEBUG: Invalid board size: " + cells.length + " expected: " + (BOARD_SIZE * BOARD_SIZE));
            return;
        }
        
        int index = 0;
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                String cellStr = cells[index++];
                char symbol = cellStr.isEmpty() ? EMPTY : cellStr.charAt(0);
                gameBoard[i][j] = symbol;
                System.out.println("DEBUG: Set board[" + i + "][" + j + "] = '" + symbol + "' from '" + cellStr + "'");
            }
        }
    }
    
    private static void printBoard() {
        System.out.println("  0 1 2");
        for (int i = 0; i < BOARD_SIZE; i++) {
            System.out.print(i + " ");
            for (int j = 0; j < BOARD_SIZE; j++) {
                System.out.print(gameBoard[i][j]);
                if (j < BOARD_SIZE - 1) System.out.print("|");
            }
            System.out.println();
            if (i < BOARD_SIZE - 1) System.out.println("  -----");
        }
    }
}