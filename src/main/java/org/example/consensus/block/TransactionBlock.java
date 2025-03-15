package org.example.consensus.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.example.consensus.transaction.SerializableTransaction;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

public class TransactionBlock extends SerializableBlock {
    private final int TRANSACTIONS_PER_BLOCK = 4;
    protected byte[] _hashPrevious = null;
    protected byte[] _hash = null;
    private SerializableTransaction[] _transactions;
    private int _count;

    private TransactionBlock() {
    }

    public TransactionBlock(byte[] hashPrevious) {
        _transactions = new SerializableTransaction[TRANSACTIONS_PER_BLOCK];
        _count = 0;
        _hashPrevious = hashPrevious;
    }

    public void append(SerializableTransaction trans) {
        _transactions[_count++] = trans;
    }

    public boolean isFull() {
        return _count == TRANSACTIONS_PER_BLOCK;
    }

    public JsonObject serialize(boolean include_signature) {
        JsonObject self = new JsonObject();
        self.addProperty("type", "TransactionBlock");

        JsonArray txs = new JsonArray();

        for (SerializableTransaction tx : _transactions)
            if (tx != null)
                txs.add(tx.serialize());

        self.add("transactions", txs);

        if (_hashPrevious != null)
            self.addProperty("hashPrevious", Base64.getEncoder().encodeToString(_hashPrevious));

        return self;
    }

    public static TransactionBlock deserialize(JsonObject el) {
        TransactionBlock tx_block = new TransactionBlock();

        tx_block._transactions = el.get("transactions").getAsJsonArray().asList().stream()
                .map(JsonElement::getAsJsonObject).map(SerializableTransaction::deserialize)
                .toArray(SerializableTransaction[]::new);

        if (el.has("hashPrevious")) {
            tx_block._hashPrevious = Base64.getDecoder().decode(el.get("hashPrevious").getAsString());
        }

        return tx_block;
    }

    public byte[] getHashPrevious() {
        return _hashPrevious;
    }

    public String formattedToString() {
        StringBuilder str = new StringBuilder();
        String strHashPrevious;

        if (_hashPrevious == null)
            strHashPrevious = null;
        else
            strHashPrevious = Base64.getEncoder().encodeToString(_hashPrevious);

        str.append("Block {\n");
        str.append("  Attr: _hash = ").append(Base64.getEncoder().encodeToString(hash())).append("\n");
        str.append("  Attr: _hashPrevious = ").append(strHashPrevious).append("\n");
        str.append("  TRANSACTIONS { \n");

        for (SerializableTransaction txn : _transactions) {
            if (txn != null)
                str.append("\t").append(txn).append("\n");
        }

        str.append("  }\n");
        str.append("}");

        return str.toString();
    }

    public int getTransactionCount() {
        return _count;
    }

    public SerializableTransaction[] getTransactions() {
        return _transactions;
    }

    public SerializableTransaction getTransaction(int i) {
        return _transactions[i];
    }

    public byte[] hash() {
        MessageDigest digest;

        if (_hash != null)
            return _hash;

        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        _hash = digest.digest(serialize(false).toString().getBytes());
        return _hash;
    }

    @Override
    public String toString() {
        return serialize().toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof TransactionBlock that))
            return false;

        if (!Arrays.equals(_hashPrevious, that._hashPrevious))
            return false;
        if (!Arrays.equals(_hash, that._hash))
            return false;
        return Arrays.equals(_transactions, that._transactions);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(_hashPrevious);
        result = 31 * result + Arrays.hashCode(_hash);
        result = 31 * result + Arrays.hashCode(_transactions);
        return result;
    }
}
