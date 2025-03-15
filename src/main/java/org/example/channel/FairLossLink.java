package org.example.channel;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class FairLossLink implements Channel {

    private final DatagramSocket _socket;
    private final DatagramSocket _socketSender;
    private final DatagramSocket _socketAck;
    private final int BUFSIZE = 65536;

    public FairLossLink(int port) throws SocketException {
        _socket = new DatagramSocket(port);
        _socketSender = new DatagramSocket(10000 + port);
        _socketAck = new DatagramSocket(20000 + port);
    }

    public void send(Message msg) throws IOException {
        byte[] buffer = msg.get_data();
        Host host = msg.get_host();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, host.get_address(), host.get_port());
        _socketSender.send(packet);
    }

    public Message receiveAck() throws IOException {
        byte[] buffer = new byte[BUFSIZE];

        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        _socketAck.receive(packet);

        return new Message(packet);
    }

    public Message receive() throws IOException {
        byte[] buffer = new byte[BUFSIZE];

        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        _socket.receive(packet);

        return new Message(packet);
    }

    public void close() {
        _socket.close();
        _socketAck.close();
        _socketSender.close();
    }
}
