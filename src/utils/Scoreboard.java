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
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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

    public Path getFilePath() {
        return filePath;
    }
}
