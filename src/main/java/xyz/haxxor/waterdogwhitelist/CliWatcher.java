package xyz.haxxor.waterdogwhitelist;

import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.sql.SQLException;

/**
 * Lets admin scripts drive the whitelist without an interactive console. The proxy's real
 * stdin (what /whitelist normally reads from) isn't reachable non-interactively - kubectl exec
 * always starts a brand new process, it can't write into WaterdogPE's own stdin. Since a fresh
 * exec'd process *can* read/write files in the same pod, admin scripts drop a "<id>.request"
 * file (single line, e.g. "add <xuid> <gamertag>") into this directory instead; this watcher
 * polls for them, runs the command, and writes the result to "<id>.response".
 */
public class CliWatcher implements Runnable {

    private final File directory;
    private final WhitelistOperations operations;
    private final Logger logger;
    private volatile boolean running = true;

    public CliWatcher(File directory, WhitelistOperations operations, Logger logger) {
        this.directory = directory;
        this.operations = operations;
        this.logger = logger;
        this.directory.mkdirs();
    }

    public void stop() {
        this.running = false;
    }

    @Override
    public void run() {
        while (this.running) {
            File[] requests = this.directory.listFiles((dir, name) -> name.endsWith(".request"));
            if (requests != null) {
                for (File request : requests) {
                    this.handle(request);
                }
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void handle(File request) {
        String name = request.getName();
        String id = name.substring(0, name.length() - ".request".length());
        File response = new File(this.directory, id + ".response");

        String result;
        try {
            result = this.execute(Files.readString(request.toPath()).trim());
        } catch (Exception e) {
            this.logger.error("CLI request '{}' failed", id, e);
            result = "ERROR: " + e.getMessage();
        }

        try {
            Files.writeString(response.toPath(), result);
            Files.delete(request.toPath());
        } catch (Exception e) {
            this.logger.error("Could not write CLI response for '{}'", id, e);
        }
    }

    private String execute(String line) throws SQLException {
        String[] args = line.split("\\s+");
        if (args.length == 0 || args[0].isEmpty()) {
            return "ERROR: empty command";
        }
        return switch (args[0].toLowerCase()) {
            case "add" -> args.length < 2
                    ? "ERROR: usage: add <xuid> [gamertag]"
                    : this.operations.add(args[1], args.length > 2 ? args[2] : null);
            case "remove" -> args.length < 2
                    ? "ERROR: usage: remove <xuid>"
                    : this.operations.remove(args[1]);
            case "list" -> this.operations.list();
            case "attempts" -> {
                try {
                    yield this.operations.attempts(args.length > 1 ? Integer.parseInt(args[1]) : 10);
                } catch (NumberFormatException e) {
                    yield "ERROR: usage: attempts [count]";
                }
            }
            default -> "ERROR: unknown command '" + args[0] + "'";
        };
    }
}
