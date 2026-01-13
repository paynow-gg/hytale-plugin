package gg.paynow.paynowhytale.core.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.UUID;

public class PlayerList {

    @SerializedName("customer_names")
    List<String> customerNames;
    @SerializedName("hytale_uuids")
    List<UUID> hytaleUuids;

    public PlayerList(List<String> names, List<UUID> uuids) {
        this.customerNames = names;
        this.hytaleUuids = uuids;
    }

}
