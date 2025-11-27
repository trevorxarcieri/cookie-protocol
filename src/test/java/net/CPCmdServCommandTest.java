package net;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cp.CPCommandMsg;
import cp.CPProtocol;
import exceptions.IWProtocolException;
import net.utils.BaseNetworkTest;
import net.utils.UdpTestPeer;
import phy.PhyProtocol;

public class CPCmdServCommandTest extends BaseNetworkTest {
    PhyProtocol phyProtocol;
    CPProtocol cProtocol;

    @BeforeEach
    void setup() throws UnknownHostException {
        this.phyProtocol = new PhyProtocol(this.commandServerPort);
        this.cProtocol = new CPProtocol(this.phyProtocol, false, null, null);
        this.cProtocol.setCookieServer(this.addr, this.cookieServerPort);
    }

    private HashMap<Integer, ArrayList<CPCommandMsg>> getPendingCommands() {
        return super.getPendingCommands(this.cProtocol);
    }

    private int getNumSuccessfulCommands() {
        return super.getNumSuccessfulCommands(this.cProtocol);
    }

    @Test
    void testPrintCommandSuccessful() throws IOException, IWProtocolException {
        try (UdpTestPeer ckServPeer = UdpTestPeer.start(
                this.cookieServerPort, // create cookie server first so its socket is able to receive ck ver req
                UdpTestPeer
                        .recv("phy 7 cp cookie_verification_request 1234 \\{\"ip\":\"" + this.addr.getHostAddress()
                                + "\",\"udp\":"
                                + this.clientPort + "\\} \\d+"),
                UdpTestPeer.send("phy 7 cp cookie_verification_response ok 1234 2096729544", this.addr,
                        this.commandServerPort))) {
            try (UdpTestPeer clientPeer = UdpTestPeer.start(
                    this.clientPort,
                    UdpTestPeer.send("phy 7 cp command 0 1234 19 print Hello, World! 2404948449", this.addr,
                            this.commandServerPort),
                    UdpTestPeer.recv("phy 7 cp command_response 0 ok 0 \\d+"))) {
                assertDoesNotThrow(() -> cProtocol.receive()); // receive command msg
                HashMap<Integer, ArrayList<CPCommandMsg>> pendingCommandsMap = getPendingCommands();
                assertEquals(pendingCommandsMap.size(), 1);
                ArrayList<CPCommandMsg> pendingCmds = pendingCommandsMap.get(1234);
                assertEquals(pendingCmds.size(), 1);
                CPCommandMsg receivedCmd = pendingCmds.get(0);
                assertEquals(receivedCmd.getId(), 0);
                assertEquals(receivedCmd.getCookie(), 1234);
                assertEquals(receivedCmd.getCommandType(), CPCommandMsg.CommandType.PRINT);
                assertEquals(receivedCmd.getCmdAndMsgFields(), "print Hello, World!");
                assertEquals(receivedCmd.getData(), "Hello, World!");

                ckServPeer.await(2000); // allow cookie server peer to receive then send
                assertDoesNotThrow(() -> cProtocol.receive()); // receive cookie verification response
                clientPeer.await(2000); // allow client peer to receive command response
                assertEquals(getNumSuccessfulCommands(), 1);
                assertEquals(pendingCmds.size(), 0);
            }
        }
    }

    @Test
    void testStatusCommandSuccessful() throws IOException, IWProtocolException {
        try (UdpTestPeer ckServPeer = UdpTestPeer.start(
                this.cookieServerPort, // create cookie server first so its socket is able to receive ck ver req
                UdpTestPeer
                        .recv("phy 7 cp cookie_verification_request 1234 \\{\"ip\":\"" + this.addr.getHostAddress()
                                + "\",\"udp\":"
                                + this.clientPort + "\\} \\d+"),
                UdpTestPeer.send("phy 7 cp cookie_verification_response ok 1234 2096729544", this.addr,
                        this.commandServerPort))) {
            try (UdpTestPeer clientPeer = UdpTestPeer.start(
                    this.clientPort,
                    UdpTestPeer.send("phy 7 cp command 0 1234 6 status 2907968835", this.addr,
                            this.commandServerPort),
                    UdpTestPeer.recv(
                            "phy 7 cp command_response 0 ok 44 \\{\"numSuccessfulCommands\":0,\"cookieTtl\":null\\} \\d+"))) {
                assertDoesNotThrow(() -> cProtocol.receive()); // receive command msg
                ArrayList<CPCommandMsg> pendingCmds = getPendingCommands().get(1234);
                CPCommandMsg receivedCmd = pendingCmds.get(0);
                assertEquals(receivedCmd.getCommandType(), CPCommandMsg.CommandType.STATUS);
                assertEquals(receivedCmd.getCmdAndMsgFields(), "status");
                assertNull(receivedCmd.getData());

                ckServPeer.await(2000); // allow cookie server peer to receive then send
                assertDoesNotThrow(() -> cProtocol.receive()); // receive cookie verification response
                clientPeer.await(2000); // allow client peer to receive command response
                assertEquals(getNumSuccessfulCommands(), 1);
                assertEquals(pendingCmds.size(), 0);
            }
        }
    }

