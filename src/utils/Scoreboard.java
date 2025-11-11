package utils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Simple scoreboard utility that appends match summaries to a text file
 * and returns recent results. Member 5 can extend this to richer formats.
 */
public class Scoreboard {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    private final Path filePath;

    public Scoreboard(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        ensureFileExists();
    }

    private void ensureFileExists() {
        try {
            if (filePath.getParent() != null && Files.notExists(filePath.getParent())) {
                Files.createDirectories(filePath.getParent());
            }
            if (Files.notExists(filePath)) {
                Files.createFile(filePath);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to initialize scoreboard file at " + filePath, e);
        }
    }

    public synchronized void recordResult(String winner, String loser, String outcome, Instant completedAt) {
        String timestamp = FORMATTER.format(completedAt != null ? completedAt : Instant.now());
        String sanitizedWinner = winner != null ? winner : "N/A";
        String sanitizedLoser = loser != null ? loser : "N/A";
        String sanitizedOutcome = outcome != null ? outcome : "UNKNOWN";

        String line = String.join("|", timestamp, sanitizedWinner, sanitizedLoser, sanitizedOutcome);
        try (BufferedWriter writer = Files.newBufferedWriter(
                filePath,
                StandardCharsets.UTF_8,
                StandardOpenOption.APPEND)) {
            writer.write(line);
            writer.newLine();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to record result to scoreboard", e);
        }
    }

    public synchronized List<String> readRecent(int limit) {
        List<String> all;
        try {
            all = Files.readAllLines(filePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return Collections.emptyList();
        }
        if (limit <= 0 || all.size() <= limit) {
            return List.copyOf(all);
        }
        return List.copyOf(all.subList(Math.max(all.size() - limit, 0), all.size()));
    }

    /** Parsed aggregate stats for a player */
    public static final class PlayerStats {
        public final String name;
        public int wins;
        public int losses;
        public int draws;
        public PlayerStats(String name) { this.name = name; }
        public int games() { return wins + losses + draws; }
    }

    /** Compute leaderboard map from all lines in the scoreboard file */
    public synchronized Map<String, PlayerStats> computeLeaderboard() {
        Map<String, PlayerStats> map = new HashMap<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return map;
        }

        for (String line : lines) {
            String[] parts = line.split("\\|", 4);
            if (parts.length < 4) continue;
            String p1 = parts[1].trim();
            String p2 = parts[2].trim();
            String outcome = parts[3].trim();

            PlayerStats s1 = map.computeIfAbsent(p1, PlayerStats::new);
            PlayerStats s2 = map.computeIfAbsent(p2, PlayerStats::new);

            if (outcome.endsWith(" wins")) {
                String winner = outcome.substring(0, outcome.length() - 5).trim();
                if (winner.equals(p1)) { s1.wins++; s2.losses++; }
                else if (winner.equals(p2)) { s2.wins++; s1.losses++; }
            } else if (outcome.endsWith(" forfeited")) {
                String forfeiter = outcome.substring(0, outcome.length() - " forfeited".length()).trim();
                if (forfeiter.equals(p1)) { s2.wins++; s1.losses++; }
                else if (forfeiter.equals(p2)) { s1.wins++; s2.losses++; }
            } else if (outcome.startsWith("Draw")) {
                s1.draws++; s2.draws++;
            }
        }
        return map;
    }

    /** Return sorted leaderboard rows */
    public synchronized List<PlayerStats> topSorted(int limit) {
        Map<String, PlayerStats> map = computeLeaderboard();
        java.util.List<PlayerStats> list = new java.util.ArrayList<>(map.values());
        list.sort((a,b) -> {
            int c = Integer.compare(b.wins, a.wins);
            if (c!=0) return c;
            c = Integer.compare(b.draws, a.draws);
            if (c!=0) return c;
            c = Integer.compare(b.games(), a.games());
            if (c!=0) return c;
            return a.name.compareToIgnoreCase(b.name);
        });
        if (limit > 0 && list.size() > limit) {
            return list.subList(0, limit);
        }
        return list;
    }

    public Path getFilePath() {
        return filePath;
    }
}
