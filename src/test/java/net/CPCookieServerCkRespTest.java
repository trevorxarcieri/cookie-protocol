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
    void testCookieRespHappyPath() throws IWProtocolException, IOException {
        AtomicReference<String> cookieRef = new AtomicReference<>();
        long minTocMs = System.currentTimeMillis();
        try (UdpTestPeer peer = UdpTestPeer.start(
                this.clientPort, UdpTestPeer.send("phy 7 cp cookie_request", this.addr, this.cookieServerPort),
                UdpTestPeer.recv("phy 7 cp cookie_response ACK (-?\\d+)", cookieRef))) {
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
    void testCookieRespIdempotent() throws IOException, IWProtocolException {
        AtomicReference<String> cookie1Ref = new AtomicReference<>();
        AtomicReference<String> cookie2Ref = new AtomicReference<>();
        try (UdpTestPeer peer = UdpTestPeer.start(
                this.clientPort,
                UdpTestPeer.send("phy 7 cp cookie_request", this.addr, this.cookieServerPort),
                UdpTestPeer.recv("phy 7 cp cookie_response ACK (-?\\d+)", cookie1Ref),
                UdpTestPeer.send("phy 7 cp cookie_request", this.addr, this.cookieServerPort),
                UdpTestPeer.recv("phy 7 cp cookie_response ACK (-?\\d+)", cookie2Ref))) {
            assertDoesNotThrow(() -> cProtocol.receive());
            assertDoesNotThrow(() -> cProtocol.receive());
            peer.await(2000);

            assertEquals(getCookieMap(this.cProtocol).size(), 1); // only 1 cookie exists in the cookie server
            assertEquals(cookie1Ref.get(), cookie2Ref.get()); // server returns the same cookie for both requests
        }
    }

    @Test
    void testCookieResp2Clients() throws IOException, IWProtocolException {
        AtomicReference<String> cookie1Ref = new AtomicReference<>();
        AtomicReference<String> cookie2Ref = new AtomicReference<>();
        int client2Port = findFreePort();
        try (UdpTestPeer peer1 = UdpTestPeer.start(
                this.clientPort,
                UdpTestPeer.send("phy 7 cp cookie_request", this.addr, this.cookieServerPort),
                UdpTestPeer.recv("phy 7 cp cookie_response ACK (-?\\d+)", cookie1Ref))) {
            assertDoesNotThrow(() -> cProtocol.receive());
            peer1.await(2000);
            try (UdpTestPeer peer2 = UdpTestPeer.start(
                    client2Port,
                    UdpTestPeer.send("phy 7 cp cookie_request", this.addr, this.cookieServerPort),
                    UdpTestPeer.recv("phy 7 cp cookie_response ACK (-?\\d+)", cookie2Ref))) {
                assertDoesNotThrow(() -> cProtocol.receive());
                peer2.await(2000);

                HashMap<PhyConfiguration, Cookie> cookieMap = getCookieMap(this.cProtocol);
                assertEquals(cookieMap.size(), 2);

                PhyConfiguration client1Conf = new PhyConfiguration(this.addr, this.clientPort, proto_id.CP);
                PhyConfiguration client2Conf = new PhyConfiguration(this.addr, client2Port, proto_id.CP);
                assertTrue(cookieMap.containsKey(client1Conf));
                assertTrue(cookieMap.containsKey(client2Conf));

                Cookie ck1 = cookieMap.get(client1Conf);
                Cookie ck2 = cookieMap.get(client2Conf);
                assertEquals(cookie1Ref.get(), "" + ck1.getCookieValue());
                assertEquals(cookie2Ref.get(), "" + ck2.getCookieValue());
                assertTrue(ck1.getTimeOfCreation() < ck2.getTimeOfCreation());
            }
        }
    }

    @Test
    void testCookieRespTimeout() throws IOException, InterruptedException, IWProtocolException {
        CPProtocol shortTtlCp = new CPProtocol(this.phyProtocol, true, null, 1);
        try (UdpTestPeer peer = UdpTestPeer.start(
                this.clientPort,
                UdpTestPeer.send("phy 7 cp cookie_request", this.addr, this.cookieServerPort),
                UdpTestPeer.recv("phy 7 cp cookie_response ACK -?\\d+"),
                UdpTestPeer.send("phy 7 cp cookie_request", this.addr, this.cookieServerPort),
                UdpTestPeer.recv("phy 7 cp cookie_response ACK -?\\d+"))) {
            assertDoesNotThrow(() -> shortTtlCp.receive());
            long minTocMs = System.currentTimeMillis();
            Thread.sleep(2);
            assertDoesNotThrow(() -> shortTtlCp.receive());
            peer.await(2000);

            HashMap<PhyConfiguration, Cookie> cookieMap = getCookieMap(shortTtlCp);
            assertEquals(cookieMap.size(), 1); // only 1 cookie exists in the cookie server
            assertTrue(
                    cookieMap
                            .values()
                            .iterator()
                            .next()
                            .getTimeOfCreation() > minTocMs // this cookie was created newly for the second request
            );
        }
    }

    @Test
    void testCookieRespTooManyClients() throws IOException, IWProtocolException {
        CPProtocol lowClientsCp = new CPProtocol(this.phyProtocol, true, 1, null);
        AtomicReference<String> cookieRef = new AtomicReference<>();
        AtomicReference<String> errorMsgRef = new AtomicReference<>();
        int client2Port = findFreePort();
        try (UdpTestPeer peer1 = UdpTestPeer.start(
                this.clientPort,
                UdpTestPeer.send("phy 7 cp cookie_request", this.addr, this.cookieServerPort),
                UdpTestPeer.recv("phy 7 cp cookie_response ACK (-?\\d+)", cookieRef))) {
            assertDoesNotThrow(() -> lowClientsCp.receive());
            peer1.await(2000);
            try (UdpTestPeer peer2 = UdpTestPeer.start(
                    client2Port,
                    UdpTestPeer.send("phy 7 cp cookie_request", this.addr, this.cookieServerPort),
                    UdpTestPeer.recv("phy 7 cp cookie_response NAK (.*)", errorMsgRef))) {
                assertDoesNotThrow(() -> lowClientsCp.receive());
                peer2.await(2000);

                HashMap<PhyConfiguration, Cookie> cookieMap = getCookieMap(lowClientsCp);
                assertEquals(cookieMap.size(), 1);

                PhyConfiguration client1Conf = new PhyConfiguration(this.addr, this.clientPort, proto_id.CP);
                PhyConfiguration client2Conf = new PhyConfiguration(this.addr, client2Port, proto_id.CP);
                assertTrue(cookieMap.containsKey(client1Conf));
                assertFalse(cookieMap.containsKey(client2Conf));

                Cookie ck1 = cookieMap.get(client1Conf);
                assertEquals(cookieRef.get(), "" + ck1.getCookieValue());
                assertTrue(errorMsgRef.get().contains("Max number of clients"));
            }
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

}
