package gg.paynow.paynowhytale.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import gg.paynow.paynowhytale.core.dto.CommandAttempt;
import gg.paynow.paynowhytale.core.dto.LinkRequest;
import gg.paynow.paynowhytale.core.dto.PlayerList;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

public class PayNowLib {

    private static final String VERSION = "0.0.8";

    private static final URI API_QUEUE_URL = URI.create("https://api.paynow.gg/v1/delivery/command-queue/");
    private static final URI API_LINK_URL = URI.create("https://api.paynow.gg/v1/delivery/gameserver/link");

    private final CommandHistory executedCommands;
    private final List<String> successfulCommands;

    private final Function<String, Boolean> executeCommandCallback;

    private final List<Consumer<PayNowConfig>> updateConfigCallbacks = new ArrayList<>();
    
    private BiConsumer<String, Level> logCallback = null;

    private PayNowConfig config = null;

    private final String ip;
    private final String motd;

    private final HttpClient httpClient;

    public PayNowLib(Function<String, Boolean> executeCommandCallback, String ip, String motd) {
        this.executeCommandCallback = executeCommandCallback;
        this.executedCommands = new CommandHistory(25);
        this.successfulCommands = new ArrayList<>();

        this.ip = ip;
        this.motd = motd;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public void updateConfig() {
        for(Consumer<PayNowConfig> callback : this.updateConfigCallbacks) {
            callback.accept(config);
        }

        this.linkToken();
    }

    public void onUpdateConfig(Consumer<PayNowConfig> callback) {
        this.updateConfigCallbacks.add(callback);
    }

    public void fetchPendingCommands(List<String> names, List<UUID> uuids) {
        this.debug("Fetching pending commands");
        String apiToken = this.config.getApiToken();
        if(apiToken == null) {
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

    public int handleResponse(String responseBody) {
        Gson gson = new Gson();
        List<QueuedCommand> commands = gson.fromJson(responseBody, new TypeToken<List<QueuedCommand>>(){}.getType());
        if(commands == null) {
            this.severe("Failed to parse commands");
            this.severe(responseBody);
            return 0;
        }

        return processCommands(commands);
    }

    private int processCommands(List<QueuedCommand> commands) {
        if(commands.isEmpty()) return 0;

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

        if(this.config.doesLogCommandExecutions()) {
            this.debug("Received " + commands.size() + " commands, executed " + this.successfulCommands.size());
        }

        this.acknowledgeCommands(this.successfulCommands);

        return this.successfulCommands.size();
    }

    private void acknowledgeCommands(List<String> commandsIds) {
        if(commandsIds.isEmpty()) return;

        String apiToken = this.config.getApiToken();
        if(apiToken == null) {
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

    private void linkToken() {
        this.debug("Linking token to game server");
        String apiToken = this.config.getApiToken();
        if(apiToken == null) {
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

    public void loadPayNowConfig(File configFile) {
        boolean exists = true;
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            try {
                configFile.createNewFile();
                exists = false;
            } catch (IOException e) {
                this.severe("Failed to create config file, using default values");
                this.config = new PayNowConfig();
                return;
            }
        }

        Gson gson = new Gson();
        try(InputStream is = new FileInputStream(configFile)) {
            byte[] bytes = new byte[is.available()];
            DataInputStream dataInputStream = new DataInputStream(is);
            dataInputStream.readFully(bytes);

            String configJson = new String(bytes);
            PayNowConfig config = gson.fromJson(configJson, PayNowConfig.class);
            if(config == null && exists) {
                this.severe("Failed to parse config, using default values");
                this.config = new PayNowConfig();
            } else {
                if (config == null) {
                    this.config = new PayNowConfig();
                } else {
                    this.config = config;
                }
            }
        } catch (IOException e) {
            this.severe("Failed to read config file, using default values");
            this.config = new PayNowConfig();
        }

        if(!exists) {
            this.savePayNowConfig(configFile);
        }

        this.linkToken();
    }

    public void savePayNowConfig(File configFile) {
        if (!configFile.exists()) {
            configFile.mkdirs();
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                this.severe("Failed to create config file");
                return;
            }

        }
        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        try(OutputStream os = new FileOutputStream(configFile)) {
            os.write(gson.toJson(this.config).getBytes());
        } catch (IOException e) {
            this.severe("Failed to save config file");
        }
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
        if(this.logCallback != null && this.config.isDebug()) this.logCallback.accept("[DEBUG] " + message, Level.INFO);
    }

    private void warn(String message) {
        if(this.logCallback != null) this.logCallback.accept("[WARN] " + message, Level.WARNING);
    }

    private void severe(String message) {
        if(this.logCallback != null) this.logCallback.accept("[SEVERE] " + message, Level.SEVERE);
    }

    public PayNowConfig getConfig() {
        return config;
    }

    // For testing purposes
    public void setConfig(PayNowConfig config) {
        this.config = config;
    }
}
