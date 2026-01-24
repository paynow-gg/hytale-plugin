package gg.paynow.paynowhytale;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.util.Config;
import gg.paynow.paynowhytale.core.PayNowLib;
import gg.paynow.paynowhytale.core.events.PayNowEvent;
import gg.paynow.paynowhytale.core.events.PlayerJoinEventData;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

public class PayNowHytale extends JavaPlugin {

    private static PayNowHytale instance;

    private PayNowLib payNowLib;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> commandsTask;
    private ScheduledFuture<?> reportEventsTask;

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
        String motd = HytaleServer.get().getConfig().getMotd();
        this.payNowLib = new PayNowLib(command -> {
            HytaleServer.get().getCommandManager().handleCommand(ConsoleSender.INSTANCE, command);
            return true;
        }, "127.0.0.1", motd == null ? "Hytale Server" : motd);
        this.payNowLib.setLogCallback((s, level) -> this.getLogger().at(level).log(s));

        this.startRunnable();

        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);

        this.getCommandRegistry().registerCommand(new PayNowCommand());
    }

    private void startRunnable() {
        if (commandsTask != null && !commandsTask.isCancelled()) {
            commandsTask.cancel(false);
        }
        if (reportEventsTask != null && !reportEventsTask.isCancelled()) {
            reportEventsTask.cancel(false);
        }

        commandsTask = scheduler.scheduleAtFixedRate(() -> {
            List<String> onlinePlayersNames = new ArrayList<>();
            List<UUID> onlinePlayersUUIDs = new ArrayList<>();
            for (PlayerRef player : Universe.get().getPlayers()) {
                onlinePlayersNames.add(player.getUsername());
                onlinePlayersUUIDs.add(player.getUuid());
            }
            this.payNowLib.fetchPendingCommands(onlinePlayersNames, onlinePlayersUUIDs);
        }, 0, this.config.get().getApiCheckInterval(), TimeUnit.SECONDS);

        reportEventsTask = scheduler.scheduleAtFixedRate(this.payNowLib::reportEvents, 0, this.config.get().getEventsQueueReportInterval(), TimeUnit.SECONDS);
    }

    public void triggerConfigUpdate(){
        this.config.save().thenAcceptAsync(_ -> {});
        this.payNowLib.linkToken();
        this.startRunnable();
    }

    private void onPlayerReady(PlayerReadyEvent event) {
        UUID playerUUID = event.getPlayerRef().getStore().ensureAndGetComponent(event.getPlayerRef(), UUIDComponent.getComponentType()).getUuid();
        PayNowEvent payNowEvent = new PayNowEvent("player_join", new Date(), new PlayerJoinEventData(null, playerUUID));
        this.payNowLib.registerEvent(payNowEvent);
    }

    public Config<PayNowConfig> getConfig() {
        return config;
    }

    public static PayNowHytale getInstance() {
        return instance;
    }
}
