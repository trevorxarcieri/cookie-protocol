package apps;

import cp.CPProtocol;
import exceptions.IWProtocolException;
import phy.PhyProtocol;

import java.io.IOException;
import java.net.InetAddress;

public class CPCommandServer {
    protected static final int COMMAND_SERVER_PORT = 2000;

    public static void main(String[] args) {
        // Set up the virtual link protocol
        PhyProtocol phy = new PhyProtocol(COMMAND_SERVER_PORT);

        // Set up command protocol
        CPProtocol cp;
        try {
            cp = new CPProtocol(phy, false, null, null);
            cp.setCookieServer(InetAddress.getByName(CPClient.SERVER_NAME), CPCookieServer.COOKIE_SERVER_PORT);
        } catch (Exception e) {
            return;
        }

        // Start server processing
        while (true) {
            try {
                String rec = cp.receive().getData();
                if (rec != null) {
                    System.out.println(rec);
                }
            } catch (IOException e) {
                System.out.println("IO error");
                return;
            } catch (IWProtocolException e) {
                System.out.println("Protocol exception: " + e.getMessage());
            }
        }
    }
}
