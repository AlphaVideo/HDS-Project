# IBFT Consensus

Project for the Highly Dependable Systems 2022/2023 course at Instituto Superior Técnico. Implements a distributed ledger utilizing Istanbul BFT Consensus algorithm in Java.

## Usage

Execute JUnit tests with `mvn clean test`

This includes tests for:
- FairLossLink functionality
- PerfectLink functionality
- Broadcast functionality
- Encryption functionality
- Consensus with n=4 servers with one having delayed responses
- Consensus with n=4 servers and f=1 byzantine (sends incorrect replies to clients and invalid blocks to servers)
- Consensus with n=4 with concurrency between clients
- Weak read behaviour for system snapshots
- Byzantine Client account creation using someone else's PubKey
- Byzantine Client transfer request trying to funnel money from someone else's account
- Load test for n=10 and 3 clients

To run processes **manually**, utilize the following command for:

### **Consensus Processes** (run at least 4):
`mvn exec:java -Dexec.mainClass="org.example.ServerMain" -Dexec.args="PID"` with 1 <= PID <= 10 \
**There always has to be a process with pid=1 as it is the leader/miner!**

### **Client Processes**:
`mvn exec:java -Dexec.mainClass="org.example.ClientMain" -Dexec.args="PORT"` with a valid port that ends in a valid client PID (11, 12 or 13) \
Recommended ports: 7011, 7012 and 7013

### Automated launch
If **tmux** installed, run the script **run.sh** (inside tmux)

Alternatively, if **tmuxp** is installed, use `tmuxp load <test>`, with either test_4.yaml or test_10.yaml (4 and 10 servers). This is a better way to test.
