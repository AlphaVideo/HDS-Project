package org.example.channel;

import org.example.common.ColourfulPrinter;
import org.example.encryption.EncryptionUnit;

import java.net.SocketException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.*;

public class PerfectLink extends FairLossLink implements Channel {
    private static final long TIMEOUT = 500;
    private final int _port;
    private static final HashMap<Integer, PerfectLink> _instances = new HashMap<>();
    private final HashSet<Long> _messageIds = new HashSet<>();
    private final HashSet<Long> _acks = new HashSet<>();
    private final EncryptionUnit _encryptionUnit;
    private ColourfulPrinter _printer;

    public PerfectLink(int port, EncryptionUnit encryptionUnit) throws SocketException {
        super(port);
        _port = port;
        _encryptionUnit = encryptionUnit;
        _printer = new ColourfulPrinter(port, true);
    }

    public static synchronized void init_instance(int port, EncryptionUnit encryptionUnit) throws SocketException {
        if (!_instances.containsKey(port)) {
            _instances.put(port, new PerfectLink(port, encryptionUnit));
        }
    }

    public static synchronized PerfectLink get_instance(int port) {
        if (_instances.containsKey(port)) {
            return _instances.get(port);
        } else {
            throw new RuntimeException("PerfectLink::get_instance called before init_instance");
        }
    }

    public static synchronized void clear_instances() {
        for (PerfectLink instance : _instances.values()) {
            instance.close();
        }
    }

    public void send(Message msg) throws IOException {
        byte[] sig = _encryptionUnit.sign(msg.get_payload_and_nonce());
        msg.set_signature(sig);
        long nonce = ByteBuffer.wrap(msg.get_nonce()).getLong();

        long i = 0;
        while (true) {
            // This might be a future optimization to save resources
            // if (i > 3) {
            // System.out.printf("\033[91m[%s]\033[0m Destination %s is unreachable%n", _port, msg.get_host());
            // break;
            // }

            super.send(msg);

            Future<Message> future = Executors.newSingleThreadExecutor().submit(super::receiveAck);

            try {
                Message ack = future.get(TIMEOUT * (1L << i), TimeUnit.MILLISECONDS);
                ack.rebuild_signature_and_nonce();

                if (_encryptionUnit.verifySignature(ack.get_payload_and_nonce(), ack.get_signature(),
                        ack.get_host().get_pid()) && Arrays.equals(ack.get_payload(), "ack".getBytes())) {
                    long ackNonce = ByteBuffer.wrap(ack.get_nonce()).getLong();

                    if (ackNonce == nonce) {
                        return;
                    }

                    synchronized (_acks) {
                        _acks.add(ackNonce);
                    }
                } else {
                    _printer.warn("Message::send Skipping ack, bad signature - %s", msg);
                }
            } catch (InterruptedException | ExecutionException e) {
                return;
            } catch (TimeoutException e) {
                // ack might be received by another thread...
            }

            synchronized (_acks) {
                if (_acks.contains(nonce)) {
                    _acks.remove(nonce);
                    return;
                }
            }

            i++;
        }
    }

    public Message receive() throws IOException {
        while (true) {
            Message msg = super.receive();
            msg.rebuild_signature_and_nonce();

            if (!_encryptionUnit.verifySignature(msg.get_payload_and_nonce(), msg.get_signature(),
                    msg.get_host().get_pid())) {
                _printer.warn("Message::send Skipping, bad signature - %s", msg);
                continue;
            }

            Host newHost = new Host(msg.get_host().get_address(), msg.get_host().get_port() + 10000);

            Message ack = new Message("ack".getBytes(), newHost);
            ack.set_nonce(msg.get_nonce());
            ack.set_signature(_encryptionUnit.sign(ack.get_payload_and_nonce()));

            super.send(ack);

            long nonce = ByteBuffer.wrap(msg.get_nonce()).getLong();

            synchronized (_messageIds) {
                if (!_messageIds.contains(nonce)) {
                    _messageIds.add(nonce);
                    return msg;
                }
            }
        }
    }

    @Override
    public void close() {
        _instances.remove(_port);
        super.close();
    }
}
