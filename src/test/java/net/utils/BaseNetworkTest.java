package net.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;

import cp.CPCommandMsg;
import cp.CPProtocol;
import cp.Cookie;
import phy.PhyConfiguration;

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

    protected static int findFreePort() throws IOException {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        }
    }

    protected static int getCookie(CPProtocol cProtocol) {
        return assertDoesNotThrow(() -> {
            MethodHandles.Lookup l = MethodHandles.privateLookupIn(CPProtocol.class,
                    MethodHandles.lookup());
            VarHandle vh = l.findVarHandle(CPProtocol.class, "cookie", int.class);
            return (int) vh.get(cProtocol);
        });
    }

    protected static HashMap<PhyConfiguration, Cookie> getCookieMap(CPProtocol cProtocol) {
        return assertDoesNotThrow(() -> {
            MethodHandles.Lookup l = MethodHandles.privateLookupIn(CPProtocol.class,
                    MethodHandles.lookup());
            VarHandle vh = l.findVarHandle(CPProtocol.class, "cookieMap", HashMap.class);
            return (HashMap<PhyConfiguration, Cookie>) vh.get(cProtocol);
        });
    }

    protected static HashMap<Integer, ArrayList<CPCommandMsg>> getPendingCommands(CPProtocol cProtocol) {
        return assertDoesNotThrow(() -> {
            MethodHandles.Lookup l = MethodHandles.privateLookupIn(CPProtocol.class,
                    MethodHandles.lookup());
            VarHandle vh = l.findVarHandle(CPProtocol.class, "pendingCommands", HashMap.class);
            return (HashMap<Integer, ArrayList<CPCommandMsg>>) vh.get(cProtocol);
        });
    }

    protected static int getNumSuccessfulCommands(CPProtocol cProtocol) {
        return assertDoesNotThrow(() -> {
            MethodHandles.Lookup l = MethodHandles.privateLookupIn(CPProtocol.class,
                    MethodHandles.lookup());
            VarHandle vh = l.findVarHandle(CPProtocol.class, "numSuccessfulCommands", int.class);
            return (int) vh.get(cProtocol);
        });
    }

    protected static Thread runAsync(ThrowingRunnable task) {
        Thread t = new Thread(() -> {
            try {
                assertDoesNotThrow(() -> task.run());
            } catch (Exception e) {
                fail("Async task failed: " + e.getMessage());
            }
        });
        t.start();
        return t;
    }

    protected void waitForThreads(Thread... threads) {
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                fail("Test threads interrupted: " + e.getMessage());
            }
        }
    }

    @FunctionalInterface
    protected interface ThrowingRunnable {
        void run() throws Exception;
    }
}
