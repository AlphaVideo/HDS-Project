package org.example.channel;

import com.google.gson.Gson;
import org.example.consensus.block.SerializableBlock;

import java.nio.charset.StandardCharsets;

public class Payload {
    private String _opcode = null;
    private int _consInstance = -1;
    private int _round = -1;
    private String _value = null;
    private int _mid = -1;

    public Payload(String op, int inst, int round, String input) {
        _opcode = op;
        _consInstance = inst;
        _round = round;
        _value = input;
    }

    public Payload(String op, int inst, int round, SerializableBlock input) {
        this(op, inst, round, input.serialize().toString());
    }

    public Payload(String op, String input, int mid) {
        _opcode = op;
        _value = input;
        _mid = mid;
    }

    public String getOp() {
        return _opcode;
    }

    public int getInstance() {
        return _consInstance;
    }

    public int getRound() {
        return _round;
    }

    public String getValue() {
        return _value;
    }

    public int getId() {
        return _mid;
    }

    public byte[] getPayload() {
        return new Gson().toJson(this).getBytes();
    }

    public static Payload parsePayload(byte[] payload) {
        String plStr = new String(payload, StandardCharsets.UTF_8);
        return new Gson().fromJson(plStr, Payload.class);
    }

    @Override
    public String toString() {
        return _opcode + "|" + _consInstance + "|" + _round + "|" + _value;
    }
}
