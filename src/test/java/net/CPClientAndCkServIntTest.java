package net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.UnknownHostException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import core.Msg;
import cp.CPCommandResponseMsg;
import cp.CPProtocol;
import exceptions.CookieTimeoutException;
import exceptions.IWProtocolException;
import net.utils.BaseNetworkTest;
import net.utils.UdpTestPeer;
import phy.PhyProtocol;

public class CPClientAndCkServIntTest extends BaseNetworkTest {
    PhyProtocol clientPhyP;
    CPProtocol clientCpP;
    PhyProtocol ckServPhyP;
    CPProtocol ckServCpP;

    @BeforeEach
    void setup() throws UnknownHostException {
        this.clientPhyP = new PhyProtocol(this.clientPort);
        this.clientCpP = new CPProtocol(this.addr, this.commandServerPort, this.clientPhyP);
        this.clientCpP.setCookieServer(this.addr, this.cookieServerPort);
        this.ckServPhyP = new PhyProtocol(this.cookieServerPort);
        this.ckServCpP = new CPProtocol(this.ckServPhyP, true, null, null);
    }

    @Test
    void testCPClientAndCkServPrintIntegration() throws IWProtocolException, IOException {
        try (UdpTestPeer peer = UdpTestPeer.start(
                this.commandServerPort,
                UdpTestPeer.recv("phy 7 cp command 0 -?\\d+ 19 print Hello, World! \\d+"),
                UdpTestPeer.send("phy 7 cp command_response 0 ok 0 2368828647", this.addr, this.clientPort))) {
            Thread ckServThread = runAsync(() -> ckServCpP.receive());
            Thread clientThread = runAsync(() -> clientCpP.send("print Hello, World!", null));
            try {
                ckServThread.join();
                clientThread.join();
            } catch (InterruptedException e) {
                fail("Test threads interrupted: " + e.getMessage());
            }
            Msg result = clientCpP.receive();
            peer.await(2000);
            assertInstanceOf(CPCommandResponseMsg.class, result);
            assertEquals(((CPCommandResponseMsg) result).getId(), 0);
            assertEquals(((CPCommandResponseMsg) result).getSuccess(), true);
            assertEquals(result.getData(), null);
        }
    }

    @Test
    void testCPClientAndCkServStatusIntegration() throws IWProtocolException, IOException {
        try (UdpTestPeer peer = UdpTestPeer.start(
                this.commandServerPort,
                UdpTestPeer.recv("phy 7 cp command 0 -?\\d+ 6 status \\d+"),
                UdpTestPeer.send(
                        "phy 7 cp command_response 0 ok 53 {numSuccessfullyProcessedCommands: 1, cookieTtlS: 40} 3491006493",
                        this.addr, this.clientPort))) {
            Thread ckServThread = runAsync(() -> ckServCpP.receive());
            Thread clientThread = runAsync(() -> clientCpP.send("status", null));
            try {
                ckServThread.join();
                clientThread.join();
            } catch (InterruptedException e) {
                fail("Test threads interrupted: " + e.getMessage());
            }
            Msg result = clientCpP.receive();
            peer.await(2000);
            assertInstanceOf(CPCommandResponseMsg.class, result);
            assertEquals(((CPCommandResponseMsg) result).getId(), 0);
            assertEquals(((CPCommandResponseMsg) result).getSuccess(), true);
            assertEquals(result.getData(), "{numSuccessfullyProcessedCommands: 1, cookieTtlS: 40}");
        }
    }

    @Test
    void testCPClientAndCkServStatusFailIntegration() throws IWProtocolException, IOException {
        try (UdpTestPeer peer = UdpTestPeer.start(
                this.commandServerPort,
                UdpTestPeer.recv("phy 7 cp command 0 -?\\d+ 6 status \\d+"),
                UdpTestPeer.send(
                        "phy 7 cp command_response 0 error 15 Invalid cookie. 2942514232",
                        this.addr, this.clientPort))) {
            Thread ckServThread = runAsync(() -> ckServCpP.receive());
            Thread clientThread = runAsync(() -> clientCpP.send("status", null));
            try {
                ckServThread.join();
                clientThread.join();
            } catch (InterruptedException e) {
                fail("Test threads interrupted: " + e.getMessage());
            }
            assertThrows(CookieTimeoutException.class, () -> clientCpP.receive());
            peer.await(2000);
            assertEquals(getCookie(this.clientCpP), -1);
        }
    }

}