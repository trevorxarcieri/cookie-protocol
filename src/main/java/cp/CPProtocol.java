package cp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Random;

import core.Configuration;
import core.Msg;
import core.Protocol;
import exceptions.CookieRequestException;
import exceptions.CookieTimeoutException;
import exceptions.IWProtocolException;
import exceptions.ReceiveCommandResponseException;
import phy.PhyConfiguration;
import phy.PhyProtocol;

public class CPProtocol extends Protocol {
    private static final int CP_TIMEOUT = 2000;
    private static final int CP_HASHMAP_SIZE = 20;
    private static final int COOKIE_LIFETIME_MS = 60000;
    private int cookie;
    private int id;
    private int max_num_clients;
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
    public CPProtocol(PhyProtocol phyP, boolean isCookieServer, Integer max_num_clients, Integer cookie_lifetime_ms) {
        this.PhyProto = phyP;
        if (isCookieServer) {
            this.role = cp_role.COOKIE;
            this.cookieMap = new HashMap<>();
            this.rnd = new Random();
            this.max_num_clients = max_num_clients == null ? CP_HASHMAP_SIZE : max_num_clients;
            this.cookie_lifetime_ms = cookie_lifetime_ms == null ? COOKIE_LIFETIME_MS : cookie_lifetime_ms;
        } else {
            this.role = cp_role.COMMAND;
            this.pendingCommands = new ArrayList<>();
        }
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

                // Otherwise, we must've hit the break due to a rejected command which means the
                // cookie most likely timed out.
                this.cookie = -1; // invalidate cookie
                throw new CookieTimeoutException();
            case COOKIE:
                while (true) { // block until cookie is received
                    try {
                        Msg in = this.PhyProto.receive();
                        if (((PhyConfiguration) in.getConfiguration()).getPid() != proto_id.CP) // if not CP protocol
                            continue;
                        resMsg = ((CPMsg) resMsg).parse(in);
                        if (resMsg instanceof CPCookieRequestMsg) {
                            cookie_process((CPMsg) resMsg);
                            return resMsg;
                        }
                    } catch (IWProtocolException ignored) {
                    }
                }
            case COMMAND:
                while (true) { // block until command is received
                    try {
                        Msg in = this.PhyProto.receive();
                        if (((PhyConfiguration) in.getConfiguration()).getPid() != proto_id.CP) // if not CP protocol
                            continue;
                        resMsg = ((CPMsg) resMsg).parse(in);
                        if (resMsg instanceof CPCommandMsg) {
                            Msg resp = command_process((CPMsg) resMsg);
                            return resp;
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

    private void send_cookie_resp(Cookie ck, PhyConfiguration conf) throws IWProtocolException, IOException {
        CPCookieResponseMsg resp = new CPCookieResponseMsg(true);
        resp.create("" + ck.getCookieValue());
        send(resp.getData(), conf);
    }

    /**
     * Process a cookie request from a client.
     * 
     * Cookie renewal is idempotent: if the client requests a cookie while its
     * current cookie is still valid, the cookie server just returns the existing
     * cookie. This simplifies client retries (they can always request again on
     * failure), avoids unnecessary growth in issued cookies, and aligns with
     * typical web API design where repeated requests do not create unnecessary new
     * state.
     */
    private void cookie_process(CPMsg cpmIn) throws IWProtocolException, IOException {
        evict_expired_cookies(); // evict old cookies to make room for the upcoming new cookie if possible

        PhyConfiguration conf = (PhyConfiguration) cpmIn.getConfiguration();

        if (cookieMap.containsKey(conf)) { // if client already has a valid cookie, resend it
            Cookie ck = cookieMap.get(conf);
            send_cookie_resp(ck, conf);
            return;
        }

        if (cookieMap.size() >= max_num_clients) { // if hashmap is full, deny cookie request
            CPCookieResponseMsg resp = new CPCookieResponseMsg(false);
            resp.create("Max number of clients currently have a valid cookie. Please try again later.");
            send(resp.getData(), conf);
            return;
        }

        int cookieVal;
        do {
            cookieVal = rnd.nextInt() & 0x7FFFFFFF; // ensure cookie value is positive
        } while (cookieMap.containsValue(new Cookie(0, cookieVal))); // ensure cookie value is unique
        Cookie ck = new Cookie(System.currentTimeMillis(), cookieVal); // create cookie
        send_cookie_resp(ck, conf); // send cookie response
        cookieMap.put(conf, ck); // add cookie to hash map
    }

    // Method for the client to request a cookie
    public void requestCookie() throws IOException, IWProtocolException {
        CPCookieRequestMsg reqMsg = new CPCookieRequestMsg();
        reqMsg.create(null);
        Msg resMsg = new CPMsg();

        int count = 0;
        while (count < 3) {
            this.PhyProto.send(new String(reqMsg.getDataBytes()), this.PhyConfigCookieServer);

            try {
                Msg in = this.PhyProto.receive(CP_TIMEOUT);
                if (((PhyConfiguration) in.getConfiguration()).getPid() != proto_id.CP)
                    continue;
                resMsg = ((CPMsg) resMsg).parse(in.getData());
                if (resMsg instanceof CPCookieResponseMsg) {
                    CPCookieResponseMsg ckRespMsg = (CPCookieResponseMsg) resMsg;

                    if (!ckRespMsg.getSuccess()) {
                        break; // cookie request was denied, break
                    }

                    this.cookie = ckRespMsg.getCookie(); // set cookie
                    return;
                }
            } catch (SocketTimeoutException e) {
                count += 1;
            } catch (IWProtocolException ignored) {
            }
        }

        throw new CookieRequestException(); // all 3 tries timed out or a req was denied, throw
    }
}
