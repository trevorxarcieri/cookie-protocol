package cp;

import core.*;
import exceptions.*;
import phy.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Random;

public class CPProtocol extends Protocol {
    private static final int CP_TIMEOUT = 2000;
    private static final int CP_HASHMAP_SIZE = 20;
    private static final int COOKIE_LIFETIME_MS = 60000;
    private int cookie;
    private int id;
    private int cookie_lifetime_ms;
    private PhyConfiguration PhyConfigCommandServer;
    private PhyConfiguration PhyConfigCookieServer;
    private final PhyProtocol PhyProto;
    private final cp_role role;
    HashMap<PhyConfiguration, Cookie> cookieMap;
    ArrayList<CPCommandMsg> pendingCommands;
    Random rnd;

    private enum cp_role {
        CLIENT, COOKIE, COMMAND
    }

    // Constructor for clients
    public CPProtocol(InetAddress rname, int rp, PhyProtocol phyP) throws UnknownHostException {
        this.cookie = -1;
        this.id = 0;
        this.PhyConfigCommandServer = new PhyConfiguration(rname, rp, proto_id.CP);
        this.PhyProto = phyP;
        this.role = cp_role.CLIENT;
    }

    // Constructors for servers
    public CPProtocol(PhyProtocol phyP, boolean isCookieServer) {
        this.PhyProto = phyP;
        if (isCookieServer) {
            this.role = cp_role.COOKIE;
            this.cookieMap = new HashMap<>();
            this.rnd = new Random();
        } else {
            this.role = cp_role.COMMAND;
            this.pendingCommands = new ArrayList<>();
        }
    }

    /**
     * CPProtocol constructor for a cookie server which supports custom cookie
     * lifetime duration.
     */
    public CPProtocol(PhyProtocol phyP, int cookie_lifetime_ms) {
        this.PhyProto = phyP;
        this.cookie_lifetime_ms = cookie_lifetime_ms;
        this.role = cp_role.COOKIE;
        this.cookieMap = new HashMap<>();
        this.rnd = new Random();
    }

    public void setCookieServer(InetAddress rname, int rp) throws UnknownHostException {
        this.PhyConfigCookieServer = new PhyConfiguration(rname, rp, proto_id.CP);
    }

    public void setId(int newId) {
        this.id = newId;
    }

    @Override
    public void send(String s, Configuration config) throws IOException, IWProtocolException {
        switch (this.role) {
            case CLIENT:
                if (this.cookie < 0) // if no valid cookie, request one
                    requestCookie();
                CPCommandMsg msg = new CPCommandMsg(this.id, this.cookie);
                msg.create(s);
                this.PhyProto.send(msg.getData(), this.PhyConfigCommandServer);
                this.id++; // guarantee next send will have higher id
                break;
            case COOKIE:
                this.PhyProto.send(s, config);
                break;
            default:
                throw new RuntimeException("Send String method not implemented for role " + this.role + ".");
        }
    }

    @Override
    public Msg receive() throws IOException, IWProtocolException {
        Msg resMsg = new CPMsg();

        switch (this.role) {
            case CLIENT:
                int i = 0;
                while (i < 3) { // try to receive up to 3 times
                    try {
                        Msg in = this.PhyProto.receive(CP_TIMEOUT);
                        if (((PhyConfiguration) in.getConfiguration()).getPid() != proto_id.CP) // if not CP protocol
                            continue; // do not count this as a try
                        resMsg = ((CPMsg) resMsg).parse(in.getData());
                        if (resMsg instanceof CPCommandResponseMsg
                                && ((CPCommandResponseMsg) resMsg).getId() == this.id - 1) {
                            if (!((CPCommandResponseMsg) resMsg).getSuccess())
                                break; // command was rejected, so cookie timed out
                            return resMsg;
                        }
                    } catch (SocketTimeoutException e) {
                        i++;
                    } catch (IWProtocolException ignored) {
                    }
                }
                if (i == 3) // if all 3 tries timed out
                    throw new ReceiveCommandResponseException(); // unable to receive command response
                throw new CookieTimeoutException(); // otherwise, we must've hit the break due to a timed-out cookie and
                                                    // thus a rejected command
            case COOKIE:
                while (true) { // block until cookie is received
                    try {
                        Msg in = this.PhyProto.receive();
                        if (((PhyConfiguration) in.getConfiguration()).getPid() != proto_id.CP) // if not CP protocol
                            continue;
                        resMsg = ((CPMsg) resMsg).parse(in.getData());
                        if (resMsg instanceof CPCookieRequestMsg) {
                            cookie_process((CPMsg) resMsg);
                            return resMsg;
                        }
                    } catch (IWProtocolException ignored) {
                    }
                }
            default:
                throw new RuntimeException("Receive method not implemented for role " + this.role + ".");
        }
    }

