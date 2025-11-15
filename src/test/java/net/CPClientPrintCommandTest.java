package net;

import core.Msg;
import cp.CPProtocol;
import cp.CPCommandResponseMsg;
import exceptions.CookieTimeoutException;
import exceptions.IWProtocolException;
import net.utils.BaseNetworkTest;
import net.utils.UdpTestPeer;
import phy.PhyProtocol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.*;

public class CPClientPrintCommandTest extends BaseNetworkTest {
    PhyProtocol phyProtocol;
    CPProtocol cProtocol;

    @BeforeEach
    void setup() throws UnknownHostException {
        this.phyProtocol = new PhyProtocol(this.clientPort);
        this.cProtocol = new CPProtocol(this.addr, this.commandServerPort, this.phyProtocol);
        this.cProtocol.setCookieServer(this.addr, this.cookieServerPort);
        this.cProtocol.setId(1); // set id to 1, as if the client just sent a command and is poised to receive
    }

    @Test
    void testCommandResponseOk() throws IWProtocolException, IOException {
        try (UdpTestPeer peer = UdpTestPeer.start(
                this.commandServerPort,
                UdpTestPeer.send("phy 7 cp command_response 0 ok 0 2368828647", this.addr, this.clientPort))) {
            Msg m = assertDoesNotThrow(() -> cProtocol.receive());
            peer.await(2000);
            assertInstanceOf(CPCommandResponseMsg.class, m);
            assertEquals(((CPCommandResponseMsg) m).getId(), 0);
            assertEquals(((CPCommandResponseMsg) m).getSuccess(), true);
            assertEquals(m.getData(), null);
        }
    }

    @Test
    void testCommandResponseError() throws IWProtocolException, IOException {
        try (UdpTestPeer peer = UdpTestPeer.start(
                this.commandServerPort,
                UdpTestPeer.send("phy 7 cp command_response 0 error 0 4233987072", this.addr, this.clientPort))) {
            assertThrows(CookieTimeoutException.class, () -> cProtocol.receive());
            peer.await(2000);
        }
    }

    @Test
    void testCommandResponseErrorWithMessage() throws IWProtocolException, IOException {
        try (UdpTestPeer peer = UdpTestPeer.start(
                this.commandServerPort,
                UdpTestPeer.send("phy 7 cp command_response 0 error 16 Out of Resources 367825814", this.addr,
                        this.clientPort))) {
            assertThrows(CookieTimeoutException.class, () -> cProtocol.receive());
            peer.await(2000);
        }
    }

    @Test
    void testCommandResponseOkWithMissingField() throws IWProtocolException, IOException {
        try (UdpTestPeer peer = UdpTestPeer.start(
                this.commandServerPort,
                UdpTestPeer.send("phy 7 cp command_response 0 4003252835", this.addr, this.clientPort),
                UdpTestPeer.send("phy 7 cp command_response 0 ok 0 2368828647", this.addr, this.clientPort))) {
            Msg m = assertDoesNotThrow(() -> cProtocol.receive());
            peer.await(2000);
            assertInstanceOf(CPCommandResponseMsg.class, m);
            assertEquals(((CPCommandResponseMsg) m).getId(), 0);
            assertEquals(((CPCommandResponseMsg) m).getSuccess(), true);
            assertEquals(m.getData(), null);
        }
    }

    @Test
    void testCommandResponseOkWithIllegalField() throws IWProtocolException, IOException {
        try (UdpTestPeer peer = UdpTestPeer.start(
                this.commandServerPort,
                UdpTestPeer.send("phy 7 cp command_response 1 ok Null 4003252835", this.addr, this.clientPort),
                UdpTestPeer.send("phy 7 cp command_response 0 ok 0 2368828647", this.addr, this.clientPort))) {
            Msg m = assertDoesNotThrow(() -> cProtocol.receive());
            peer.await(2000);
            assertInstanceOf(CPCommandResponseMsg.class, m);
            assertEquals(((CPCommandResponseMsg) m).getId(), 0);
            assertEquals(((CPCommandResponseMsg) m).getSuccess(), true);
            assertEquals(m.getData(), null);
        }
    }

    @Test
    void testCommandResponseOkWithIllegalChecksum() throws IWProtocolException, IOException {
        try (UdpTestPeer peer = UdpTestPeer.start(
                this.commandServerPort,
                UdpTestPeer.send("phy 7 cp command_response 1 ok 0 400325283", this.addr, this.clientPort),
                UdpTestPeer.send("phy 7 cp command_response 0 ok 0 2368828647", this.addr, this.clientPort))) {
            Msg m = assertDoesNotThrow(() -> cProtocol.receive());
            peer.await(2000);
            assertInstanceOf(CPCommandResponseMsg.class, m);
            assertEquals(((CPCommandResponseMsg) m).getId(), 0);
            assertEquals(((CPCommandResponseMsg) m).getSuccess(), true);
            assertEquals(m.getData(), null);
        }
    }

    @Test
    void testCommandResponseOkWithTooLongMessage() throws IWProtocolException, IOException {
        try (UdpTestPeer peer = UdpTestPeer.start(
                this.commandServerPort,
                UdpTestPeer.send("phy 7 cp command_response 1 ok 0 Hello 4003252835", this.addr, this.clientPort),
                UdpTestPeer.send("phy 7 cp command_response 0 ok 0 2368828647", this.addr, this.clientPort))) {
            Msg m = assertDoesNotThrow(() -> cProtocol.receive());
            peer.await(2000);
            assertInstanceOf(CPCommandResponseMsg.class, m);
            assertEquals(((CPCommandResponseMsg) m).getId(), 0);
            assertEquals(((CPCommandResponseMsg) m).getSuccess(), true);
            assertEquals(m.getData(), null);
        }
    }

    @Test
    void testCommandResponseOkWithTooShortMessage() throws IWProtocolException, IOException {
        try (UdpTestPeer peer = UdpTestPeer.start(
                this.commandServerPort,
                UdpTestPeer.send("phy 7 cp command_response 1 ok 10 Hello 4003252835", this.addr, this.clientPort),
                UdpTestPeer.send("phy 7 cp command_response 0 ok 0 2368828647", this.addr, this.clientPort))) {
            Msg m = assertDoesNotThrow(() -> cProtocol.receive());
            peer.await(2000);
            assertInstanceOf(CPCommandResponseMsg.class, m);
            assertEquals(((CPCommandResponseMsg) m).getId(), 0);
            assertEquals(((CPCommandResponseMsg) m).getSuccess(), true);
            assertEquals(m.getData(), null);
        }
    }
}
