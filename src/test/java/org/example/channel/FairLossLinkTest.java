package org.example.channel;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.*;

public class FairLossLinkTest {
    @Test
    public void testOneConnection() throws UnknownHostException, SocketException, InterruptedException {
        Host host1 = new Host(InetAddress.getLocalHost(), 7001);
        Host host2 = new Host(InetAddress.getLocalHost(), 7002);

        FairLossLink fll1 = new FairLossLink(host1.get_port());
        FairLossLink fll2 = new FairLossLink(host2.get_port());

        Thread server1 = new Thread(() -> {
            try {
                Message msg = fll1.receive();
                assertArrayEquals("Ola".getBytes(), msg.get_payload());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        Thread client1 = new Thread(() -> {
            try {
                fll1.send(new Message("Adeus".getBytes(), host2));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        Thread server2 = new Thread(() -> {
            try {
                Message msg = fll2.receive();
                assertArrayEquals("Adeus".getBytes(), msg.get_payload());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        Thread client2 = new Thread(() -> {
            try {
                fll2.send(new Message("Ola".getBytes(), host1));
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

        fll1.close();
        fll2.close();
    }

}
