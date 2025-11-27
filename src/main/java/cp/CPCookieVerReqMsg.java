package cp;

import core.Msg;
import exceptions.IWProtocolException;
import exceptions.IllegalMsgException;

class CPCookieVerReqMsg extends CPMsg {
    protected static final String CP_CK_VER_REQ_HEADER = "cookie_verification_request";
    private int cookie;

    protected CPCookieVerReqMsg() {
        super();
    }

    protected CPCookieVerReqMsg(int cookie) throws IllegalMsgException {
        super();
        this.cookie = cookie;
    }

    @Override
    protected void create(String data) {
        data = CP_CK_VER_REQ_HEADER + " " + this.cookie + " " + data;
        data += " " + super.getCrc(data);
        super.create(data);
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
}