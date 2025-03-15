package org.example.consensus;

import org.example.channel.Message;
import org.example.channel.Payload;
import org.example.consensus.block.SerializableBlock;
import org.example.consensus.block.TransactionBlock;
import org.example.consensus.exceptions.UnknownAccountException;
import org.example.consensus.transaction.Transaction;

import java.io.IOException;
import java.net.SocketException;
import java.security.PublicKey;

public class DebugIstanbulMember extends IstanbulMember {
    private final TransactionBlock _fakeBlock = new TransactionBlock(null); // For *evil* purposes

    public DebugIstanbulMember(int pid) throws SocketException {
        super(pid, "/processes.csv");
    }

    public DebugIstanbulMember(int pid, String configPath) throws SocketException {
        super(pid, configPath);
    }

    public DebugIstanbulMember(int pid, boolean evil) throws SocketException {
        super(pid, "/processes.csv");

        if (evil) {
            /* Start Fake Block Creation */
            PublicKey dstKey = _encryption_unit.getPublicKey(11);
            PublicKey srcKey = _encryption_unit.getPublicKey(12);
            Transaction tx = new Transaction(srcKey, dstKey, 1000);
            tx.sign(_encryption_unit); // Invalid signature as srcKeys != evilServerKeys
            _fakeBlock.append(tx);
            _fakeBlock.append(tx);
            _fakeBlock.append(tx);
            _fakeBlock.append(tx);
            /* End Fake Block Creation */
        }
    }

    public DebugIstanbulMember(int pid, String configPath, boolean evil) throws SocketException {
        super(pid, configPath);

        if (evil) {
            /* Start Fake Block Creation */
            PublicKey dstKey = _encryption_unit.getPublicKey(11);
            PublicKey srcKey = _encryption_unit.getPublicKey(12);
            Transaction tx = new Transaction(srcKey, dstKey, 1000);
            tx.sign(_encryption_unit); // Invalid signature as srcKeys != evilServerKeys
            _fakeBlock.append(tx);
            _fakeBlock.append(tx);
            _fakeBlock.append(tx);
            _fakeBlock.append(tx);
            /* End Fake Block Creation */
        }
    }

    @Override
    public void cleanup() {
        _receiver.interrupt();
        _consumer.interrupt();
        _perfectLink.close();
    }

    /**
     * Allows to set a wait before consuming
     **/
    public void delayed_serve_forever(long wait) throws InterruptedException {
        _receiver = new Thread(this::receive_forever);
        _consumer = new Thread(this::consume_forever);

        _receiver.start();
        Thread.sleep(wait);
        _consumer.start();
    }

    /**
     * Ignores N instances of consensus and M client messages
     */
    public void serve_but_ignore(int ignoreN, int ignoreM) {
        _receiver = new Thread(() -> receive_forever_but_ignore(ignoreN, ignoreM));
        _consumer = new Thread(this::consume_forever);

        _receiver.start();
        _consumer.start();
    }

    /**
     * Ignores N instances of consensus and M client messages
     */
    protected void receive_forever_but_ignore(int ignoreN, int ignoreM) {
        int clientMessageCounter = 0;
        while (!Thread.interrupted()) {
            try {
                Message received = _perfectLink.receive();

                if (received != null) {
                    Payload pl = Payload.parsePayload(received.get_payload());

                    if (received.is_client()) {
                        if (clientMessageCounter > ignoreM) {
                            _messages.add(received);
                        }
                        clientMessageCounter++;
                    } else if (pl.getInstance() > ignoreN) {
                        _messages.add(received);
                    }

                }
            } catch (IOException e) {
                break;
            }
        }
    }

    public int getBlockchainSize() {
        return _tes.getBlockchainSize();
    }

    public int getClientBalance(int pid) {
        try {
            PublicKey key = _encryption_unit.getPublicKey(pid);
            return _tes.check_balance(key);
        } catch (UnknownAccountException e) {
            return -1;
        }
    }

    public int getClientWeakBalance(int pid) {
        try {
            PublicKey key = _encryption_unit.getPublicKey(pid);
            return _tes.check_balance_weak(key);
        } catch (UnknownAccountException e) {
            return -1;
        }
    }

    // * * * * * * * * * EVIL VERSION * * * * * * * * * //

    public void evil_serve_forever() {
        _receiver = new Thread(this::receive_forever);
        _consumer = new Thread(this::evil_consume_forever);

        _consumer.start();
        _receiver.start();
    }

    private void evil_consume_forever() {
        while (true) {
            Message msg;

            try {
                msg = _messages.take();
            } catch (InterruptedException e) {
                break;
            }

            evil_consume(msg);
        }
    }

    private void evil_consume(Message received) {
        Payload pl = Payload.parsePayload(received.get_payload());
        OP_CODE op = OP_CODE.valueOf(pl.getOp());

        try {
            if (received.is_client()) {
                switch (op) {
                case CREATE, TRANSFER, CHECK_BALANCE, CHECK_BALANCE_WEAK -> reply_to_client(received,
                        "Don't care; Didn't ask.");

                default -> System.out
                        .println("IstanbulMember::execute_consensus - Couldn't parse client operation: " + op);
                }
            } else {
                switch (op) {
                case PRE_PREPARE -> {
                    if (!_istConsensus.isRunning())
                        _istConsensus.startOnPrePrepare(pl.getInstance(), pl.getRound(), _fakeBlock,
                                received.get_host().get_pid());
                    else
                        _istConsensus.receivedPrePrepare(pl.getInstance(), pl.getRound(), _fakeBlock,
                                received.get_host().get_pid());
                }
                case PREPARE -> _istConsensus.prepare(pl.getInstance(), pl.getRound(), _fakeBlock,
                        received.get_host().get_pid());
                case COMMIT -> _istConsensus.commit(pl.getInstance(), pl.getRound(), _fakeBlock,
                        received.get_host().get_pid());
                case ROUND_CHANGE -> _istConsensus.roundChange(pl.getInstance(), pl.getRound());
                default -> System.out
                        .println("IstanbulMember::execute_consensus - Couldn't parse server operation: " + op);
                }
            }
        } catch (IOException | InterruptedException e) {
            return;
        }

        if (_istConsensus.getDecidedValue(_consensusInstance) != null) {
            SerializableBlock outputBlock = _tes
                    .finish_produce_block(_istConsensus.getDecidedValue(_consensusInstance++));

            System.out.printf("\033[95m[EVIL]\033[0m\033[92m[%d]\033[0m CONSENSUS finished with value:\n'%s'%n\n",
                    _port, outputBlock.formattedToString());
        }
    }
}
