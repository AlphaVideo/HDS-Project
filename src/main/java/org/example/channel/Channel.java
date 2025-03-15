package org.example.channel;

import java.io.IOException;

public interface Channel {
    void send(Message msg) throws IOException;

    Message receive() throws IOException;
}
