package org.example.channel;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

public class Host {
    private final InetAddress _address;
    private final int _port;
    private final int _pid;

    public Host(String hostname, int port) {
        _port = port;
        _pid = port % 1000;
        try {
            _address = InetAddress.getByName(hostname);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    public Host(InetAddress address, int port) {
        _address = address;
        _port = port;
        _pid = port % 1000;
    }

    public InetAddress get_address() {
        return _address;
    }

    public int get_port() {
        return _port;
    }

    public int get_pid() {
        return _pid;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Host o) {
            return this.get_address().equals(o.get_address()) && this.get_port() == o.get_port();
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(get_address());
    }

    @Override
    public String toString() {
        return "Host[" + _address.getHostAddress() + "," + _port + "]";
    }
}
