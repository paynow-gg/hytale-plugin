package gg.paynow.paynowhytale;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public class PayNowConfig {
    public static final BuilderCodec<PayNowConfig> CODEC = BuilderCodec.builder(PayNowConfig.class, PayNowConfig::new)
            .append(new KeyedCodec<>("API token", Codec.STRING, true), PayNowConfig::setApiToken, PayNowConfig::getApiToken).add()
            .append(new KeyedCodec<>("Check interval", Codec.INTEGER), PayNowConfig::setApiCheckInterval, PayNowConfig::getApiCheckInterval).add()
            .append(new KeyedCodec<>("Log command executions", Codec.BOOLEAN), PayNowConfig::setLogCommandExecutions, PayNowConfig::doesLogCommandExecutions).add()
            .append(new KeyedCodec<>("Debug", Codec.BOOLEAN), PayNowConfig::setDebug, PayNowConfig::isDebug).add().build();

    private String apiToken = "";

    private int apiCheckInterval = 10;

    private boolean logCommandExecutions = true;

    private boolean debug = false;

    public PayNowConfig() {}

    public String getApiToken() {
        return apiToken;
    }

    public int getApiCheckInterval() {
        return apiCheckInterval;
    }

    public boolean doesLogCommandExecutions() {
        return logCommandExecutions;
    }

    public void setApiCheckInterval(int apiCheckInterval) {
        this.apiCheckInterval = apiCheckInterval;
    }

    public void setLogCommandExecutions(boolean logCommandExecutions) {
        this.logCommandExecutions = logCommandExecutions;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }
}