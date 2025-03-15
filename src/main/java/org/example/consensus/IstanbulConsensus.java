package org.example.consensus;

import org.example.channel.Payload;
import org.example.channel.BroadcastPrimitive;

import java.lang.Math;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import com.google.common.collect.Multimap;
import com.google.common.collect.ArrayListMultimap;
import org.example.consensus.block.SerializableBlock;
import org.example.consensus.block.Snapshot;
import org.example.encryption.EncryptionUnit;

import java.io.IOException;

public class IstanbulConsensus {
    private static final int TIMEOUT_BASE = 60000;

    private final int _pid;
    private final BroadcastPrimitive _bp;
    private int _consInstance;
    private int _consRound;
    private final Multimap<Integer, SerializableBlock> _prepared = ArrayListMultimap.create();
    private SerializableBlock _prepared_value = null;
    private final Multimap<Integer, SerializableBlock> _commitReq = ArrayListMultimap.create();
    private final HashMap<Integer, SerializableBlock> _decided = new HashMap<>();
    private final HashSet<Integer> _alreadyPrepared = new HashSet<>();
    private final HashSet<Integer> _alreadyCommited = new HashSet<>();
    private SerializableBlock _input;
    private boolean _isLeader;
    private boolean _isRunning;
    private Timer _timer;
    private EncryptionUnit _unit;

    private final ActionListener _timeoutEvent = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent ae) {
            // Round-Change on timeout
            _timer.stop();

            try {
                Payload pl = new Payload("ROUND_CHANGE", _consInstance, _consRound, _prepared_value);
                _bp.broadcast(pl.getPayload());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    };

    public IstanbulConsensus(int pid, BroadcastPrimitive bp, EncryptionUnit unit) {
        _pid = pid;
        _bp = bp;
        _unit = unit;
    }

    public void start(int inst, SerializableBlock inputVal) throws IOException, InterruptedException {
        _consInstance = inst;
        _input = inputVal;
        _consRound = 1;
        _isRunning = true;
        _prepared_value = inputVal;

        cleanupStructures();

        _isLeader = (_pid == 1);

        if (_isLeader) {
            Payload pl = new Payload("PRE_PREPARE", _consInstance, _consRound, _input);
            _bp.broadcast(pl.getPayload());
        }

        _timer = new Timer(calculateTimeout(), _timeoutEvent);
        _timer.start();
    }

    public void startOnPrePrepare(int inst, int round, SerializableBlock val, int senderPid)
            throws IOException, InterruptedException {
        _consInstance = inst;
        _input = val;
        _consRound = round;
        _isRunning = true;

        cleanupStructures();

        _isLeader = (_pid == 1);

        _timer = new Timer(calculateTimeout(), _timeoutEvent);
        _timer.start();

        receivedPrePrepare(inst, round, val, senderPid);
    }

    public void receivedPrePrepare(int inst, int round, SerializableBlock val, int senderPid)
            throws IOException, InterruptedException {
        if (!verifyLeader(senderPid))
            return;
        else if (inst != _consInstance)
            return;

        _timer.restart();

        Payload pl = new Payload("PREPARE", inst, round, val);
        _bp.broadcast(pl.getPayload());
    }

    public void prepare(int inst, int round, SerializableBlock val, int senderPid)
            throws IOException, InterruptedException {

        // Ignore if not correct instance or if repeat
        if (inst != _consInstance)
            return;
        else if (_alreadyPrepared.contains(senderPid))
            return;

        _prepared.put(round, val);
        _alreadyPrepared.add(senderPid);

        // If received prepare set hit a quorum, try to commit
        if (_bp.isQuorum(_prepared.get(round).size())) {
            SerializableBlock block = getMostCommon(_prepared.get(round));

            if (val instanceof Snapshot snapshot) {
                snapshot.sign(_unit);
                block = snapshot;
            }

            _prepared_value = block;

            Payload pl = new Payload("COMMIT", inst, round, block);
            _bp.broadcast(pl.getPayload());
        }
    }

    public void commit(int inst, int round, SerializableBlock val, int senderPid)
            throws IOException, InterruptedException {

        // Ignore if not correct instance or if repeat
        if (inst != _consInstance)
            return;
        else if (_alreadyCommited.contains(senderPid))
            return;

        _commitReq.put(round, val);
        _alreadyCommited.add(senderPid);

        // If received commit set hit a quorum, decide
        if (_bp.isQuorum(_commitReq.get(round).size())) {
            _timer.stop();

            SerializableBlock block = getMostCommon(_commitReq.get(round));

            if (block instanceof Snapshot snapshot) {
                for (SerializableBlock v : _commitReq.get(round)) {
                    if (v instanceof Snapshot s) {
                        if (snapshot.equals(s)) {
                            for (var entry : s.get_signatures().entrySet()) {
                                if (snapshot.verify_signature(_unit, entry.getKey(), entry.getValue()))
                                    snapshot.addSignature(entry.getKey(), entry.getValue());
                            }
                        }
                    }
                }

                if (!_bp.isQuorum(snapshot.get_signatures().entrySet().size()))
                    throw new RuntimeException(
                            "COMMIT FAILED: THERE WAS NOT A QUORUM OF VALID SIGNATURES FOR SNAPSHOT");

                block = snapshot;
            }

            _decided.put(inst, block);
            _isRunning = false;
        }

    }

    public SerializableBlock getDecidedValue(int inst) {
        return _decided.getOrDefault(inst, null);
    }

    public void roundChange(int inst, int round) throws InterruptedException {

        // Only round change on requests from processes in the same round
        if (inst == _consInstance && _consRound == round) {
            _consRound++;

            // We only need to clear the dupe request detection structures
            _alreadyPrepared.clear();
            _alreadyCommited.clear();
            if (_isLeader && getDecidedValue(_consInstance) == null) {
                _timer.restart();
                Payload pl = new Payload("PRE_PREPARE", _consInstance, _consRound, _input);
                _bp.broadcast(pl.getPayload());
            }
        }

    }

    // Exponential function based on round
    public int calculateTimeout() {
        return (int) Math.round((Math.pow(2, (_consRound - 1)) * TIMEOUT_BASE));
    }

    public boolean verifyLeader(int pid) {
        return (pid == 1);
    }

    public boolean isRunning() {
        return _isRunning;
    }

    private SerializableBlock getMostCommon(Collection<SerializableBlock> vals) {
        HashMap<SerializableBlock, Integer> counter = new HashMap<>();

        for (SerializableBlock _val : vals) {
            if (counter.containsKey(_val)) {
                counter.put(_val, counter.get(_val) + 1);
            } else {
                counter.put(_val, 1);
            }
        }

        SerializableBlock decided_val = null;
        int max_count = 0;

        for (SerializableBlock _val : counter.keySet()) {
            if (counter.get(_val) > max_count) {
                max_count = counter.get(_val);
                decided_val = _val;
            }
        }

        return decided_val;
    }

    // Call me at the beginning of new INSTANCES
    private void cleanupStructures() {
        _alreadyPrepared.clear();
        _alreadyCommited.clear();
        _prepared.clear();
        _commitReq.clear();
    }
}
