package org.example;

import org.example.client.Client;

import java.net.SocketException;

public class ClientMain {
    public static void main(String[] args) {
        String configPath = "/processes.csv";

        if (args.length < 1 || 2 < args.length)
            throw new RuntimeException("Wrong number of arguments: expected 1 or 2.");

        int port = Integer.parseInt(args[0]);

        if (args.length == 2)
            configPath = args[1];

        Client client;
        try {
            client = new Client(port, configPath);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }

        client.do_forever();
        client.shutdown();
    }
}
