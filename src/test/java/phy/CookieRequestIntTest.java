package phy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockConstruction;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import cp.CPProtocol;
import exceptions.IWProtocolException;

@ExtendWith(MockitoExtension.class)
public class CookieRequestIntTest {
    String serverName = "localhost";
    int commandServerPort = 3027;
    int cookieServerPort = 2999;
    PhyProtocol phyProtocol;
    CPProtocol cProtocol;

    @Test
    void testCookieRequestIntegration() throws IWProtocolException, IOException {
        // Mock the datagram socket used by the object-under-test
        // with the message needed for this test case
        byte[] sb = "phy 7 cp cookie_response ACK 12345".getBytes();

        // Patch socket constructor to allow behavior of the mocked socket
        mockConstruction(DatagramSocket.class, (mock, ctx) -> {
            doAnswer(inv -> {
                DatagramPacket p = inv.getArgument(0);
                p.setData(sb);
                return null;
            }).when(mock).receive(any(DatagramPacket.class));
            doNothing().when(mock).send(any(DatagramPacket.class));
        });

        // Set up the object-under-test
        phyProtocol = new PhyProtocol(5001);
        cProtocol = new CPProtocol(InetAddress.getByName(serverName), commandServerPort, phyProtocol);
        cProtocol.setCookieServer(InetAddress.getByName(serverName), cookieServerPort);

        // Run the test
        cProtocol.requestCookie();
        // commented out to allow debugging
        // assertDoesNotThrow(() -> cProtocol.requestCookie());

        // verify a specified behavior
        int value = assertDoesNotThrow(() -> {
            MethodHandles.Lookup l = MethodHandles.privateLookupIn(CPProtocol.class,
                    MethodHandles.lookup());
            VarHandle vh = l.findVarHandle(CPProtocol.class, "cookie", int.class);
            return (int) vh.get(cProtocol);
        });
        assertEquals(12345, value);
    }

}
