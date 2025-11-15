package net;

import cp.CPProtocol;
import exceptions.CookieRequestException;
import exceptions.IWProtocolException;
import net.utils.BaseNetworkTest;
import net.utils.UdpTestPeer;
import phy.PhyProtocol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.*;

public class CPClientCookieRequestTest extends BaseNetworkTest {
    PhyProtocol phyProtocol;
    CPProtocol cProtocol;

    @BeforeEach
    void setup() throws UnknownHostException {
        this.phyProtocol = new PhyProtocol(this.clientPort);
        this.cProtocol = new CPProtocol(this.addr, this.commandServerPort, this.phyProtocol);
        this.cProtocol.setCookieServer(this.addr, this.cookieServerPort);
    }

    private int getCookie() {
        return assertDoesNotThrow(() -> {
            MethodHandles.Lookup l = MethodHandles.privateLookupIn(CPProtocol.class,
                    MethodHandles.lookup());
            VarHandle vh = l.findVarHandle(CPProtocol.class, "cookie", int.class);
            return (int) vh.get(this.cProtocol);
        });
    }

    @Test
    void testCookieRequestSuccessful() throws IWProtocolException, IOException {
        try (UdpTestPeer peer = UdpTestPeer.start(
                this.cookieServerPort, UdpTestPeer.recv("phy 7 cp cookie_request"),
                UdpTestPeer.send("phy 7 cp cookie_response ACK 12345", this.addr, this.clientPort))) {
            assertDoesNotThrow(() -> cProtocol.requestCookie());
            peer.await(2000);
            assertEquals(getCookie(), 12345);
        }
    }

    @Test
    void testNoCookie() throws IOException, IWProtocolException {
        try (UdpTestPeer peer = UdpTestPeer.start(
                this.cookieServerPort, UdpTestPeer.recv("phy 7 cp cookie_request"),
                UdpTestPeer.send("phy 7 cp cookie_response NAK no resources", this.addr, this.clientPort))) {
            assertThrows(CookieRequestException.class, () -> cProtocol.requestCookie());
            peer.await(2000);
            assertEquals(getCookie(), -1);
        }
    }

    @Test
    void testIllegalPhyMsg() throws IOException, IWProtocolException {
        try (UdpTestPeer peer = UdpTestPeer.start(
                this.cookieServerPort, UdpTestPeer.recv("phy 7 cp cookie_request"),
                UdpTestPeer.send("phy 5 cp cookie_response ACK 12345", this.addr, this.clientPort),
                UdpTestPeer.send("phy 7 cp cookie_response ACK 12345", this.addr, this.clientPort))) {
            assertDoesNotThrow(() -> cProtocol.requestCookie());
            peer.await(2000);
            assertEquals(getCookie(), 12345);
        }
    }

    @Test
    void testMalformedCPMsg() throws IOException, IWProtocolException {
        try (UdpTestPeer peer = UdpTestPeer.start(
                this.cookieServerPort, UdpTestPeer.recv("phy 7 cp cookie_request"),
                UdpTestPeer.send("phy 7 cp cookie_response ACK abc", this.addr, this.clientPort),
                UdpTestPeer.send("phy 7 cp cookie_response ACK 12345", this.addr, this.clientPort))) {
            assertDoesNotThrow(() -> cProtocol.requestCookie());
            peer.await(2000);
            assertEquals(getCookie(), 12345);
        }
    }

    @Test
    void testIncompleteCPMsg() throws IOException, IWProtocolException {
        try (UdpTestPeer peer = UdpTestPeer.start(
                this.cookieServerPort, UdpTestPeer.recv("phy 7 cp cookie_request"),
                UdpTestPeer.send("phy 7 cp cookie_response ACK", this.addr, this.clientPort),
                UdpTestPeer.send("phy 7 cp cookie_response ACK 12345", this.addr, this.clientPort))) {
            assertDoesNotThrow(() -> cProtocol.requestCookie());
            peer.await(2000);
            assertEquals(getCookie(), 12345);
        }
    }

    @Test
    void testMessageLoss() throws IOException, IWProtocolException {
        try (UdpTestPeer peer = UdpTestPeer.start(
                this.cookieServerPort, UdpTestPeer.recv("phy 7 cp cookie_request"))) {
            assertThrows(CookieRequestException.class, () -> cProtocol.requestCookie());
            peer.await(2000);
            assertEquals(getCookie(), -1);
        }
    }

}
