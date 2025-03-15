package org.example.consensus.block;

import com.google.gson.JsonObject;

abstract public class SerializableBlock {
    public JsonObject serialize() {
        return serialize(true);
    }

    abstract public JsonObject serialize(boolean include_signature);

    public static <T extends SerializableBlock> T deserialize(JsonObject el) {
        String type = el.get("type").getAsString();

        return switch (type) {
        case "Snapshot" -> (T) Snapshot.deserialize(el);
        case "TransactionBlock" -> (T) TransactionBlock.deserialize(el);
        default -> throw new RuntimeException("Block type not found");
        };
    }

    public abstract String formattedToString();

    abstract public boolean equals(Object o);

    abstract public int hashCode();
}