    @Test
    void testUknownCommand() throws IOException, IWProtocolException {
        try (UdpTestPeer ckServPeer = UdpTestPeer.start(
                this.cookieServerPort, // create cookie server first so its socket is able to receive ck ver req
                UdpTestPeer
                        .recv("phy 7 cp cookie_verification_request 1234 \\{\"ip\":\"" + this.addr.getHostAddress()
                                + "\",\"udp\":"
                                + this.clientPort + "\\} \\d+"),
                UdpTestPeer.send("phy 7 cp cookie_verification_response ok 1234 2096729544", this.addr,
                        this.commandServerPort))) {
            try (UdpTestPeer clientPeer = UdpTestPeer.start(
                    this.clientPort,
                    UdpTestPeer.send("phy 7 cp command 0 1234 3 foo 3158010491", this.addr,
                            this.commandServerPort),
                    UdpTestPeer.send("phy 7 cp command 0 1234 19 print Hello, World! 2404948449", this.addr,
                            this.commandServerPort),
                    UdpTestPeer.recv("phy 7 cp command_response 0 ok 0 \\d+"))) {
                assertDoesNotThrow(() -> cProtocol.receive()); // receive command msg
                ArrayList<CPCommandMsg> pendingCmds = getPendingCommands().get(1234);
                assertEquals(pendingCmds.size(), 1);

                ckServPeer.await(2000); // allow cookie server peer to receive then send
                assertDoesNotThrow(() -> cProtocol.receive()); // receive cookie verification response
                clientPeer.await(2000); // allow client peer to receive command response
                assertEquals(getNumSuccessfulCommands(), 1);
            }
        }
    }

    @Test
    void testFailedCookieVerification() throws IOException, IWProtocolException {
        try (UdpTestPeer ckServPeer = UdpTestPeer.start(
                this.cookieServerPort, // create cookie server first so its socket is able to receive ck ver req
                UdpTestPeer
                        .recv("phy 7 cp cookie_verification_request 1234 \\{\"ip\":\"" + this.addr.getHostAddress()
                                + "\",\"udp\":"
                                + this.clientPort + "\\} \\d+"),
                UdpTestPeer.send(
                        "phy 7 cp cookie_verification_response error 1234 40 The client does not have a valid cookie. 1576510428",
                        this.addr,
                        this.commandServerPort))) {
            try (UdpTestPeer clientPeer = UdpTestPeer.start(
                    this.clientPort,
                    UdpTestPeer.send("phy 7 cp command 0 1234 19 print Hello, World! 2404948449", this.addr,
                            this.commandServerPort),
                    UdpTestPeer.recv(
                            "phy 7 cp command_response 0 error 59 Cookie verification failed. Please retry with a new cookie. \\d+"))) {
                assertDoesNotThrow(() -> cProtocol.receive()); // receive command msg
                ArrayList<CPCommandMsg> pendingCmds = getPendingCommands().get(1234);
                assertEquals(pendingCmds.size(), 1);

                ckServPeer.await(2000); // allow cookie server peer to receive then send
                assertDoesNotThrow(() -> cProtocol.receive()); // receive cookie verification response
                clientPeer.await(2000); // allow client peer to receive command response
                assertEquals(getNumSuccessfulCommands(), 0);
            }
        }
    }

