package org.example.channel;

import org.example.encryption.EncryptionUnit;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

public class Message {
    private static final int SIG_LEN = 256;
    private static final int NONCE_LEN = 16;

    private final Host _host;
    private byte[] _payload;
    private byte[] _nonce = new byte[NONCE_LEN];
    private byte[] _signature = new byte[] {};

    public Message(DatagramPacket packet) {
        _host = new Host(packet.getAddress(), packet.getPort());
        _payload = Arrays.copyOf(packet.getData(), packet.getLength());
    }

    public Message(byte[] data, Host host) {
        _payload = data;
        new SecureRandom().nextBytes(_nonce);
        _host = host;
    }

    public Host get_host() {
        return _host;
    }

    public boolean is_client() {
        return EncryptionUnit.is_client(_host.get_pid());
    }

    public byte[] get_payload() {
        return _payload;
    }

    public byte[] get_payload_and_nonce() {
        return ByteBuffer.allocate(_payload.length + NONCE_LEN).put(_payload).put(_nonce).array();
    }

    public byte[] get_data() {
        if (_signature.length == 0) {
            return _payload;
        }
        return ByteBuffer.allocate(_payload.length + _nonce.length + _signature.length).put(_payload).put(_nonce)
                .put(_signature).array();
    }

    // This NEEDS to be invocated before get_nonce, get_signature and get_payload
    public void rebuild_signature_and_nonce() {
        if (_signature.length == 0 && _payload.length >= (NONCE_LEN + SIG_LEN)) {
            ByteBuffer buffer = ByteBuffer.wrap(_payload);

            _payload = new byte[_payload.length - (NONCE_LEN + SIG_LEN)];
            _signature = new byte[256];

            buffer.get(_payload, 0, buffer.capacity() - (NONCE_LEN + SIG_LEN));
            buffer.get(_nonce, 0, NONCE_LEN);
            buffer.get(_signature, 0, SIG_LEN);
        }
    }

    public byte[] get_signature() {
        return _signature;
    }

    public byte[] get_nonce() {
        return _nonce;
    }

    public void set_nonce(byte[] nonce) {
        _nonce = nonce;
    }

    public void set_signature(byte[] sig) {
        _signature = sig;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_host, ByteBuffer.wrap(_payload));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Message o) {
            return Arrays.equals(_payload, o._payload) && _host.equals(o._host)
                    && Arrays.equals(_signature, o._signature);
        }
        return false;
    }

    @Override
    public String toString() {
        return "Message[" + _host + ",'" + new String(_payload) + "', Nonce[" + ByteBuffer.wrap(_nonce).getLong()
                + "], HashCode[" + hashCode() + "]]";
    }
}