    // CookieServer processing of incoming messages
    // Only CookieCommandMsg are processed, all others are ignored
    private Msg command_process(CPMsg cpmIn) throws IWProtocolException {
        CPCommandMsg stored = null;

        return stored;
    }

    private void evict_expired_cookies() {
        long curTime = System.currentTimeMillis();
        for (Entry<PhyConfiguration, Cookie> e : this.cookieMap.entrySet()) {
            if (curTime > e.getValue().getTimeOfCreation() + this.cookie_lifetime_ms) {
                this.cookieMap.remove(e.getKey());
            }
        }
    }

    // Processing of the CookieRequestMsg
    private void cookie_process(CPMsg cpmIn) throws IWProtocolException, IOException {
        evict_expired_cookies(); // evict old cookies to make room for the upcoming new cookie if possible

        PhyConfiguration conf = (PhyConfiguration) cpmIn.getConfiguration();

        if (cookieMap.size() >= CP_HASHMAP_SIZE) {
            CPCookieResponseMsg resp = new CPCookieResponseMsg(false);
            resp.create("Max number of clients (20) currently have a valid cookie. Please try again later.");
            send(resp.getData(), conf);
        }

        Cookie ck = new Cookie(System.currentTimeMillis(), rnd.nextInt()); // create cookie

        // send cookie response
        CPCookieResponseMsg resp = new CPCookieResponseMsg(true);
        resp.create("" + ck.getCookieValue());
        send(resp.getData(), conf);

        // add cookie to hash map
        cookieMap.put(conf, ck);
    }

    // Method for the client to request a cookie
    public void requestCookie() throws IOException, IWProtocolException {
        CPCookieRequestMsg reqMsg = new CPCookieRequestMsg();
        reqMsg.create(null);
        Msg resMsg = new CPMsg();

        boolean waitForResp = true; // TODO: remove and just use break
        int count = 0;
        while (waitForResp && count < 3) {
            this.PhyProto.send(new String(reqMsg.getDataBytes()), this.PhyConfigCookieServer);

            try {
                Msg in = this.PhyProto.receive(CP_TIMEOUT);
                if (((PhyConfiguration) in.getConfiguration()).getPid() != proto_id.CP)
                    continue;
                resMsg = ((CPMsg) resMsg).parse(in.getData());
                if (resMsg instanceof CPCookieResponseMsg)
                    waitForResp = false;
            } catch (SocketTimeoutException e) {
                count += 1;
            } catch (IWProtocolException ignored) {
            }
        }

        if (count == 3)
            throw new CookieRequestException();
        if (resMsg instanceof CPCookieResponseMsg && !((CPCookieResponseMsg) resMsg).getSuccess()) {
            throw new CookieRequestException();
        }
        assert resMsg instanceof CPCookieResponseMsg;
        this.cookie = ((CPCookieResponseMsg) resMsg).getCookie();
    }
}

class Cookie {
    private final long timeOfCreation;
    private final int cookieValue;

    public Cookie(long toc, int c) {
        this.timeOfCreation = toc;
        this.cookieValue = c;
    }

    public long getTimeOfCreation() {
        return timeOfCreation;
    }

    public int getCookieValue() {
        return cookieValue;
    }
}