    @Test
    void testUnauthorizedCookieVerification() throws IOException, IWProtocolException {
        try (UdpTestPeer ckServPeer = UdpTestPeer.start(
                this.cookieServerPort, // create cookie server first so its socket is able to receive ck ver req
                UdpTestPeer
                        .recv("phy 7 cp cookie_verification_request 1234 \\{\"ip\":\"" + this.addr.getHostAddress()
                                + "\",\"udp\":"
                                + this.clientPort + "\\} \\d+"),
                UdpTestPeer.recv("continue"), // block on arbitrary message so real ver resp doesn't send before unauth
                UdpTestPeer.send("phy 7 cp cookie_verification_response ok 1234 2096729544", this.addr,
                        this.commandServerPort))) {

            try (UdpTestPeer clientPeer = UdpTestPeer.start(
                    this.clientPort,
                    UdpTestPeer.send("phy 7 cp command 0 1234 19 print Hello, World! 2404948449", this.addr,
                            this.commandServerPort),
                    UdpTestPeer.recv("phy 7 cp command_response 0 ok 0 \\d+"))) {
                assertDoesNotThrow(() -> cProtocol.receive()); // receive command msg
                ArrayList<CPCommandMsg> pendingCmds = getPendingCommands().get(1234);
                assertEquals(pendingCmds.size(), 1);

                try (UdpTestPeer fakeCkServPeer = UdpTestPeer.start(
                        findFreePort(),
                        UdpTestPeer.send("phy 7 cp cookie_verification_response ok 1234 2096729544", this.addr,
                                this.commandServerPort), // send unauthorized cookie verification message
                        UdpTestPeer.send("continue", this.addr, this.cookieServerPort))) {
                    fakeCkServPeer.await(2000);
                }

                ckServPeer.await(2000); // allow cookie server peer to receive then send
                assertDoesNotThrow(// receive unauthorized cookie verification response, then actual (unauthorized
                                   // receipt verified by coverage)
                        () -> cProtocol.receive());
                clientPeer.await(2000); // allow client peer to receive command response
                assertEquals(getNumSuccessfulCommands(), 1);
            }
        }
    }

    @Test
    void testNonexistentCookieVerification() throws IOException, IWProtocolException {
        try (UdpTestPeer ckServPeer = UdpTestPeer.start(
                this.cookieServerPort, // create cookie server first so its socket is able to receive ck ver req
                UdpTestPeer
                        .recv("phy 7 cp cookie_verification_request 1234 \\{\"ip\":\"" + this.addr.getHostAddress()
                                + "\",\"udp\":"
                                + this.clientPort + "\\} \\d+"),
                UdpTestPeer.send("phy 7 cp cookie_verification_response ok 2 99104636", this.addr,
                        this.commandServerPort),
                UdpTestPeer.send("phy 7 cp cookie_verification_response ok 1234 2096729544", this.addr,
                        this.commandServerPort))) {

            try (UdpTestPeer clientPeer = UdpTestPeer.start(
                    this.clientPort,
                    UdpTestPeer.send("phy 7 cp command 0 1234 19 print Hello, World! 2404948449", this.addr,
                            this.commandServerPort),
                    UdpTestPeer.recv("phy 7 cp command_response 0 ok 0 \\d+"))) {
                assertDoesNotThrow(() -> cProtocol.receive()); // receive command msg
                ArrayList<CPCommandMsg> pendingCmds = getPendingCommands().get(1234);
                assertEquals(pendingCmds.size(), 1);

                ckServPeer.await(2000); // allow cookie server peer to receive then send
                assertDoesNotThrow(// receive nonexistent cookie verification response, then actual (nonexistent
                                   // cookie receipt verified by coverage)
                        () -> cProtocol.receive());
                clientPeer.await(2000); // allow client peer to receive command response
                assertEquals(getNumSuccessfulCommands(), 1);
            }
        }
    }

