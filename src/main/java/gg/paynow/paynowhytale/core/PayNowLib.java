package gg.paynow.paynowhytale.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import gg.paynow.paynowhytale.PayNowConfig;
import gg.paynow.paynowhytale.PayNowHytale;
import gg.paynow.paynowhytale.core.dto.CommandAttempt;
import gg.paynow.paynowhytale.core.dto.LinkRequest;
import gg.paynow.paynowhytale.core.dto.PlayerList;
import gg.paynow.paynowhytale.core.events.PayNowEvent;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Level;

public class PayNowLib {

    private static final String VERSION = "0.0.8";

    private static final URI API_QUEUE_URL = URI.create("https://api.paynow.gg/v1/delivery/command-queue/");
    private static final URI API_LINK_URL = URI.create("https://api.paynow.gg/v1/delivery/gameserver/link");
    private static final URI API_EVENTS_URL = URI.create("https://api.paynow.gg/v1/delivery/events");

    private final CommandHistory executedCommands;
    private final List<String> successfulCommands;

    private final ConcurrentLinkedQueue<PayNowEvent> eventQueue;

    private final Function<String, Boolean> executeCommandCallback;
    
    private BiConsumer<String, Level> logCallback = null;

    private final String ip;
    private final String motd;

    private final HttpClient httpClient;

