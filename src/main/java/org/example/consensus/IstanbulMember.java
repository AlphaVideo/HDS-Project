package org.example.consensus;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.channel.*;
import org.example.common.ColourfulPrinter;
import org.example.consensus.block.SerializableBlock;
import org.example.consensus.exceptions.*;
import org.example.consensus.transaction.Account;
import org.example.consensus.transaction.Transaction;
import org.example.encryption.EncryptionUnit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class IstanbulMember {
    final String CURRENCY = "BTE";
    final int _pid;
    int _port;
    Set<Host> _processSet;
    final PerfectLink _perfectLink;
    final BroadcastPrimitive _broadcast;
    final IstanbulConsensus _istConsensus;
    int _consensusInstance = 1;
    final BlockingQueue<Message> _messages = new LinkedBlockingQueue<>();
    EncryptionUnit _encryption_unit;
    TokenExchangeSystem _tes;

    Thread _consumer;
    Thread _receiver;
    ColourfulPrinter _printer;
    String _configPath;

    public enum OP_CODE {
        PRE_PREPARE("PRE_PREPARE"), PREPARE("PREPARE"), COMMIT("COMMIT"), ROUND_CHANGE("ROUND_CHANGE"),
        CREATE("CREATE"), CHECK_BALANCE("CHECK_BALANCE"), CHECK_BALANCE_WEAK("CHECK_BALANCE_WEAK"),
        TRANSFER("TRANSFER");

        public final String _val;

        OP_CODE(String val) {
            this._val = val;
        }

        @Override
        public String toString() {
            return "OP_CODE[ " + _val + " ]";
        }
    }

    public IstanbulMember(int pid, String configPath) throws SocketException {
        _pid = pid;
        _configPath = configPath;
        loadConfig();
        _printer = new ColourfulPrinter(_port, true);

        _encryption_unit = new EncryptionUnit(_pid);
        PerfectLink.init_instance(_port, _encryption_unit);
        _perfectLink = PerfectLink.get_instance(_port);
        _broadcast = new BroadcastPrimitive(_port, _processSet);
        _istConsensus = new IstanbulConsensus(_pid, _broadcast, _encryption_unit);
        _tes = new TokenExchangeSystem(_encryption_unit, _printer, this);

        // Leader is miner with pid=1, which always has account
        Account minerAcc = new Account(_encryption_unit.getPublicKey(1));

        try {
            _tes.createAccount(minerAcc);
        } catch (InvalidClientSignatureException e) {
            throw new RuntimeException("ERROR: couldn't create miner account.");
        }

    }

    public void serve_forever() {
        _receiver = new Thread(this::receive_forever);
        _consumer = new Thread(this::consume_forever);

        _consumer.start();
        _receiver.start();
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

    protected void consume_forever() {
        while (!Thread.interrupted()) {
            Message msg;

            try {
                msg = _messages.take();
            } catch (InterruptedException e) {
                break;
            }

            consume(msg);
        }
    }

    private void consume(Message received) {
        Payload pl = Payload.parsePayload(received.get_payload());
        OP_CODE op = OP_CODE.valueOf(pl.getOp());

        try {
            if (received.is_client()) {
                switch (op) {
                case CREATE -> {
                    if (!_istConsensus.isRunning()) {
                        try {
                            JsonObject obj = JsonParser.parseString(pl.getValue()).getAsJsonObject();

                            boolean created = _tes.createAccount(Account.deserialize(obj));
                            if (created)
                                reply_to_client(received, String.format("Account creation now pending in BC for PID %d",
                                        received.get_host().get_pid()));
                            else
                                reply_to_client(received, "Account already registered");
                        } catch (InvalidClientSignatureException e) {
                            reply_to_client(received, "ERR: invalid signature on account request");
                        }
                    } else {
                        _messages.add(received);
                    }

                }
                case TRANSFER -> {
                    if (!_istConsensus.isRunning()) {
                        JsonObject obj = JsonParser.parseString(pl.getValue()).getAsJsonObject();

                        try {
                            int amount = obj.get("amount").getAsInt();
                            _tes.transfer(Transaction.deserialize(obj));
                            reply_to_client(received, String.format(
                                    "Transaction now pending in blockchain for: TRANSFER %d %s", amount, CURRENCY));

                        } catch (InsufficientBalanceException e) {
                            reply_to_client(received, "ERR: insufficient balance");
                        } catch (InvalidClientSignatureException e) {
                            reply_to_client(received, "ERR: invalid signature on transaction request");
                        } catch (InvalidAmountException e) {
                            reply_to_client(received, "ERR: invalid amount");
                        }
                    } else {
                        _messages.add(received);
                    }
                }
                case CHECK_BALANCE -> {
                    try {
                        int balance = _tes.check_balance(_encryption_unit.getPublicKey(received.get_host().get_pid()));
                        reply_to_client(received, String.format("current balance is %d %s", balance, CURRENCY));
                    } catch (UnknownAccountException e) {
                        reply_to_client(received, String.format("ERR: unknown %s account", e.getMessage()));
                    }
                }
                case CHECK_BALANCE_WEAK -> reply_to_client(received, _tes.get_snapshot().serialize().toString());
                default -> _printer.warn("IstanbulMember::execute_consensus - Couldn't parse client operation: %d", op);
                }
            } else {
                SerializableBlock block = SerializableBlock
                        .deserialize(JsonParser.parseString(pl.getValue()).getAsJsonObject());
                switch (op) {
                case PRE_PREPARE -> {
                    try {
                        _tes.checkBlock(block);
                    } catch (IllegalArgumentException e) {
                        _printer.warn("Invalid block: illegal arguments. Ignoring...%n");
                    } catch (BrokenBlockchainException e) {
                        _printer.warn("Invalid block: broken blockchain. Ignoring...%n");
                    } catch (InvalidFeeException e) {
                        _printer.warn("Invalid block: invalid fee. Ignoring...%n");
                    } catch (UnknownAccountException e) {
                        _printer.warn("Invalid block: unknown account. Ignoring...%n");
                    } catch (InsufficientBalanceException e) {
                        _printer.warn("Invalid block: insufficient balance. Ignoring...%n");
                    } catch (InvalidAmountException e) {
                        _printer.warn("Invalid block: invalid transaction amount. Ignoring...%n");
                    } catch (InvalidClientSignatureException e) {
                        _printer.warn("Invalid block: invalid client signature. Ignoring...%n");
                    } catch (InvalidSnapshotException e) {
                        _printer.warn("Invalid block: invalid snapshot %s. Ignoring...%n", e.getMessage());
                    }

                    if (!_istConsensus.isRunning()) {
                        // Case where server has to catch up
                        if (_consensusInstance < pl.getInstance())
                            _consensusInstance = pl.getInstance();

                        _istConsensus.startOnPrePrepare(pl.getInstance(), pl.getRound(), block,
                                received.get_host().get_pid());
                    } else {
                        _istConsensus.receivedPrePrepare(pl.getInstance(), pl.getRound(), block,
                                received.get_host().get_pid());
                    }
                }
                case PREPARE -> _istConsensus.prepare(pl.getInstance(), pl.getRound(), block,
                        received.get_host().get_pid());
                case COMMIT -> _istConsensus.commit(pl.getInstance(), pl.getRound(), block,
                        received.get_host().get_pid());
                case ROUND_CHANGE -> _istConsensus.roundChange(pl.getInstance(), pl.getRound());
                default -> _printer.warn("IstanbulMember::execute_consensus - Couldn't parse server operation: %d", op);
                }
            }
        } catch (IOException | InterruptedException e) {
            return;
        }

        if (_istConsensus.getDecidedValue(_consensusInstance) != null) {
            SerializableBlock outputBlock = _tes
                    .finish_produce_block(_istConsensus.getDecidedValue(_consensusInstance));

            _printer.ok("CONSENSUS finished with value:%n%s", outputBlock.formattedToString());

            _consensusInstance++;

            _tes.try_produce_snapshot();

        }
    }

    public void start_consensus_with_block(SerializableBlock val) {

        if (_istConsensus.isRunning())
            throw new RuntimeException("ERR: Consensus already running!");

        try {
            _istConsensus.start(_consensusInstance, val);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    protected void reply_to_client(Message msg, String reply) {
        Host client = msg.get_host();
        Payload sent = Payload.parsePayload(msg.get_payload());
        Host new_host = new Host(client.get_address(), client.get_port() - 10000);
        Payload pl = new Payload("REPLY", reply, sent.getId());
        Message to_send = new Message(pl.getPayload(), new_host);
        try {
            _perfectLink.send(to_send);
        } catch (IOException e) {
            _printer.warn("Client is offline: %s", new_host);
        }
    }

    private void loadConfig() {
        _processSet = new HashSet<>();

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    Objects.requireNonNull(IstanbulMember.class.getResourceAsStream(_configPath))));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                String[] params = line.split(",");

                if (params.length != 3)
                    throw new RuntimeException("Invalid config file.");

                _processSet.add(new Host(params[1], Integer.parseInt(params[2])));

                if (Integer.parseInt(params[0]) == _pid)
                    _port = Integer.parseInt(params[2]);
            }
            reader.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void cleanup() {
        try {
            _consumer.join();
            _receiver.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        _perfectLink.close();
    }
}
