package sessionmanager;

import server.GameServer;
import client.GameClient;
import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A small GUI to start/stop the existing GameServer and spawn GameClient
 * windows.
 * This is specific to the Tic-Tac-Toe game in this repo.
 */
public class GameSessionManager {
    private final JFrame frame;
    private final JButton startServerBtn;
    private final JButton stopServerBtn;
    private final JButton createClientBtn;
    private final JTextArea logArea;

    private GameServer server;
    private final AtomicInteger clientCounter = new AtomicInteger(1);

    public GameSessionManager() {
        frame = new JFrame("NetTacToe - Session Manager");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        startServerBtn = new JButton("Start Server");
        stopServerBtn = new JButton("Stop Server");
        createClientBtn = new JButton("Create Client");
        stopServerBtn.setEnabled(false);

        top.add(startServerBtn);
        top.add(stopServerBtn);
        top.add(createClientBtn);
        frame.add(top, BorderLayout.NORTH);

        logArea = new JTextArea(20, 60);
        logArea.setEditable(false);
        frame.add(new JScrollPane(logArea), BorderLayout.CENTER);

        startServerBtn.addActionListener(e -> startServer());
        stopServerBtn.addActionListener(e -> stopServer());
        createClientBtn.addActionListener(e -> createClient());

        frame.pack();
        frame.setLocationRelativeTo(null);
    }

    private void log(String s) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(s + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void startServer() {
        if (server != null && server.isRunning())
            return;
        server = new GameServer(5000);
        // start server in background thread (server.start shows its own UI)
        new Thread(server::start, "embedded-gameserver").start();
        startServerBtn.setEnabled(false);
        stopServerBtn.setEnabled(true);
        log("Server started (embedded)");
    }

    private void stopServer() {
        if (server == null)
            return;
        server.stop();
        startServerBtn.setEnabled(true);
        stopServerBtn.setEnabled(false);
        log("Server stopped");
        server = null;
    }

    private void createClient() {
        String defaultName = "Client" + clientCounter.getAndIncrement();
        String name = (String) JOptionPane.showInputDialog(
                frame,
                "Enter player name:",
                "New Client",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                defaultName);
        if (name == null) {
            log("Client creation cancelled");
            return;
        }
        name = name.trim();
        if (name.isEmpty()) {
            name = defaultName;
        }
        try {
            GameClient client = new GameClient();
            client.show();
            // programmatically connect using supplied name; server will pair clients
            boolean ok = client.connectToServer(name);
            log("Created client " + name + " - connect=" + ok);
        } catch (Exception ex) {
            log("Failed to create client: " + ex.getMessage());
        }
    }

    public void show() {
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GameSessionManager().show());
    }
}
