package org.example.client;

import com.google.gson.JsonParser;
import org.example.channel.*;
import org.example.common.ColourfulPrinter;
import org.example.consensus.block.Snapshot;
import org.example.consensus.transaction.Account;
import org.example.consensus.transaction.Transaction;
import org.example.encryption.EncryptionUnit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketException;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Client {
    protected final BroadcastPrimitive _broadcast;
    protected final PerfectLink _perfectLink;
    protected int _serverCount;
    protected int _mid = 1;
    protected Thread _receiver;
    protected final BlockingQueue<Message> _messages = new LinkedBlockingQueue<>();
    protected EncryptionUnit _unit;
    protected final int _pid;
    private final ColourfulPrinter _printer;
    private final String _configPath;

    public Client(int port, String configPath) throws SocketException {
        _configPath = configPath;
        _unit = new EncryptionUnit(port % 1000);
        PerfectLink.init_instance(port, _unit);
        _pid = port % 1000;

        _perfectLink = PerfectLink.get_instance(port);
        _broadcast = new BroadcastPrimitive(port, loadConfig());

        _printer = new ColourfulPrinter("C " + _pid, true);
    }

    protected void receive_forever() {
        while (!Thread.interrupted()) {
            try {
                Message received = _perfectLink.receive();

                if (received != null) {
                    _messages.add(received);
                }
            } catch (IOException e) {
                break;
            }
        }
    }

    public void do_forever() {

        Scanner input = new Scanner(System.in);
        String line;
        String[] parsed;

        _receiver = new Thread(this::receive_forever);
        _receiver.start();

        System.out.println("Running Client");
        System.out.println("""
                []===Available commands===:
                || create                          => Creates new account with client PublicKey.
                || transfer [dst:pid] [amount:int] => Transfers amount to chosen destination account.
                || read-weak                       => Returns current client balance. (weak consistency)
                || read-strong                     => Returns current client balance. (strong consistency)
                || CTRL-C                         => Exits application.
                """);
        System.out.println("Please remember that transactions incur the fee of 1 token and that starting balance=100.");
        System.out.print("> ");

        while (input.hasNext()) {
            line = input.nextLine();
            parsed = line.split(" ");

            if (parsed.length == 0)
                break;

            switch (parsed[0]) {
            case "create" -> create_account();
            case "transfer" -> transfer(Integer.parseInt(parsed[1]), Integer.parseInt(parsed[2]));
            case "read-weak" -> check_balance_weak();
            case "read-strong" -> check_balance();
            default -> _printer.warn("Unknown command");
            }

            System.out.print("> ");
        }
    }

    protected void send_and_wait_for_consensus(Payload payload, int stopAt) {
        _printer.debug("Broadcasting: %s", payload);
        try {
            _broadcast.broadcast(payload.getPayload());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        HashMap<String, Integer> counter = new HashMap<>();

        for (int i = 0; i < stopAt; i++) {
            Message m;

            try {
                m = _messages.take();
            } catch (InterruptedException e) {
                break;
            }

            Payload pl = Payload.parsePayload(m.get_payload());

            if (pl.getId() != _mid) {
                i--;
                continue;
            }

            _printer.debug("Received: %s", m);

            if (counter.containsKey(pl.getValue())) {
                counter.put(pl.getValue(), counter.get(pl.getValue()) + 1);
            } else {
                counter.put(pl.getValue(), 1);
            }
        }

        int max_count = 0;
        String blockchain = "";

        for (String key : counter.keySet()) {
            if (counter.get(key) > max_count) {
                max_count = counter.get(key);
                blockchain = key;
            }
        }

        _printer.debug("Received %d of %d messages, reached quorum.", stopAt, _serverCount);
        if (blockchain.startsWith("ERR"))
            _printer.error("Received: %s", blockchain);
        else
            _printer.ok("Received: %s", blockchain);
    }

    protected void create_account() {
        int stopAt = byzantineQuorumOf(_serverCount);

        Account acc = new Account(_unit.getOwnPublicKey());
        acc.sign(_unit);

        Payload payload = new Payload("CREATE", acc.serialize().toString(), _mid);
        send_and_wait_for_consensus(payload, stopAt);
        _mid++;
    }

    protected void check_balance_weak() {
        Payload payload = new Payload("CHECK_BALANCE_WEAK", "", _mid);

        _printer.debug("Broadcasting: %s", payload);
        try {
            _broadcast.broadcast(payload.getPayload());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        Payload pl = null;
        int retries = 0;
        while (true) {
            Message m;

            try {
                m = _messages.take();
            } catch (InterruptedException e) {
                break;
            }

            pl = Payload.parsePayload(m.get_payload());

            if (pl.getId() == _mid) {
                _printer.debug("Received: %s", m);

                Snapshot snapshot = Snapshot
                        .deserialize(JsonParser.parseString(Objects.requireNonNull(pl).getValue()).getAsJsonObject());

                if (!snapshot.get_balance().containsKey(_unit.getOwnPublicKey())) {
                    if (!_broadcast.isQuorum(retries)) {
                        _printer.error("ERROR: unknown account. Retrying...");
                        retries += 1;
                        continue;
                    } else {
                        _printer.error("ERROR: unknown account.");
                        _mid++;
                        return;
                    }
                }

                if (!_broadcast.isQuorum(snapshot.get_valid_signatures(_unit))) {
                    _printer.error("ERROR: received snapshot with invalid signatures. Retrying...");
                    continue;
                }

                _printer.ok("current balance is %d", snapshot.get_balance().get(_unit.getOwnPublicKey()));
                _mid++;
                return;
            }
        }
    }

    protected void check_balance() {
        int stopAt = byzantineQuorumOf(_serverCount);

        Payload payload = new Payload("CHECK_BALANCE", "", _mid);
        send_and_wait_for_consensus(payload, stopAt);
        _mid++;
    }

    protected void transfer(int dest, int amount) {
        int stopAt = byzantineQuorumOf(_serverCount);
        PublicKey dst = _unit.getPublicKey(dest);

        if (dst == null) {
            _printer.warn("No public key found associated with destination pid.");
            return;
        }

        Transaction tx = new Transaction(_unit.getOwnPublicKey(), _unit.getPublicKey(dest), amount);
        tx.sign(_unit);

        Payload payload = new Payload("TRANSFER", tx.serialize().toString(), _mid);

        send_and_wait_for_consensus(payload, stopAt);

        _mid++;
    }

    protected Set<Host> loadConfig() {
        Set<Host> _processSet = new HashSet<>();

        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(Objects.requireNonNull(Client.class.getResourceAsStream(_configPath))));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                String[] params = line.split(",");

                if (params.length != 3)
                    throw new RuntimeException("Invalid config file.");

                _processSet.add(new Host(params[1], Integer.parseInt(params[2])));
            }
            reader.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        _serverCount = _processSet.size();
        return _processSet;
    }

    protected int byzantineQuorumOf(int n) {
        return 2 * (n - 1) / 3 + 1;
    }

    public void shutdown() {
        if (_receiver != null)
            _receiver.interrupt();
        _perfectLink.close();
    }
}
