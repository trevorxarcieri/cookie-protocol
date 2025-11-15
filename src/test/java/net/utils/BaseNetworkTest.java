package net.utils;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;

import org.junit.jupiter.api.BeforeEach;

public class BaseNetworkTest {
    private static final String SERVER_NAME = "localhost";
    protected InetAddress addr;
    protected int clientPort;
    protected int commandServerPort;
    protected int cookieServerPort;

    @BeforeEach
    void commonSetup() throws Exception {
        this.addr = InetAddress.getByName(SERVER_NAME);
        this.clientPort = findFreePort();
        this.commandServerPort = findFreePort();
        this.cookieServerPort = findFreePort();
    }

    protected int findFreePort() throws IOException {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
