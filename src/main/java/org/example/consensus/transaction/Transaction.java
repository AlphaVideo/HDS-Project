package org.example.consensus.transaction;

import com.google.gson.JsonObject;
import org.example.encryption.EncryptionUnit;

import java.security.PublicKey;
import java.util.Base64;
import java.util.Date;

public class Transaction extends SerializableTransaction {
    private PublicKey _src;

    private Transaction() {
    }

    public Transaction(PublicKey src, PublicKey dst, int amount) {
        _src = src;
        _dst = dst;
        _amount = amount;
        _timestamp = new Date().getTime();
    }

    public Transaction(Transaction tx, int fee, PublicKey miner) {
        _src = tx.get_source();
        _dst = miner;
        _timestamp = tx.get_timestamp();
        _clientSig = null;
        _amount = fee;
    }

    public Transaction(Account acc, int fee, PublicKey miner) {
        _src = acc.get_client_key();
        _dst = miner;
        _timestamp = acc.get_timestamp();
        _clientSig = null;
        _amount = fee;
    }

    @Override
    public JsonObject serialize() {
        JsonObject self = new JsonObject();

        self.addProperty("type", "Transaction");
        self.addProperty("amount", _amount);
        self.addProperty("timestamp", _timestamp);
        self.addProperty("source", EncryptionUnit.public_key_to_base64(_src));
        self.addProperty("destination", EncryptionUnit.public_key_to_base64(_dst));

        if (_clientSig != null)
            self.addProperty("client_signature", new String(Base64.getEncoder().encode(_clientSig)));

        return self;
    }

    @Override
    public JsonObject signatureSerialize() {
        JsonObject self = new JsonObject();

        self.addProperty("type", "Transaction");
        self.addProperty("amount", _amount);
        self.addProperty("timestamp", _timestamp);
        self.addProperty("source", EncryptionUnit.public_key_to_base64(_src));
        self.addProperty("destination", EncryptionUnit.public_key_to_base64(_dst));

        return self;
    }

    public PublicKey get_source() {
        return _src;
    }

    public static Transaction deserialize(JsonObject el) {
        Transaction tx = new Transaction();

        tx._amount = el.get("amount").getAsInt();
        tx._timestamp = el.get("timestamp").getAsLong();

        tx._src = EncryptionUnit.base64_to_public_key(el.get("source").getAsString());
        tx._dst = EncryptionUnit.base64_to_public_key(el.get("destination").getAsString());

        if (el.has("client_signature"))
            tx._clientSig = Base64.getDecoder().decode(el.get("client_signature").getAsString());
        else
            tx._clientSig = null;

        return tx;
    }

    @Override
    public String toString() {
        return "TXN {" + " SrcPubKey: " + EncryptionUnit.public_key_to_base64(_src) + " | DstPubKey: "
                + EncryptionUnit.public_key_to_base64(_dst) + " | Amount: " + _amount + " | Timestamp: " + _timestamp
                + " }";
    }
}
