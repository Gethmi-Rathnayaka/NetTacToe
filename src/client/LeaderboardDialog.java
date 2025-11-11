package client;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/** Simple leaderboard viewer dialog. */
public class LeaderboardDialog extends JDialog {

    public static class Row {
        public final String name;
        public final int wins, losses, draws, games;
        public Row(String name, int wins, int losses, int draws, int games) {
            this.name = name;
            this.wins = wins;
            this.losses = losses;
            this.draws = draws;
            this.games = games;
        }
    }

    public LeaderboardDialog(Window owner, List<Row> rows, String currentPlayer) {
        super(owner, "Leaderboard", ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        String[] cols = {"Player", "Wins", "Losses", "Draws", "Games"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setRowHeight(22);

        // Center numeric columns
        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        for (int c = 1; c < cols.length; c++) {
            table.getColumnModel().getColumn(c).setCellRenderer(center);
        }

        for (Row ps : rows) {
            model.addRow(new Object[]{ps.name, ps.wins, ps.losses, ps.draws, ps.games});
        }

        // Highlight current player row
        if (currentPlayer != null && !currentPlayer.isBlank()) {
            for (int i = 0; i < model.getRowCount(); i++) {
                Object val = model.getValueAt(i, 0);
                if (val != null && currentPlayer.equalsIgnoreCase(String.valueOf(val))) {
                    table.setRowSelectionInterval(i, i);
                    break;
                }
            }
        }

        setLayout(new BorderLayout());
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        btns.add(close);
        add(btns, BorderLayout.SOUTH);

        setSize(520, 420);
        setLocationRelativeTo(owner);
    }
}
