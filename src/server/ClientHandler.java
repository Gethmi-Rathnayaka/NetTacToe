package server;

import shared.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles communication with a single client connection.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final GameServer server;

    private final AtomicBoolean active = new AtomicBoolean(true);
    private BufferedReader in;
    private PrintWriter out;
    private String playerName;
    private String assignedSymbol;
    private volatile GameSession currentSession;

    public ClientHandler(Socket socket, GameServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        server.log("Client connected from " + socket.getRemoteSocketAddress());
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            String line;
            while (active.get() && (line = in.readLine()) != null) {
                server.handleMessage(this, line.trim());
            }

        } catch (IOException e) {
            if (active.get()) {
                server.log("Connection error with client " + getSafeName() + ": " + e.getMessage());
            }
        } finally {
            shutdown();
            server.handleDisconnect(this);
        }
    }

    public void send(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    public void shutdown() {
        if (active.compareAndSet(true, false)) {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException ignored) {
                // ignore
            }
        }
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public String getAssignedSymbol() {
        return assignedSymbol;
    }

    public void setAssignedSymbol(String assignedSymbol) {
        this.assignedSymbol = assignedSymbol;
        send(String.join(Protocol.DELIMITER, Protocol.ASSIGN, assignedSymbol));
    }

    public Socket getSocket() {
        return socket;
    }

    public boolean isActive() {
        return active.get();
    }

    String getSafeName() {
        return playerName != null ? playerName : socket.getRemoteSocketAddress().toString();
    }

    GameSession getCurrentSession() {
        return currentSession;
    }

    void setCurrentSession(GameSession currentSession) {
        this.currentSession = currentSession;
    }
}

