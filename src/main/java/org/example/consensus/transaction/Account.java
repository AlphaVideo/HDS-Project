package org.example.consensus.transaction;

import com.google.gson.JsonObject;
import org.example.encryption.EncryptionUnit;

import java.util.Base64;

import java.security.PublicKey;
import org.example.encryption.*;
import java.util.Date;

public class Account extends SerializableTransaction {
    private final int STARTING_BALANCE = 100;

    private Account() {
    }

    public Account(PublicKey clientKey) {
        _dst = clientKey; // Destination = new client created
        _amount = STARTING_BALANCE;
        _timestamp = new Date().getTime();
    }

    public static Account deserialize(JsonObject el) {
        Account acc = new Account();

        acc._amount = el.get("amount").getAsInt();
        acc._timestamp = el.get("timestamp").getAsLong();

        acc._dst = EncryptionUnit.base64_to_public_key(el.get("client_key").getAsString());

        if (el.has("client_signature"))
            acc._clientSig = Base64.getDecoder().decode(el.get("client_signature").getAsString());
        else
            acc._clientSig = null;

        return acc;
    }

    // Alias for code clarity
    public PublicKey get_client_key() {
        return get_destination();
    }

    @Override
    public JsonObject serialize() {
        JsonObject self = new JsonObject();

        self.addProperty("type", "Account");
        self.addProperty("amount", _amount);
        self.addProperty("timestamp", _timestamp);
        self.addProperty("client_key", EncryptionUnit.public_key_to_base64(_dst));

        if (_clientSig != null)
            self.addProperty("client_signature", new String(Base64.getEncoder().encode(_clientSig)));

        return self;
    }

    @Override
    public JsonObject signatureSerialize() {
        JsonObject self = new JsonObject();

        self.addProperty("type", "Account");
        self.addProperty("amount", _amount);
        self.addProperty("timestamp", _timestamp);
        self.addProperty("client_key", EncryptionUnit.public_key_to_base64(_dst));

        return self;
    }

    @Override
    public String toString() {
        return "ACC_TXN {" + " AccPubKey: " + EncryptionUnit.public_key_to_base64(_dst) + " | Creation Balance: "
                + _amount + " | Timestamp: " + _timestamp + " }";
    }
}
