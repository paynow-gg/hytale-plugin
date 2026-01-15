package gg.paynow.paynowhytale;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.util.Config;
import gg.paynow.paynowhytale.core.PayNowLib;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

public class PayNowHytale extends JavaPlugin {

    private static PayNowHytale instance;

    private PayNowLib payNowLib;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> currentTask;

    private final Config<PayNowConfig> config;

    public PayNowHytale(JavaPluginInit init) {
        super(init);
        instance = this;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        System.out.println("[PayNow] Plugin loaded!");
        this.config = this.withConfig(PayNowConfig.CODEC);
    }

    @Override
    protected void setup() {
        // Create the config if it doesn't exist
        this.config.save().thenAcceptAsync(_ -> {});

        // TODO: Change IP parameter
        this.payNowLib = new PayNowLib(command -> {
            HytaleServer.get().getCommandManager().handleCommand(ConsoleSender.INSTANCE, command);
            return true;
        }, "Hytale Server", HytaleServer.get().getConfig().getMotd());
        this.payNowLib.setLogCallback((s, level) -> this.getLogger().at(level).log(s));

        this.startRunnable();

        this.getCommandRegistry().registerCommand(new PayNowCommand());
    }

    private void startRunnable() {
        if (currentTask != null && !currentTask.isCancelled()) {
            currentTask.cancel(false);
        }

        currentTask = scheduler.scheduleAtFixedRate(() -> {
            List<String> onlinePlayersNames = new ArrayList<>();
            List<UUID> onlinePlayersUUIDs = new ArrayList<>();
            for (PlayerRef player : Universe.get().getPlayers()) {
                onlinePlayersNames.add(player.getUsername());
                onlinePlayersUUIDs.add(player.getUuid());
            }
            this.payNowLib.fetchPendingCommands(onlinePlayersNames, onlinePlayersUUIDs);
        }, 0, this.config.get().getApiCheckInterval(), TimeUnit.SECONDS);
    }

    public void triggerConfigUpdate(){
        this.config.save().thenAcceptAsync(_ -> {});
        this.payNowLib.linkToken();
        this.startRunnable();
    }

    public Config<PayNowConfig> getConfig() {
        return config;
    }

    public static PayNowHytale getInstance() {
        return instance;
    }
}