    public PayNowLib(Function<String, Boolean> executeCommandCallback, String ip, String motd) {
        this.executeCommandCallback = executeCommandCallback;
        this.executedCommands = new CommandHistory(25);
        this.successfulCommands = new ArrayList<>();
        this.eventQueue = new ConcurrentLinkedQueue<>();

        this.ip = ip;
        this.motd = motd;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public void fetchPendingCommands(List<String> names, List<UUID> uuids) {
        this.debug("Fetching pending commands");
        String apiToken = PayNowHytale.getInstance().getConfig().get().getApiToken();
        if(apiToken == null || apiToken.isEmpty()) {
            this.warn("API Token is not set");
            return;
        }

        String requestBody = formatPlayers(names, uuids);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(API_QUEUE_URL)
                .header("Content-Type", "application/json")
                .header("Authorization", "Gameserver " + apiToken)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        PayNowUtils.ASYNC_EXEC.submit(() -> {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if(response.statusCode() != 200) {
                    severe("Failed to fetch commands: " + response.body());
                    return;
                }

                handleResponse(response.body());
            } catch (IOException | InterruptedException e) {
                severe("Failed to fetch commands: error executing request");
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    public void handleResponse(String responseBody) {
        Gson gson = new Gson();
        List<QueuedCommand> commands = gson.fromJson(responseBody, new TypeToken<List<QueuedCommand>>(){}.getType());
        if(commands == null) {
            this.severe("Failed to parse commands");
            this.severe(responseBody);
            return;
        }

        processCommands(commands);
    }

    private void processCommands(List<QueuedCommand> commands) {
        if(commands.isEmpty()) return;

        this.successfulCommands.clear();
        for (QueuedCommand command : commands) {
            if(this.executedCommands.contains(command.getAttemptId())) continue;

            boolean success = this.executeCommandCallback.apply(command.getCommand());
            if(success) {
                this.successfulCommands.add(command.getAttemptId());
            } else {
                this.warn("Failed to execute command: " + command.getCommand());
            }
        }

        if(this.getConfig().doesLogCommandExecutions()) {
            this.debug("Received " + commands.size() + " commands, executed " + this.successfulCommands.size());
        }

        this.acknowledgeCommands(this.successfulCommands);

    }

    private void acknowledgeCommands(List<String> commandsIds) {
        if(commandsIds.isEmpty()) return;

        String apiToken = this.getConfig().getApiToken();
        if(apiToken == null || apiToken.isEmpty()) {
            this.warn("API Token is not set");
            return;
        }

        String formatted = formatCommandIds(commandsIds);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(API_QUEUE_URL)
                .header("Content-Type", "application/json")
                .header("Authorization", "Gameserver " + apiToken)
                .header("Accept", "application/json")
                .method("DELETE", HttpRequest.BodyPublishers.ofString(formatted))
                .build();

        PayNowUtils.ASYNC_EXEC.submit(() -> {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if(!PayNowUtils.isSuccess(response.statusCode())) {
                    this.warn("Failed to acknowledge commands: " + response.body());
                } else {
                    for (String commandId : commandsIds) {
                        this.executedCommands.add(commandId);
                    }
                }
            } catch (IOException | InterruptedException e) {
                severe("Failed to acknowledge commands: error executing request");
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    public void linkToken() {
        this.debug("Linking token to game server");
        String apiToken = this.getConfig().getApiToken();
        if(apiToken == null || apiToken.isEmpty()) {
            this.warn("API Token is not set");
            return;
        }

        Gson gson = new Gson();

        LinkRequest linkRequest = new LinkRequest(this.ip, this.motd == null ? "" : this.motd, "Hytale", VERSION);
        String requestJson = gson.toJson(linkRequest);

        this.log(requestJson);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(API_LINK_URL)
                .header("Content-Type", "application/json")
                .header("Authorization", "Gameserver " + apiToken)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        PayNowUtils.ASYNC_EXEC.submit(() -> {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                String responseBody = response.body();

                this.debug("Linked token: " + responseBody);
                if(!PayNowUtils.isSuccess(response.statusCode())) {
                    this.warn("Failed to link token: " + responseBody);
                }

                log(responseBody);
                handleLinkResponse(responseBody);
            } catch (IOException | InterruptedException e) {
                severe("Failed to link token: error executing request");
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    private void handleLinkResponse(String responseBody) {
        Gson gson = new Gson();
        JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);

        if(responseJson.has("update_available") && responseJson.get("update_available").getAsBoolean()) {
            String latestVersion = responseJson.get("latest_version").getAsString();
            this.warn("A new version of the PayNow plugin is available: " + latestVersion + "! Your version: " + VERSION);
        }

        if(responseJson.has("previously_linked")) {
            JsonObject previouslyLinked = responseJson.get("previously_linked").getAsJsonObject();
            String hostname = previouslyLinked.get("host_name").getAsString();
            String ip = previouslyLinked.get("ip").getAsString();
            this.warn("This token has been previously used on \"" + hostname + "\" (" + ip + "), ensure you have removed this token from the previous server.");
        }

        if(!responseJson.has("gameserver")) {
            this.warn("PayNow API did not return a GameServer object, this may be a transient issue, please try again or contact support.");
            return;
        }

        JsonObject gameServer = responseJson.get("gameserver").getAsJsonObject();
        String gsName = gameServer.get("name").getAsString();
        String gsId = gameServer.get("id").getAsString();

        this.log("Successfully connected to PayNow using the token for \"" + gsName + "\" (" + gsId + ")");
    }

    public void registerEvent(PayNowEvent event) {
        this.eventQueue.add(event);
    }

    public void reportEvents() {
        if(this.eventQueue.isEmpty()) return;

        String apiToken = this.getConfig().getApiToken();
        if(apiToken == null) {
            this.warn("API Token is not set");
            return;
        }

        Gson gson = new GsonBuilder().registerTypeAdapter(PayNowEvent.class, new PayNowEvent.PayNowEventAdapter()).create();

        // Atomically drain all events from the queue
        List<PayNowEvent> eventsToReport = new ArrayList<>();
        PayNowEvent event;
        while ((event = this.eventQueue.poll()) != null) {
            eventsToReport.add(event);
        }

        if(eventsToReport.isEmpty()) return;

        String requestJson = gson.toJson(eventsToReport);

        this.debug(requestJson);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(API_EVENTS_URL)
                .header("Content-Type", "application/json")
                .header("Authorization", "Gameserver " + apiToken)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        // Execute the HTTP request asynchronously
        PayNowUtils.ASYNC_EXEC.submit(() -> {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int statusCode = response.statusCode();
                if(!PayNowUtils.isSuccess(statusCode)) {
                    this.warn("Failed to report events: " + statusCode);
                    // Re-add events to the front of the queue if failed to report
                    eventsToReport.forEach(this.eventQueue::offer);
                } else {
                    this.debug("Successfully reported " + eventsToReport.size() + " events");
                }
            } catch (IOException | InterruptedException ex) {
                severe("Failed to report events: " + ex.getMessage());
                debug(Arrays.toString(ex.getStackTrace()));
                // Re-add events to the queue if failed to report
                eventsToReport.forEach(this.eventQueue::offer);
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }


    private String formatPlayers(List<String> names, List<UUID> uuids) {
        Gson gson = new Gson();

        PlayerList playerList = new PlayerList(names, uuids);
        String json = gson.toJson(playerList);
        this.debug(json);
        return json;
    }

    private String formatCommandIds(List<String> commandIds) {
        List<CommandAttempt> attempts = new ArrayList<>();
        for (String commandId : commandIds) {
            attempts.add(new CommandAttempt(commandId));
        }

        Gson gson = new Gson();
        String json = gson.toJson(attempts);
        this.debug(json);
        return json;
    }

    public void setLogCallback(BiConsumer<String, Level> logCallback) {
        this.logCallback = logCallback;
    }

    private void log(String message) {
        if(this.logCallback != null) this.logCallback.accept(message, Level.INFO);
    }

    private void debug(String message) {
        if(this.logCallback != null && this.getConfig().isDebug()) this.logCallback.accept("[DEBUG] " + message, Level.INFO);
    }

    private void warn(String message) {
        if(this.logCallback != null) this.logCallback.accept("[WARN] " + message, Level.WARNING);
    }

    private void severe(String message) {
        if(this.logCallback != null) this.logCallback.accept("[SEVERE] " + message, Level.SEVERE);
    }

    private PayNowConfig getConfig() {
        return PayNowHytale.getInstance().getConfig().get();
    }
}
