package cp;

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.google.gson.Gson;

import core.Msg;
import core.Protocol;
import exceptions.IWProtocolException;
import exceptions.IllegalMsgException;
import phy.PhyConfiguration;

class CPCookieVerReqMsg extends CPMsg {
    protected static final String CP_CK_VER_REQ_HEADER = "cookie_verification_request";
    private static final Gson GSON = new Gson();
    private int cookie;

    protected CPCookieVerReqMsg() {
        super();
    }

    protected CPCookieVerReqMsg(int cookie) throws IllegalMsgException {
        super();
        this.cookie = cookie;
    }

    public int getCookie() {
        return this.cookie;
    }

    @Override
    protected void create(String data) {
        data = CP_CK_VER_REQ_HEADER + " " + this.cookie + " " + data;
        data += " " + super.getCrc(data);
        super.create(data);
    }

    protected String create(PhyConfiguration config) {
        ClientEndpoint endpoint = new ClientEndpoint(config.getRemoteIPAddress(), config.getRemotePort());
        create(GSON.toJson(endpoint));
        return this.getData();
    }

    @Override
    protected Msg parse(String sentence) throws IWProtocolException {
        if (!sentence.startsWith(CP_CK_VER_REQ_HEADER)) {
            throw new IllegalMsgException();
        }

        String[] parts = sentence.split("\\s+", 3);
        if (parts.length < 3)
            throw new IllegalMsgException();

        try {
            this.cookie = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalMsgException();
        }

        this.data = super.getMsgField(sentence, parts, false);

        return this;
    }

    protected PhyConfiguration getClientConfiguration() throws UnknownHostException {
        ClientEndpoint endpoint = GSON.fromJson(this.data, ClientEndpoint.class);
        return new PhyConfiguration(endpoint.ip(), endpoint.udp(), Protocol.proto_id.CP);
    }
}

record ClientEndpoint(InetAddress ip, int udp) {
}