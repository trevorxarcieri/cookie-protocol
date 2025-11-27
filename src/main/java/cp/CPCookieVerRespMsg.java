package cp;

import core.Msg;
import exceptions.IWProtocolException;
import exceptions.IllegalMsgException;

class CPCookieVerRespMsg extends CPMsg {
    protected static final String CP_CK_VER_RESP_HEADER = "cookie_verification_response";
    private int cookie;
    private boolean success;

    protected CPCookieVerRespMsg() {
        super();
    }

    protected CPCookieVerRespMsg(int cookie, boolean success) throws IllegalMsgException {
        super();
        this.cookie = cookie;
        this.success = success;
    }

    public int getCookie() {
        return this.cookie;
    }

    public boolean getSuccess() {
        return this.success;
    }

    @Override
    protected void create(String data) {
        data = CP_CK_VER_RESP_HEADER + " " + (this.success ? "ok" : "error") + " " + this.cookie;
        if (!success) {
            data += " " + data.length() + " " + data;
        }
        data += " " + super.getCrc(data);
        super.create(data);
    }

    @Override
    protected Msg parse(String sentence) throws IWProtocolException {
        if (!sentence.startsWith(CP_CK_VER_RESP_HEADER)) {
            throw new IllegalMsgException();
        }

        String[] parts = sentence.split("\\s+", 4);
        if (parts.length < 4)
            throw new IllegalMsgException();

        this.success = parts[1].equals("ok");
        try {
            this.cookie = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            throw new IllegalMsgException();
        }

        if (!this.success) { // if successful, split off length field and check it
            parts = sentence.split("\\s+", 5);
            if (parts.length < 5)
                throw new IllegalMsgException();
            this.data = super.getMsgField(sentence, parts, true);
        } else { // otherwise just check CRC
            this.data = super.getMsgField(sentence, parts, false);
        }

        return this;
    }
}