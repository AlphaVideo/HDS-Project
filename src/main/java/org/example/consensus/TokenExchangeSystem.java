package org.example.consensus;

import org.example.common.ColourfulPrinter;
import org.example.consensus.block.SerializableBlock;
import org.example.consensus.block.Snapshot;
import org.example.consensus.exceptions.*;
import org.example.encryption.EncryptionUnit;
import org.example.consensus.block.TransactionBlock;
import org.example.consensus.transaction.Account;
import org.example.consensus.transaction.SerializableTransaction;
import org.example.consensus.transaction.Transaction;

import java.security.PublicKey;
import java.util.*;

public class TokenExchangeSystem {
    public final static int INITIAL_BALANCE = 100;
    private final int FEE = 1;
    private final int SNAPSHOT_STEP = 4;
    private final Map<PublicKey, Integer> _balance;
    private TransactionBlock _currentBlock;
    private final List<TransactionBlock> _blockchain;
    private final EncryptionUnit _unit;
    private final IstanbulMember _member;
    private final ColourfulPrinter _printer;
    private Snapshot _snapshot;

    public TokenExchangeSystem(EncryptionUnit unit, ColourfulPrinter printer, IstanbulMember member) {
        _balance = new HashMap<>();
        _blockchain = new ArrayList<>();
        _currentBlock = new TransactionBlock(null);
        _unit = unit;
        _member = member;
        _printer = printer;
        _snapshot = new Snapshot();
    }

    public Snapshot get_snapshot() {
        return _snapshot;
    }

    public boolean createAccount(Account acc) throws InvalidClientSignatureException {
        PublicKey pubKey = acc.get_client_key();

        // Only verify signature if the account isn't leader's
        if (!pubKey.equals(_unit.getPublicKey(1))) {
            if (!acc.verifySignature(_unit, pubKey))
                throw new InvalidClientSignatureException(acc.get_client_signature());
        }

        if (_balance.containsKey(pubKey))
            return false;

        // Leader produces block
        PublicKey leader = _unit.getPublicKey(1);
        Transaction acc_fee = new Transaction(acc, FEE, leader);

        _currentBlock.append(acc);
        _currentBlock.append(acc_fee);

        try_produce_block();

        return true;
    }

    public int check_balance(PublicKey publicKey) throws UnknownAccountException {
        if (!_balance.containsKey(publicKey))
            throw new UnknownAccountException("client");

        return _balance.get(publicKey);
    }

    public int check_balance_weak(PublicKey publicKey) throws UnknownAccountException {
        if (!_snapshot.get_balance().containsKey(publicKey))
            throw new UnknownAccountException("client");

        return _snapshot.get_balance().get(publicKey);
    }

    private void try_produce_block() {
        if (!_currentBlock.isFull())
            return;

        _printer.debug("Trying to produce block");

        try {
            checkBlock(_currentBlock);
        } catch (IllegalArgumentException e) {
            discardCurrentBlock();
            _printer.warn("Invalid block: %s. Discarding...%n", e.getMessage());
            return;
        } catch (BrokenBlockchainException e) {
            discardCurrentBlock();
            _printer.warn("Invalid block: broken blockchain. Discarding...%n");
            return;
        } catch (InvalidFeeException e) {
            discardCurrentBlock();
            _printer.warn("Invalid block: invalid fee. Discarding...%n");
            return;
        } catch (UnknownAccountException e) {
            discardCurrentBlock();
            _printer.warn("Invalid block: unknown account. Discarding...%n");
            return;
        } catch (InsufficientBalanceException e) {
            discardCurrentBlock();
            _printer.warn("Invalid block: insufficient balance. Discarding...%n");
            return;
        } catch (InvalidAmountException e) {
            discardCurrentBlock();
            _printer.warn("Invalid block: invalid transaction amount. Discarding...%n");
            return;
        } catch (InvalidClientSignatureException e) {
            discardCurrentBlock();
            _printer.warn("Invalid block: invalid client signature. Discarding...%n");
            return;
        } catch (InvalidSnapshotException e) {
            _printer.warn("Invalid block: invalid snapshot %s. Discarding...%n", e.getMessage());
            return;
        }

        _member.start_consensus_with_block(_currentBlock);
    }

    public void discardCurrentBlock() {
        _currentBlock = new TransactionBlock(_blockchain.get(getBlockchainSize() - 1).hash());
    }

    public void try_produce_snapshot() {
        if (getBlockchainSize() % SNAPSHOT_STEP == 0) {
            if (!(_snapshot.get_balance().size() == _balance.size() && _snapshot.get_balance().entrySet().stream()
                    .allMatch(e -> e.getValue().equals(_balance.get(e.getKey()))))) {
                Snapshot snapshot = new Snapshot(_balance);
                _printer.debug("Trying to produce snapshot... %s", snapshot.formattedToString());
                snapshot.sign(_unit);
                _member.start_consensus_with_block(snapshot);
            }
        }
    }

    public SerializableBlock finish_produce_block(SerializableBlock block) {
        if (block instanceof TransactionBlock decided) {
            _currentBlock = new TransactionBlock(decided.hash());
            _blockchain.add(decided);

            try {
                updateState(decided);
            } catch (BrokenBlockchainException | InvalidBlockException e) {
                throw new RuntimeException(e);
            }
        } else if (block instanceof Snapshot snapshot) {
            _snapshot = snapshot;
        } else {
            throw new RuntimeException("finish_produce_block block is not valid");
        }

        return block;
    }

