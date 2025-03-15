package org.example.consensus.transaction;

import com.google.gson.JsonObject;
import org.example.encryption.EncryptionUnit;

import java.security.PublicKey;
import java.util.Arrays;
import java.util.Objects;

abstract public class SerializableTransaction {
    protected PublicKey _dst;
    protected int _amount;
    protected long _timestamp;
    protected byte[] _clientSig;

    abstract public JsonObject serialize();

    /**
     * Excludes client signature from serialization for signing and verification purposes.
     */
    abstract public JsonObject signatureSerialize();

    public static <T extends SerializableTransaction> T deserialize(JsonObject el) {
        String type = el.get("type").getAsString();

        return switch (type) {
        case "Transaction" -> (T) Transaction.deserialize(el);
        case "Account" -> (T) Account.deserialize(el);
        default -> throw new RuntimeException("Transaction type not found");
        };
    }

    public int get_amount() {
        return _amount;
    }

    public PublicKey get_destination() {
        return _dst;
    }

    public long get_timestamp() {
        return _timestamp;
    }

    public byte[] get_client_signature() {
        return _clientSig;
    }

    public void sign(EncryptionUnit clientUnit) {
        _clientSig = clientUnit.sign(this.signatureSerialize().toString().getBytes());
    }

    public boolean verifySignature(EncryptionUnit serverUnit, PublicKey clientKey) {
        return serverUnit.verifySignature(this.signatureSerialize().toString().getBytes(), _clientSig, clientKey);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof SerializableTransaction that))
            return false;

        if (_amount != that._amount)
            return false;
        if (_timestamp != that._timestamp)
            return false;
        if (!Objects.equals(_dst, that._dst))
            return false;
        return Arrays.equals(_clientSig, that._clientSig);
    }

    @Override
    public int hashCode() {
        int result = _dst != null ? _dst.hashCode() : 0;
        result = 31 * result + _amount;
        result = 31 * result + (int) (_timestamp ^ (_timestamp >>> 32));
        result = 31 * result + Arrays.hashCode(_clientSig);
        return result;
    }
}