    @Test
    void testMultipleClientsSuccessful() throws IOException, IWProtocolException {
        try (UdpTestPeer ckServPeer = UdpTestPeer.start(
                this.cookieServerPort, // create cookie server first so its socket is able to receive ck ver req
                UdpTestPeer.recv("continue"), // block on arbitrary message so ver resps are not processed
                UdpTestPeer
                        .recv("phy 7 cp cookie_verification_request 1234 \\{\"ip\":\"" + this.addr.getHostAddress()
                                + "\",\"udp\":"
                                + this.clientPort + "\\} \\d+"),
                UdpTestPeer.send("phy 7 cp cookie_verification_response ok 1234 2096729544", this.addr,
                        this.commandServerPort),
                UdpTestPeer
                        .recv("phy 7 cp cookie_verification_request 5678 \\{\"ip\":\"" + this.addr.getHostAddress()
                                + "\",\"udp\":\\d+\\} \\d+"),
                UdpTestPeer.send("phy 7 cp cookie_verification_response ok 5678 2571647852", this.addr,
                        this.commandServerPort))) {

            try (UdpTestPeer clientPeer = UdpTestPeer.start(
                    this.clientPort,
                    UdpTestPeer.send("phy 7 cp command 0 1234 19 print Hello, World! 2404948449", this.addr,
                            this.commandServerPort),
                    UdpTestPeer.recv("phy 7 cp command_response 0 ok 0 \\d+"))) {
                try (UdpTestPeer client2Peer = UdpTestPeer.start(
                        findFreePort(),
                        UdpTestPeer.send("phy 7 cp command 0 5678 19 print Hello, World! 2218495072", this.addr,
                                this.commandServerPort),
                        UdpTestPeer.send("continue", this.addr, this.cookieServerPort), // unblock cookie server
                        UdpTestPeer.recv("phy 7 cp command_response 0 ok 0 \\d+"))) {
                    assertDoesNotThrow(() -> cProtocol.receive()); // receive command msg, send first cookie
                                                                   // verification
                    HashMap<Integer, ArrayList<CPCommandMsg>> pendingCmdsMap = getPendingCommands();
                    assertEquals(pendingCmdsMap.size(), 1);

                    assertDoesNotThrow(() -> cProtocol.receive()); // receive second command msg, send first cookie
                                                                   // verification
                    assertEquals(pendingCmdsMap.size(), 2);

                    ckServPeer.await(2000); // allow cookie server peer to receive then send
                    assertDoesNotThrow(
                            () -> cProtocol.receive() // receive first cookie verification response
                    );
                    assertDoesNotThrow(
                            () -> cProtocol.receive() // receive second cookie verification response
                    );
                    clientPeer.await(2000); // allow client peer to receive command response
                    client2Peer.await(2000);
                    assertEquals(pendingCmdsMap.size(), 0);
                    assertEquals(getNumSuccessfulCommands(), 2);
                }
            }
        }
    }

    @Test
    void testMultiplePendingCommandsSuccessful() throws IOException, IWProtocolException {
        try (UdpTestPeer ckServPeer = UdpTestPeer.start(
                this.cookieServerPort, // create cookie server first so its socket is able to receive ck ver req
                UdpTestPeer.recv("continue"),
                UdpTestPeer
                        .recv("phy 7 cp cookie_verification_request 1234 \\{\"ip\":\"" + this.addr.getHostAddress()
                                + "\",\"udp\":"
                                + this.clientPort + "\\} \\d+"),
                UdpTestPeer.send("phy 7 cp cookie_verification_response ok 1234 2096729544", this.addr,
                        this.commandServerPort),
                UdpTestPeer
                        .recv("phy 7 cp cookie_verification_request 1234 \\{\"ip\":\"" + this.addr.getHostAddress()
                                + "\",\"udp\":"
                                + this.clientPort + "\\} \\d+"),
                UdpTestPeer.send("phy 7 cp cookie_verification_response ok 1234 2096729544", this.addr,
                        this.commandServerPort))) {

            try (UdpTestPeer clientPeer = UdpTestPeer.start(
                    this.clientPort,
                    UdpTestPeer.send("phy 7 cp command 0 1234 19 print Hello, World! 2404948449", this.addr,
                            this.commandServerPort),
                    UdpTestPeer.send("phy 7 cp command 1 1234 19 print Hello, World! 3965665477", this.addr,
                            this.commandServerPort),
                    UdpTestPeer.send("continue", this.addr, this.cookieServerPort),
                    UdpTestPeer.recv("phy 7 cp command_response 0 ok 0 \\d+"),
                    UdpTestPeer.recv("phy 7 cp command_response 1 ok 0 \\d+"))) {
                assertDoesNotThrow(() -> cProtocol.receive()); // receive command msg 1
                assertDoesNotThrow(() -> cProtocol.receive()); // receive command msg 2
                ArrayList<CPCommandMsg> pendingCmds = getPendingCommands().get(1234);
                assertEquals(pendingCmds.size(), 2);

                ckServPeer.await(2000); // allow cookie server peer to receive then send
                assertDoesNotThrow(() -> cProtocol.receive());
                assertDoesNotThrow(() -> cProtocol.receive());
                clientPeer.await(2000); // allow client peer to receive command response
                assertEquals(pendingCmds.size(), 0);
                assertEquals(getNumSuccessfulCommands(), 2);
            }
        }
    }
}
