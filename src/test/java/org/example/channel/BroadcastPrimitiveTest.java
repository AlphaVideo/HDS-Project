package org.example.channel;

import org.example.encryption.EncryptionUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class BroadcastPrimitiveTest {
    private static final HashSet<Host> processSet = new HashSet<>();

    @BeforeAll
    public static void beforeAll() throws SocketException, UnknownHostException {
        processSet.add(new Host(InetAddress.getLocalHost(), 7001));
        processSet.add(new Host(InetAddress.getLocalHost(), 7002));
        processSet.add(new Host(InetAddress.getLocalHost(), 7003));
        PerfectLink.init_instance(7001, new EncryptionUnit(1));
        PerfectLink.init_instance(7002, new EncryptionUnit(2));
        PerfectLink.init_instance(7003, new EncryptionUnit(3));
    }

    @Test
    public void broadcastTest() throws InterruptedException {
        HashSet<Thread> threads = new HashSet<>();

        BroadcastPrimitive bp = new BroadcastPrimitive(7001, processSet);

        for (Host host : processSet) {
            threads.add(new Thread(() -> {
                PerfectLink pl = PerfectLink.get_instance(host.get_port());
                try {
                    List<String> msgs = new ArrayList<>();
                    Message msg = pl.receive();
                    msgs.add(new String(msg.get_payload()));
                    msg = pl.receive();
                    msgs.add(new String(msg.get_payload()));
                    msg = pl.receive();
                    msgs.add(new String(msg.get_payload()));
                    msgs.sort(Comparator.comparing(String::toString));
                    assertArrayEquals(new String[] { "Ola", "Ola", "Ola2" }, msgs.toArray());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        for (Thread t : threads) {
            t.start();
        }

        bp.broadcast("Ola".getBytes());
        bp.broadcast("Ola".getBytes());
        bp.broadcast("Ola2".getBytes());

        for (Thread t : threads) {
            t.join();
        }

        for (Host host : processSet) {
            PerfectLink.get_instance(host.get_port()).close();
        }

        PerfectLink.clear_instances();
    }

}
