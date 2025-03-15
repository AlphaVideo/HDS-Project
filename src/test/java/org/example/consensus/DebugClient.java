package org.example.consensus;

import org.example.channel.*;
import org.example.client.Client;
import org.example.consensus.transaction.Account;
import org.example.consensus.transaction.Transaction;
import java.security.PublicKey;

import java.net.SocketException;

public class DebugClient extends Client {

    public DebugClient(int port) throws SocketException {
        super(port, "/processes.csv");
    }

    public DebugClient(int port, String configPath) throws SocketException {
        super(port, configPath);
    }

    public void debug_client_request(String command) {
        _receiver = new Thread(this::receive_forever);
        _receiver.start();

        String[] parsed = command.split(" ");

        if (parsed.length == 0)
            return;

        switch (parsed[0]) {
        case "create" -> create_account();
        case "transfer" -> transfer(Integer.parseInt(parsed[1]), Integer.parseInt(parsed[2]));
        case "read-weak" -> check_balance_weak();
        case "read-strong" -> check_balance();
        default -> System.err.println("Unknown command.");
        }
    }

    public void evil_client_request(String command) {
        _receiver = new Thread(this::receive_forever);
        _receiver.start();

        String[] parsed = command.split(" ");

        if (parsed.length == 0)
            return;

        switch (parsed[0]) {
        case "create" -> create_account_evil(Integer.parseInt(parsed[1]));
        case "transfer-from" -> transfer_evil(Integer.parseInt(parsed[1]), Integer.parseInt(parsed[2]));
        case "read-weak" -> check_balance_weak();
        case "read-strong" -> check_balance();
        default -> System.err.println("Unknown command.");
        }
    }

    protected void create_account_evil(int victim_id) {
        int stopAt = byzantineQuorumOf(_serverCount);

        // Unauthorized account creation
        Account acc = new Account(_unit.getPublicKey(victim_id));
        acc.sign(_unit);

        Payload payload = new Payload("CREATE", acc.serialize().toString(), _mid);
        send_and_wait_for_consensus(payload, stopAt);
        _mid++;
    }

    protected void transfer_evil(int victim, int amount) {
        int stopAt = byzantineQuorumOf(_serverCount);
        PublicKey victimKey = _unit.getPublicKey(victim);

        if (victimKey == null) {
            System.out.println("No public key found associated with victim pid.");
            return;
        }

        Transaction tx = new Transaction(victimKey, _unit.getOwnPublicKey(), amount);
        tx.sign(_unit); // Unauthorized transfer => invalid signature

        Payload payload = new Payload("TRANSFER", tx.serialize().toString(), _mid);

        send_and_wait_for_consensus(payload, stopAt);

        _mid++;
    }
}
