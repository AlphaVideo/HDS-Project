package org.example.consensus;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import java.net.SocketException;

public class ConsensusNetworkTest {
    DebugIstanbulMember server1;
    DebugIstanbulMember server2;
    DebugIstanbulMember server3;
    DebugIstanbulMember server4;
    DebugClient client11;
    DebugClient client12;

    @BeforeEach
    public void beforeEach() {
        try {
            server1 = new DebugIstanbulMember(1);
            server2 = new DebugIstanbulMember(2);
            server3 = new DebugIstanbulMember(3);
            server4 = new DebugIstanbulMember(4, true);
            client11 = new DebugClient(7011);
            client12 = new DebugClient(7012);
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
        client11.shutdown();
        client12.shutdown();
    }

    @Test
    @DisplayName("Testing consensus with ServerPID=4 being delayed 2s")
    public void testOneDelayedServer() throws InterruptedException {
        final String COMMAND = "transfer 12 10";
        server1.serve_forever();
        server2.serve_forever();
        server3.serve_forever();
        Thread t1 = new Thread(() -> {
            try {
                server4.delayed_serve_forever(2000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        t1.start();
        client11.debug_client_request("create");
        client12.debug_client_request("create");

        client11.debug_client_request(COMMAND);
        client11.debug_client_request(COMMAND);

        // Expected Block chain:
        // Block1: {Account(Leader); Account_Fee(Leader); Account(C11); Account_Fee(C11)}
        // Block2: {Account(C12); Account_Fee(C12); Transfer(C11,C12,10); Transfer_Fee(C11,C12,10)}

        // Not yet appended: Transfer(C11,C12,10), Transfer_Fee(C11,C12,10)
        // No Snapshot

        Thread.sleep(1000);

        assertEquals(2, server1.getBlockchainSize(),
                "Blockchain should have successfully appended 2 blocks and has size=2.");

        assertEquals(88, server1.getClientBalance(11),
                "Blockchain should have successfully appended account creation and 1 new transaction."
                        + " Client 11 should now have balance=88.");

        assertEquals(-1, server1.getClientWeakBalance(11),
                "Weak reads should not have client account registered unless BLOCKCHAIN_STEP is 1"
                        + " Client 11 should now have weak_balance=-1.");

        t1.join();
    }

    @Test
    @DisplayName("Testing consensus with ServerPID=4 sending wrong value")
    public void testOneByzantineServer() throws InterruptedException {
        final String COMMAND = "transfer 12 5";
        server1.serve_forever();
        server2.serve_forever();
        server3.serve_forever();
        server4.evil_serve_forever();
        client11.debug_client_request("create");
        client12.debug_client_request("create");
        client11.debug_client_request(COMMAND);
        client11.debug_client_request(COMMAND);

        // Expected Block chain: Block1: {Account(Leader); Account_Fee(Leader); Account(C11); Account_Fee(C11)}
        // Block2: {Account(C12); Account_Fee(C12); Transfer(C11,C12,5); Transfer_Fee(C11,C12,5)}

        // Not yet appended: Transfer(C11,C12,5), Transfer_Fee(C11,C12,5)
        // No Snapshot

        Thread.sleep(1000);
        assertEquals(93, server1.getClientBalance(11),
                "Blockchain should have successfully appended account creation and 1 new transaction."
                        + " Client 11 should now have balance=93.");

        assertEquals(-1, server1.getClientWeakBalance(11),
                "Weak reads should not have client account registered unless BLOCKCHAIN_STEP is lower than 3"
                        + " Client 11 should now have weak_balance=-1.");
    }

    @Test
    @DisplayName("Testing Client 11 and Client 12 sending concurrent requests.")
    public void testConcurrentClientRequests() throws InterruptedException {
        server1.serve_forever();
        server2.serve_forever();
        server3.serve_forever();
        server4.serve_forever();
        client11.debug_client_request("create");
        client12.debug_client_request("create");

        Thread t1 = new Thread(() -> {
            client11.debug_client_request("transfer 12 15");
            client11.debug_client_request("transfer 12 10");
            client11.debug_client_request("transfer 12 5");
        });

        Thread t2 = new Thread(() -> {
            client12.debug_client_request("transfer 11 18");
            client12.debug_client_request("transfer 11 12");
        });

        t1.start();
        t2.start();

        // Expected blockchain will have:
        // 3 accounts
        // 3 account fees
        // 5 transfers
        // 5 transfer fees
        // Total = 16 transactions => 4 blocks + 1 Snapshot

        Thread.sleep(2000);

        assertEquals(4, server1.getBlockchainSize(),
                "Blockchain should have successfully appended 4 blocks and has size=4.");

        assertEquals(96, server1.getClientBalance(11),
                "Blockchain should have handled concurrency. Client 11 should now have balance=96.");
        assertEquals(97, server1.getClientBalance(12),
                "Blockchain should have handled concurrency. Client 12 should now have balance=97.");

        assertEquals(96, server1.getClientWeakBalance(11),
                "Blockchain should have snapshot after the 4th block. Client 11 should now have weak_balance=96.");
        assertEquals(97, server1.getClientWeakBalance(12),
                "Blockchain should have snapshot after the 4th block. Client 12 should now have weak_balance=97.");

        t1.join();
        t2.join();
    }

    @Test
    @DisplayName("Testing weak read behaviour for snapshots.")
    public void testSnapshot() throws InterruptedException {
        final String COMMAND = "transfer 12 1";
        server1.serve_forever();
        server2.serve_forever();
        server3.serve_forever();
        server4.serve_forever();
        client11.debug_client_request("create");
        client12.debug_client_request("create");

        Thread.sleep(100);
        assertEquals(-1, server1.getClientWeakBalance(11),
                "Weak reads should not have client accounts registered yet." + " Client 11 weak_read returns -1.");

        // We need 8 blocks for SNAPSHOT_STEP = 4
        // => 32 transactions, we already have 6 (3 account creations with fees)
        // Goal: execute 13 transfers (26 transactions when fees included)

        for (int i = 0; i < 3; i++) {
            client11.debug_client_request(COMMAND);
        }

        Thread.sleep(300);
        assertEquals(-1, server1.getClientWeakBalance(11),
                "Weak reads still shouldn't have any new data." + " Client 11 weak_read returns -1.");

        for (int i = 0; i < 6; i++) {
            client11.debug_client_request(COMMAND);
        }

        Thread.sleep(500);
        assertEquals(89, server1.getClientWeakBalance(11),
                "Weak reads should now read Snapshot=1." + " Client 11 weak_read returns 81.");

        for (int i = 0; i < 4; i++) {
            client11.debug_client_request(COMMAND);
        }

        // Client balance: 100 -27 from transfers -1 from acc creation

        Thread.sleep(500);
        assertEquals(8, server1.getBlockchainSize(),
                "Blockchain should have successfully appended 8 blocks and has size=8.");

        assertEquals(73, server1.getClientWeakBalance(11),
                "Weak reads should read from Snapshot 2 and read an updated value for the last transfer."
                        + " Client 11 should now have weak_balance=73.");
    }

    @Test
    @DisplayName("Testing byzantine client sending unauthorized client creation.")
    public void testByzantineAccountCreation() throws InterruptedException {
        server1.serve_forever();
        server2.serve_forever();
        server3.serve_forever();
        server4.serve_forever();
        client11.evil_client_request("create 12");

        Thread.sleep(1000);

        assertEquals(0, server1.getBlockchainSize(), "Blockchain should have rejected request and has size=0.");
    }

    @Test
    @DisplayName("Testing byzantine client sending unauthorized transfer request.")
    public void testByzantineTransfer() throws InterruptedException {
        final String EVIL_COMMAND = "transfer-from 12 99";
        server1.serve_forever();
        server2.serve_forever();
        server3.serve_forever();
        server4.serve_forever();
        client11.debug_client_request("create");
        client12.debug_client_request("create");

        client11.evil_client_request(EVIL_COMMAND);

        Thread.sleep(1000);

        assertEquals(1, server1.getBlockchainSize(),
                "Blockchain should have rejected last transfer request and has size=1.");
        assertEquals(99, server1.getClientBalance(11),
                "Blockchain shouldn't have accepted false transaction. Client 11 should still have balance=99.");
    }
}
