package server;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;

/**
 * Swing-based dashboard for monitoring server state.
 */
public class ServerUI {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final GameServer server;
    private final JFrame frame;
    private final JButton startButton;
    private final JButton stopButton;
    private final DefaultListModel<String> playerListModel;
    private final DefaultTableModel sessionTableModel;
    private final JTextArea logArea;

    public ServerUI(GameServer server) {
        this.server = server;
        this.frame = new JFrame("Net-Tac-Toe Server Dashboard");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        startButton = new JButton("Start Server");
        stopButton = new JButton("Stop Server");
        stopButton.setEnabled(false);

        playerListModel = new DefaultListModel<>();
        sessionTableModel = new DefaultTableModel(new Object[]{"Session", "Started"}, 0);
        logArea = new JTextArea();
        logArea.setEditable(false);

        layoutComponents();
        wireActions();
    }

    private void layoutComponents() {
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controlPanel.add(startButton);
        controlPanel.add(stopButton);

        frame.add(controlPanel, BorderLayout.NORTH);

        JSplitPane horizontalSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        horizontalSplit.setResizeWeight(0.3);
        horizontalSplit.setLeftComponent(buildPlayerPane());
        horizontalSplit.setRightComponent(buildSessionPane());

        JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        verticalSplit.setResizeWeight(0.7);
        verticalSplit.setTopComponent(horizontalSplit);
        verticalSplit.setBottomComponent(buildLogPane());

        frame.add(verticalSplit, BorderLayout.CENTER);

        frame.setSize(new Dimension(900, 600));
        frame.setLocationRelativeTo(null);
    }

    private JScrollPane buildPlayerPane() {
        JList<String> playerList = new JList<>(playerListModel);
        playerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(playerList);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Connected Players"));
        return scrollPane;
    }

    private JScrollPane buildSessionPane() {
        JTable sessionTable = new JTable(sessionTableModel);
        sessionTable.setFillsViewportHeight(true);
        JScrollPane scrollPane = new JScrollPane(sessionTable);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Active Sessions"));
        return scrollPane;
    }

    private JScrollPane buildLogPane() {
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Server Log"));
        return scrollPane;
    }

    private void wireActions() {
        startButton.addActionListener(e -> {
            server.start();
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
        });
        stopButton.addActionListener(e -> {
            server.stop();
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
        });
    }

    public void show() {
        frame.setVisible(true);
    }

    public void setServerStatus(String status) {
        frame.setTitle("Net-Tac-Toe Server Dashboard - " + status);
    }

    public void updatePlayers(Collection<String> players) {
        SwingUtilities.invokeLater(() -> {
            playerListModel.clear();
            players.stream().sorted().forEach(playerListModel::addElement);
        });
    }

    public void updateSessions(List<GameSession> sessions) {
        SwingUtilities.invokeLater(() -> {
            sessionTableModel.setRowCount(0);
            for (GameSession session : sessions) {
                String start = session.getStartedAt() == null
                        ? "pending"
                        : TIME_FORMATTER.format(session.getStartedAt());
                sessionTableModel.addRow(new Object[]{session.toString(), start});
            }
        });
    }

    public void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + System.lineSeparator());
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}

