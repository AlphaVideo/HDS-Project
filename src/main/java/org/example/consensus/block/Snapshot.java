package org.example.consensus.block;

import java.security.PublicKey;
import com.google.gson.JsonObject;
import org.example.encryption.EncryptionUnit;

import java.util.*;

public class Snapshot extends SerializableBlock {
    private final Map<PublicKey, Integer> _balance;

    private final Map<PublicKey, byte[]> _signatures;

    public Snapshot() {
        _balance = new HashMap<>();
        _signatures = new HashMap<>();
    }

    public Snapshot(Map<PublicKey, Integer> balance) {
        _balance = balance;
        _signatures = new HashMap<>();
    }

    public Map<PublicKey, Integer> get_balance() {
        return _balance;
    }

    public Map<PublicKey, byte[]> get_signatures() {
        return _signatures;
    }

    public JsonObject serialize(boolean include_signature) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "Snapshot");

        JsonObject balances = new JsonObject();
        for (PublicKey k : _balance.keySet()) {
            balances.addProperty(EncryptionUnit.public_key_to_base64(k), _balance.get(k));
        }
        obj.add("balances", balances);

        if (include_signature) {
            JsonObject signatures = new JsonObject();
            for (PublicKey k : _signatures.keySet()) {
                signatures.addProperty(EncryptionUnit.public_key_to_base64(k),
                        Base64.getEncoder().encodeToString(_signatures.get(k)));
            }
            obj.add("signatures", signatures);
        }

        return obj;
    }

    @Override
    public String formattedToString() {
        StringBuilder str = new StringBuilder();

        str.append("Snapshot {\n");
        str.append("  BALANCES { \n");

        for (var entry : _balance.entrySet()) {
            str.append("\t").append(EncryptionUnit.public_key_to_base64(entry.getKey())).append(": ")
                    .append(entry.getValue()).append("\n");
        }

        str.append("  }\n");

        str.append("  SIGNATURES { \n");

        for (var entry : _signatures.entrySet()) {
            str.append("\t").append(EncryptionUnit.public_key_to_base64(entry.getKey())).append(": ")
                    .append(Base64.getEncoder().encodeToString(entry.getValue())).append("\n");
        }

        str.append("  }\n");
        str.append("}");

        return str.toString();
    }

    public static Snapshot deserialize(JsonObject el) {
        Snapshot snap = new Snapshot();

        JsonObject balances = el.get("balances").getAsJsonObject();
        for (String key : balances.keySet()) {
            snap._balance.put(EncryptionUnit.base64_to_public_key(key), balances.get(key).getAsInt());
        }

        if (el.has("signatures")) {
            JsonObject signatures = el.get("signatures").getAsJsonObject();
            for (String key : signatures.keySet()) {
                snap._signatures.put(EncryptionUnit.base64_to_public_key(key),
                        Base64.getDecoder().decode(signatures.get(key).getAsString()));
            }
        }

        return snap;
    }

    public int get_valid_signatures(EncryptionUnit unit) {
        byte[] b = serialize(false).toString().getBytes();

        int i = 0;
        for (Map.Entry<PublicKey, byte[]> sigEntry : _signatures.entrySet()) {
            if (unit.verifySignature(b, sigEntry.getValue(), sigEntry.getKey()))
                i += 1;

        }

        return i;
    }

    public boolean verify_signature(EncryptionUnit unit, PublicKey pk, byte[] sig) {
        byte[] b = serialize(false).toString().getBytes();

        return unit.verifySignature(b, sig, pk);
    }

    public void sign(EncryptionUnit unit) {
        addSignature(unit.getOwnPublicKey(), unit.sign(serialize(false).toString().getBytes()));
    }

    public void addSignature(PublicKey pk, byte[] signature) {
        _signatures.put(pk, signature);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Snapshot snapshot))
            return false;

        return _balance.equals(snapshot._balance);
    }

    @Override
    public int hashCode() {
        return _balance.hashCode();
    }
}
