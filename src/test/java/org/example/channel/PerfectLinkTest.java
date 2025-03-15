package org.example.channel;

import org.example.encryption.EncryptionUnit;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class PerfectLinkTest {
    @Test
    public void testOneConnection() throws UnknownHostException, SocketException, InterruptedException {
        Host host1 = new Host(InetAddress.getLocalHost(), 7001);
        Host host2 = new Host(InetAddress.getLocalHost(), 7002);

        PerfectLink pl1 = new PerfectLink(host1.get_port(), new EncryptionUnit(host1.get_pid()));
        PerfectLink pl2 = new PerfectLink(host2.get_port(), new EncryptionUnit(host2.get_pid()));

        Thread server1 = new Thread(() -> {
            try {
                Message msg = pl1.receive();
                assertArrayEquals("MSG1".getBytes(), msg.get_payload());
                msg = pl1.receive();
                assertArrayEquals("MSG2".getBytes(), msg.get_payload());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        Thread client1 = new Thread(() -> {
            try {
                pl1.send(new Message("msg1".getBytes(), host2));
                pl1.send(new Message("msg2".getBytes(), host2));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        Thread server2 = new Thread(() -> {
            try {
                Message msg = pl2.receive();
                assertArrayEquals("msg1".getBytes(), msg.get_payload());
                msg = pl2.receive();
                assertArrayEquals("msg2".getBytes(), msg.get_payload());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        Thread client2 = new Thread(() -> {
            try {
                pl2.send(new Message("MSG1".getBytes(), host1));
                pl2.send(new Message("MSG2".getBytes(), host1));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        server1.start();
        server2.start();
        client1.start();
        client2.start();

        server1.join();
        server2.join();
        client1.join();
        client2.join();

        pl1.close();
        pl2.close();
    }

}
