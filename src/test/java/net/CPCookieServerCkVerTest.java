package net;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.IOException;
import java.net.UnknownHostException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cp.CPMsg;
import cp.CPProtocol;
import exceptions.IWProtocolException;
import net.utils.BaseNetworkTest;
import net.utils.UdpTestPeer;
import phy.PhyProtocol;
import java.util.concurrent.atomic.AtomicReference;

public class CPCookieServerCkVerTest extends BaseNetworkTest {
    PhyProtocol phyProtocol;
    CPProtocol cProtocol;
    AtomicReference<String> cookieRef;

    @BeforeEach
    void setup() throws IOException, UnknownHostException, IWProtocolException {
        this.phyProtocol = new PhyProtocol(this.cookieServerPort);
        this.cProtocol = new CPProtocol(this.phyProtocol, true, null, null);
        this.cookieRef = new AtomicReference<>();
        try (UdpTestPeer client = UdpTestPeer.start(this.clientPort,
                UdpTestPeer.send("phy 7 cp cookie_request", this.addr, this.cookieServerPort),
                UdpTestPeer.recv("phy 7 cp cookie_response ACK (\\d+)", this.cookieRef))) {
            assertDoesNotThrow(() -> this.cProtocol.receive());
            client.await(2000);
        }
    }

    @Test
    void testCookieVerifiedSuccessfully() throws IOException, IWProtocolException {
        String cookieVerificationMsg = "cookie_verification_request " + this.cookieRef.get() + " {\"ip\":\""
                + this.addr.getHostAddress() + "\",\"udp\":" + this.clientPort + "}";
        try (UdpTestPeer cmdServ = UdpTestPeer.start(this.commandServerPort,
                UdpTestPeer.send("phy 7 cp " + cookieVerificationMsg + " " + CPMsg.getCrc(cookieVerificationMsg),
                        this.addr, this.cookieServerPort),
                UdpTestPeer.recv("phy 7 cp cookie_verification_response ok " + this.cookieRef.get()
                        + " \\d+"))) {
            assertDoesNotThrow(() -> this.cProtocol.receive());
            cmdServ.await(2000);
        }
    }

    @Test
    void testClientNoCookie() throws IOException, IWProtocolException {
        String cookieVerificationMsg = "cookie_verification_request " + this.cookieRef.get() + " {\"ip\":\""
                + this.addr.getHostAddress() + "\",\"udp\":" + (this.clientPort + 1) + "}";
        try (UdpTestPeer cmdServ = UdpTestPeer.start(this.commandServerPort,
                UdpTestPeer.send("phy 7 cp " + cookieVerificationMsg + " " + CPMsg.getCrc(cookieVerificationMsg),
                        this.addr, this.cookieServerPort),
                UdpTestPeer.recv("phy 7 cp cookie_verification_response error " + this.cookieRef.get()
                        + " 40 The client does not have a valid cookie. \\d+"))) {
            assertDoesNotThrow(() -> this.cProtocol.receive());
            cmdServ.await(2000);
        }
    }

    @Test
    void testClientWrongCookie() throws IOException, IWProtocolException {
        int wrongCookieVal = Integer.parseInt(this.cookieRef.get()) + 1;
        String cookieVerificationMsg = "cookie_verification_request " + wrongCookieVal + " {\"ip\":\""
                + this.addr.getHostAddress() + "\",\"udp\":" + this.clientPort + "}";
        try (UdpTestPeer cmdServ = UdpTestPeer.start(this.commandServerPort,
                UdpTestPeer.send("phy 7 cp " + cookieVerificationMsg + " " + CPMsg.getCrc(cookieVerificationMsg),
                        this.addr, this.cookieServerPort),
                UdpTestPeer.recv("phy 7 cp cookie_verification_response error " + wrongCookieVal
                        + " 33 The client's cookie is incorrect. \\d+"))) {
            assertDoesNotThrow(() -> this.cProtocol.receive());
            cmdServ.await(2000);
        }
    }

    @Test
    void testExpiredCookie() throws IOException, IWProtocolException, InterruptedException {
        CPProtocol shortLifetimeCpProtocol = new CPProtocol(this.phyProtocol, true, null, 1);
        this.cookieRef = new AtomicReference<>();
        try (UdpTestPeer client = UdpTestPeer.start(this.clientPort,
                UdpTestPeer.send("phy 7 cp cookie_request", this.addr, this.cookieServerPort),
                UdpTestPeer.recv("phy 7 cp cookie_response ACK (\\d+)", this.cookieRef))) {
            assertDoesNotThrow(() -> shortLifetimeCpProtocol.receive());
            client.await(2000);
        }
        Thread.sleep(10);
        String cookieVerificationMsg = "cookie_verification_request " + this.cookieRef.get() + " {\"ip\":\""
                + this.addr.getHostAddress() + "\",\"udp\":" + (this.clientPort + 1) + "}";
        try (UdpTestPeer cmdServ = UdpTestPeer.start(this.commandServerPort,
                UdpTestPeer.send("phy 7 cp " + cookieVerificationMsg + " " + CPMsg.getCrc(cookieVerificationMsg),
                        this.addr, this.cookieServerPort),
                UdpTestPeer.recv("phy 7 cp cookie_verification_response error " + this.cookieRef.get()
                        + " 40 The client does not have a valid cookie. \\d+"))) {
            assertDoesNotThrow(() -> shortLifetimeCpProtocol.receive());
            cmdServ.await(2000);
        }
    }

}
