package org.example.consensus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.SocketException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HardcoreConsensusNetworkTest {
    DebugIstanbulMember server1;
    DebugIstanbulMember server2;
    DebugIstanbulMember server3;
    DebugIstanbulMember server4;
    DebugIstanbulMember server5;
    DebugIstanbulMember server6;
    DebugIstanbulMember server7;
    DebugIstanbulMember server8;
    DebugIstanbulMember server9;
    DebugIstanbulMember server10;
    DebugClient client11;
    DebugClient client12;
    DebugClient client13;

    @BeforeEach
    public void beforeEach() {
        try {
            server1 = new DebugIstanbulMember(1, "/processes2.csv");
            server2 = new DebugIstanbulMember(2, "/processes2.csv");
            server3 = new DebugIstanbulMember(3, "/processes2.csv");
            server4 = new DebugIstanbulMember(4, "/processes2.csv", true);
            server5 = new DebugIstanbulMember(5, "/processes2.csv");
            server6 = new DebugIstanbulMember(6, "/processes2.csv");
            server7 = new DebugIstanbulMember(7, "/processes2.csv", true);
            server8 = new DebugIstanbulMember(8, "/processes2.csv");
            server9 = new DebugIstanbulMember(9, "/processes2.csv");
            server10 = new DebugIstanbulMember(10, "/processes2.csv", true);
            client11 = new DebugClient(7011, "/processes2.csv");
            client12 = new DebugClient(7012, "/processes2.csv");
            client13 = new DebugClient(7013, "/processes2.csv");
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    public void afterEach() {
        server1.cleanup();
        server2.cleanup();
        server3.cleanup();
        server4.cleanup();
        server5.cleanup();
        server6.cleanup();
        server7.cleanup();
        server8.cleanup();
        server9.cleanup();
        server10.cleanup();
        client11.shutdown();
        client12.shutdown();
        client13.shutdown();
    }

    @Test
    @DisplayName("Testing Client 11, 12 and 13 sending concurrent requests to 10 different servers.")
    public void testConcurrentClientRequests() throws InterruptedException {
        server1.serve_forever();
        server2.serve_forever();
        server3.serve_forever();
        server4.serve_forever();
        server5.serve_forever();
        server6.serve_forever();
        server7.serve_forever();
        server8.serve_forever();
        server9.serve_forever();
        server10.serve_forever();
        client11.debug_client_request("create");
        client12.debug_client_request("create");
        client13.debug_client_request("create");

        Thread t1 = new Thread(() -> {
            client11.debug_client_request("transfer 12 3");
            client11.debug_client_request("transfer 12 6");
            client11.debug_client_request("transfer 13 12");
        });

        Thread t2 = new Thread(() -> {
            client12.debug_client_request("transfer 11 5");
            client12.debug_client_request("transfer 13 10");
            client12.debug_client_request("transfer 11 20");
        });

        Thread t3 = new Thread(() -> {
            client13.debug_client_request("transfer 11 7");
            client13.debug_client_request("transfer 12 14");
        });

        t1.start();
        t2.start();
        t3.start();

        /**
         * Expected blockchain will have: 4 accounts, 4 account fees, 8 transfers, 8 transfer fees. Total = 24 txns
         * transactions => 6 blocks
         */

        Thread.sleep(7500);

        assertEquals(6, server1.getBlockchainSize(),
                "Blockchain should have successfully appended 6 blocks and have size=6.");

        assertEquals(111, server1.getClientBalance(1),
                "Blockchain should have handled concurrency. Leader should now have balance=111.");

        assertEquals(107, server1.getClientBalance(11),
                "Blockchain should have handled concurrency. Client 11 should now have balance=107.");

        assertEquals(84, server1.getClientBalance(12),
                "Blockchain should have handled concurrency. Client 12 should now have balance=84.");

        assertEquals(98, server1.getClientBalance(13),
                "Blockchain should have handled concurrency. Client 13 should now have balance=98.");

        t1.join();
        t2.join();
        t3.join();
    }
}
