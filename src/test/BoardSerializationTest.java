package test;

import common.Board;

public class BoardSerializationTest {
    public static void main(String[] args) {
        System.out.println("=== Testing Board Serialization ===");
        
        // Create empty board
        Board board = new Board();
        System.out.println("Empty board serialized: '" + board.serialize() + "'");
        
        // Make some moves
        board.makeMove(0, 0, 'X');
        System.out.println("After X at (0,0): '" + board.serialize() + "'");
        
        board.makeMove(1, 1, 'O');
        System.out.println("After O at (1,1): '" + board.serialize() + "'");
        
        // Test deserialization
        String serialized = board.serialize();
        Board newBoard = Board.deserialize(serialized);
        System.out.println("Deserialized board:");
        System.out.println(newBoard.toString());
        
        // Test what happens with each cell
        System.out.println("\nAnalyzing serialization:");
        String[] cells = serialized.split(",");
        for (int i = 0; i < cells.length; i++) {
            System.out.println("Cell " + i + ": '" + cells[i] + "' (length: " + cells[i].length() + ")");
        }
    }
}