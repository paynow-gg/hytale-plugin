package gg.paynow.paynowhytale.core.events;

import com.google.gson.annotations.SerializedName;

import java.util.UUID;

public class PlayerJoinEventData extends EventData {

    @SerializedName("ip_address")
    private String ipAddress;

    @SerializedName("hytale_uuid")
    private UUID hytaleUUID;

    public PlayerJoinEventData(String ipAddress, UUID hytaleUUID) {
        this.ipAddress = ipAddress;
        this.hytaleUUID = hytaleUUID;
    }

    public PlayerJoinEventData() { }

    public String getIpAddress() {
        return ipAddress;
    }

    public UUID getHytaleUUID() {
        return hytaleUUID;
    }

}