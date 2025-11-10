package test;

import common.Board;

/**
 * Simple test class to verify Member 4's Board implementation works correctly
 */
public class BoardTest {
    
    public static void main(String[] args) {
        System.out.println("=== Testing Member 4 Board Implementation ===");
        
        // Test 1: Basic Board Creation
        Board board = new Board();
        System.out.println("✓ Board created successfully");
        System.out.println("Initial board:");
        System.out.println(board.toString());
        
        // Test 2: Valid Move
        boolean moveSuccess = board.makeMove(0, 0, Board.X);
        System.out.println("✓ Move X to (0,0): " + moveSuccess);
        
        // Test 3: Invalid Move (same position)
        boolean invalidMove = board.makeMove(0, 0, Board.O);
        System.out.println("✓ Invalid move to (0,0): " + !invalidMove);
        
        // Test 4: Valid Move O
        board.makeMove(1, 1, Board.O);
        System.out.println("✓ Move O to (1,1)");
        System.out.println("Board after moves:");
        System.out.println(board.toString());
        
        // Test 5: Serialization
        String serialized = board.serialize();
        System.out.println("✓ Serialized board: " + serialized);
        
        // Test 6: Deserialization
        Board newBoard = Board.deserialize(serialized);
        System.out.println("✓ Deserialized board:");
        System.out.println(newBoard.toString());
        
        // Test 7: Win Condition
        Board winTestBoard = new Board();
        winTestBoard.makeMove(0, 0, Board.X); // X at (0,0)
        winTestBoard.makeMove(0, 1, Board.X); // X at (0,1) 
        winTestBoard.makeMove(0, 2, Board.X); // X at (0,2) - should win
        
        char winner = winTestBoard.checkWinner();
        System.out.println("✓ Win test - Winner: " + (winner == Board.X ? "X" : "None"));
        System.out.println("Winning board:");
        System.out.println(winTestBoard.toString());
        
        // Test 8: Draw Condition
        Board drawBoard = new Board();
        // Fill board without winning
        drawBoard.makeMove(0, 0, Board.X); // X
        drawBoard.makeMove(0, 1, Board.O); // O
        drawBoard.makeMove(0, 2, Board.X); // X
        drawBoard.makeMove(1, 0, Board.O); // O
        drawBoard.makeMove(1, 1, Board.X); // X
        drawBoard.makeMove(1, 2, Board.O); // O
        drawBoard.makeMove(2, 0, Board.O); // O
        drawBoard.makeMove(2, 1, Board.X); // X
        drawBoard.makeMove(2, 2, Board.O); // O
        
        System.out.println("✓ Draw test - Is full: " + drawBoard.isFull());
        System.out.println("✓ Draw test - Winner: " + (drawBoard.checkWinner() == Board.EMPTY ? "None" : "Someone"));
        System.out.println("Draw board:");
        System.out.println(drawBoard.toString());
        
        System.out.println("\n=== All Board Tests Completed Successfully! ===");
    }
}