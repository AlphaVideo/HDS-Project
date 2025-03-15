package org.example;

import org.example.consensus.IstanbulMember;

import java.net.SocketException;

public class ServerMain {
    public static void main(String[] args) {
        String configPath = "/processes.csv";

        if (args.length < 1 || 2 < args.length)
            throw new RuntimeException("Wrong number of arguments: expected 1 or 2.");

        int pid = Integer.parseInt(args[0]);

        if (args.length == 2)
            configPath = args[1];

        IstanbulMember server;
        try {
            server = new IstanbulMember(pid, configPath);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
        server.serve_forever();
        server.cleanup();
    }

}