    public void transfer(Transaction tx)
            throws InsufficientBalanceException, InvalidClientSignatureException, InvalidAmountException {

        PublicKey srcKey = tx.get_source();
        int amount = tx.get_amount();

        if (amount <= 0)
            throw new InvalidAmountException();

        if (!tx.verifySignature(_unit, srcKey))
            throw new InvalidClientSignatureException(tx.get_client_signature());

        PublicKey leader = _unit.getPublicKey(1);
        Transaction tx_fee = new Transaction(tx, FEE, leader);

        _currentBlock.append(tx);
        _currentBlock.append(tx_fee);

        try_produce_block();
    }

    /**
     * Validates block.
     */

    public void checkBlock(SerializableBlock b) throws BrokenBlockchainException, InvalidFeeException,
            UnknownAccountException, InsufficientBalanceException, InvalidAmountException,
            InvalidClientSignatureException, IllegalArgumentException, InvalidSnapshotException {

        if (b instanceof TransactionBlock block) {
            byte[] hashPrevious;
            HashMap<PublicKey, Integer> stateCopy = new HashMap<>(_balance);

            if (_blockchain.size() == 0)
                hashPrevious = null;
            else
                hashPrevious = _blockchain.get(_blockchain.size() - 1).hash();

            if (!Arrays.equals(block.getHashPrevious(), hashPrevious)) {
                throw new BrokenBlockchainException();
            }

            int transaction_count = block.getTransactionCount();
            for (int i = 0; i < transaction_count; i++) {
                SerializableTransaction trans = block.getTransaction(i);

                if (trans instanceof Transaction tx) {
                    PublicKey sender = tx.get_source();
                    PublicKey recipient = tx.get_destination();
                    int amount = tx.get_amount();
                    int newBalanceSender, newBalanceRecipient;

                    if (tx.get_client_signature() == null) {
                        if (i == 0) {
                            throw new InvalidFeeException("First transaction in block cannot be a fee");
                        }

                        if (tx.get_amount() != FEE) {
                            throw new InvalidFeeException("Invalid FEE amount");
                        }

                        // Check if leader is the receiver
                        if (!_unit.getPublicKey(1).equals(recipient)) {
                            throw new InvalidFeeException("Receiver should be group leader");
                        }

                        SerializableTransaction prev = block.getTransaction(i - 1);

                        if (prev instanceof Transaction prevTrans) {
                            if (!prevTrans.get_source().equals(sender))
                                throw new InvalidFeeException("Fee source doesn't match previous transaction source");
                        } else if (prev instanceof Account prevAcc) {
                            if (!prevAcc.get_destination().equals(sender))
                                throw new InvalidFeeException("Fee source doesn't match previous transaction source");
                        } else {
                            throw new IllegalArgumentException(
                                    "SerializableTransaction is of neither Transaction nor Account type");
                        }
                    } else if (!tx.verifySignature(_unit, sender)) {
                        throw new InvalidClientSignatureException(tx.get_client_signature());
                    }

                    if (!stateCopy.containsKey(sender))
                        throw new UnknownAccountException("sender");
                    else
                        newBalanceSender = stateCopy.get(sender) - amount;

                    if (!stateCopy.containsKey(recipient))
                        throw new UnknownAccountException("recipient");
                    else
                        newBalanceRecipient = stateCopy.get(sender) + amount;

                    if (newBalanceRecipient < 0 || newBalanceSender < 0)
                        throw new InsufficientBalanceException();

                    stateCopy.put(sender, newBalanceSender);
                    stateCopy.put(recipient, newBalanceRecipient);
                } else if (trans instanceof Account acc) {
                    PublicKey receiver = acc.get_destination();
                    int amount = acc.get_amount();

                    if (amount < 0)
                        throw new InvalidAmountException();

                    stateCopy.put(receiver, amount);
                } else {
                    throw new IllegalArgumentException(
                            "SerializableTransaction is of neither Transaction nor Account type");
                }

            }
        } else if (b instanceof Snapshot snapshot) {
            // Balances should be equal when processing snapshots
            if (snapshot.get_balance().size() != _balance.size()) {
                throw new InvalidSnapshotException("size");
            }

            if (!snapshot.get_balance().entrySet().stream()
                    .allMatch(e -> e.getValue().equals(_balance.get(e.getKey())))) {
                throw new InvalidSnapshotException("contents");
            }

        }
    }

    private void updateState(TransactionBlock newBlock) throws BrokenBlockchainException, InvalidBlockException {
        for (SerializableTransaction trans : newBlock.getTransactions()) {
            if (trans instanceof Transaction tx) {
                PublicKey sender = tx.get_source();
                PublicKey recipient = tx.get_destination();
                int amount = tx.get_amount();

                _balance.put(sender, _balance.getOrDefault(sender, INITIAL_BALANCE) - amount);
                _balance.put(recipient, _balance.getOrDefault(recipient, INITIAL_BALANCE) + amount);
            } else if (trans instanceof Account acc) {
                PublicKey recipient = acc.get_destination();
                int amount = acc.get_amount();

                _balance.put(recipient, amount);
            } else {
                _printer.error("Cause: UpdateState - Invalid block type");
            }
        }
        _printer.debug("Successfully updated state");
    }

    public int getBlockchainSize() {
        return _blockchain.size();
    }
}
