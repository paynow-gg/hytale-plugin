package gg.paynow.paynowhytale;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import gg.paynow.paynowhytale.core.PayNowLib;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PayNowHytale extends JavaPlugin {

    private static PayNowHytale instance;

    private PayNowLib payNowLib;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> currentTask;

    public PayNowHytale(JavaPluginInit init) {
        super(init);
        instance = this;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        System.out.println("[PayNow] Plugin loaded!");

    }

    @Override
    protected void setup() {
        File configFile = getConfigFile();

        // TODO: Change IP parameter
        this.payNowLib = new PayNowLib(command -> {
            HytaleServer.get().getCommandManager().handleCommand(ConsoleSender.INSTANCE, command);
            return true;
        }, "Hytale Server", HytaleServer.get().getConfig().getMotd());
        this.payNowLib.setLogCallback((s, level) -> this.getLogger().at(level).log(s));
        this.payNowLib.loadPayNowConfig(configFile);

        // Register command

        this.payNowLib.onUpdateConfig(_ -> this.startRunnable());
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
        }, 0, this.payNowLib.getConfig().getApiCheckInterval(), TimeUnit.SECONDS);
    }

    public void triggerConfigUpdate(){
        this.payNowLib.savePayNowConfig(this.getConfigFile());
        this.payNowLib.updateConfig();
    }

    private File getConfigFile() {
        return new File(new File(this.getDataDirectory().toUri()), "config.yml");
    }

    public PayNowLib getPayNowLib() {
        return payNowLib;
    }

    public static PayNowHytale getInstance() {
        return instance;
    }
}
