package net;

import cp.CPProtocol;
import cp.Cookie;
import exceptions.IWProtocolException;
import net.utils.BaseNetworkTest;
import net.utils.UdpTestPeer;
import phy.PhyConfiguration;
import phy.PhyProtocol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import core.Protocol.proto_id;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class CPCookieServerCkRespTest extends BaseNetworkTest {
    PhyProtocol phyProtocol;
    CPProtocol cProtocol;

    @BeforeEach
    void setup() throws UnknownHostException {
        this.phyProtocol = new PhyProtocol(this.cookieServerPort);
        this.cProtocol = new CPProtocol(this.phyProtocol, true, null, null);
    }

    @Test
    void testCkRespSucc() throws IWProtocolException, IOException {
        AtomicReference<String> cookieRef = new AtomicReference<>();
        long minTocMs = System.currentTimeMillis();
        try (UdpTestPeer peer = UdpTestPeer.start(
                this.clientPort, UdpTestPeer.send("phy 7 cp cookie_request", this.addr, this.cookieServerPort),
                UdpTestPeer.recvCapture("phy 7 cp cookie_response ACK (-?\\d+)", cookieRef))) {
            assertDoesNotThrow(() -> cProtocol.receive());
            peer.await(2000);
            HashMap<PhyConfiguration, Cookie> cookieMap = getCookieMap(this.cProtocol);
            assertEquals(cookieMap.size(), 1);

            PhyConfiguration expectedClientConf = new PhyConfiguration(this.addr, this.clientPort, proto_id.CP);
            assertTrue(cookieMap.containsKey(expectedClientConf));

            Cookie ck = cookieMap.get(expectedClientConf);
            assertEquals(cookieRef.get(), "" + ck.getCookieValue());
            assertTrue(ck.getTimeOfCreation() >= minTocMs && ck.getTimeOfCreation() <= System.currentTimeMillis());
        }
    }

    @Test
    void testIllegalMsgs() throws IOException, IWProtocolException {
        try (UdpTestPeer peer = UdpTestPeer.start(
                this.clientPort,
                UdpTestPeer.send("phy 5 cp cookie_request", this.addr, this.cookieServerPort),
                UdpTestPeer.send("phy 7 cpp cookie_request", this.addr, this.cookieServerPort),
                UdpTestPeer.send("phy 7 cp cookie_requestt", this.addr, this.cookieServerPort),
                UdpTestPeer.send("phy 7 cp cookie_request", this.addr, this.cookieServerPort),
                UdpTestPeer.recv("phy 7 cp cookie_response ACK -?\\d+"))) {
            assertDoesNotThrow(() -> cProtocol.receive());
            peer.await(2000);
            assertEquals(getCookieMap(this.cProtocol).size(), 1);
        }
    }

    // @Test
    // void testNoCookie() throws IOException, IWProtocolException {
    // try (UdpTestPeer peer = UdpTestPeer.start(
    // this.cookieServerPort, UdpTestPeer.recv("phy 7 cp cookie_request"),
    // UdpTestPeer.send("phy 7 cp cookie_response NAK no resources", this.addr,
    // this.clientPort))) {
    // assertThrows(CookieRequestException.class, () -> cProtocol.requestCookie());
    // peer.await(2000);
    // assertEquals(getCookie(), -1);
    // }
    // }

    // @Test
    // void testMalformedCPMsg() throws IOException, IWProtocolException {
    // try (UdpTestPeer peer = UdpTestPeer.start(
    // this.cookieServerPort, UdpTestPeer.recv("phy 7 cp cookie_request"),
    // UdpTestPeer.send("phy 7 cp cookie_response ACK abc", this.addr,
    // this.clientPort),
    // UdpTestPeer.send("phy 7 cp cookie_response ACK 12345", this.addr,
    // this.clientPort))) {
    // assertDoesNotThrow(() -> cProtocol.requestCookie());
    // peer.await(2000);
    // assertEquals(getCookie(), 12345);
    // }
    // }

    // @Test
    // void testIncompleteCPMsg() throws IOException, IWProtocolException {
    // try (UdpTestPeer peer = UdpTestPeer.start(
    // this.cookieServerPort, UdpTestPeer.recv("phy 7 cp cookie_request"),
    // UdpTestPeer.send("phy 7 cp cookie_response ACK", this.addr, this.clientPort),
    // UdpTestPeer.send("phy 7 cp cookie_response ACK 12345", this.addr,
    // this.clientPort))) {
    // assertDoesNotThrow(() -> cProtocol.requestCookie());
    // peer.await(2000);
    // assertEquals(getCookie(), 12345);
    // }
    // }

    // @Test
    // void testMessageLoss() throws IOException, IWProtocolException {
    // try (UdpTestPeer peer = UdpTestPeer.start(
    // this.cookieServerPort, UdpTestPeer.recv("phy 7 cp cookie_request"))) {
    // assertThrows(CookieRequestException.class, () -> cProtocol.requestCookie());
    // peer.await(2000);
    // assertEquals(getCookie(), -1);
    // }
    // }

}
