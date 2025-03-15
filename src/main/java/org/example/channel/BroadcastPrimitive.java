package org.example.channel;

import java.io.IOException;
import java.util.Set;
import java.util.HashSet;
import java.lang.Math;

public class BroadcastPrimitive {
    private final Set<Host> _processSet;
    private final PerfectLink _perfectLink;

    public BroadcastPrimitive(int port, Set<Host> processSet) {
        _processSet = processSet;
        _perfectLink = PerfectLink.get_instance(port);
    }

    public void broadcast(byte[] payload) throws InterruptedException {
        HashSet<Thread> threads = new HashSet<>();

        for (Host dst : _processSet) {
            threads.add(new Thread(() -> {
                try {
                    _perfectLink.send(new Message(payload, dst));
                } catch (IOException e) {
                    return;
                }
            }));
        }

        for (Thread t : threads) {
            t.start();
        }
    }

    public boolean isQuorum(int q) {
        int N = _processSet.size();
        int f = (N - 1) / 3;
        return q >= (N + f) / 2 + 1;
    }
}
