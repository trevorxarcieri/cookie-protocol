package cp;

import com.google.gson.Gson;

import core.Msg;
import exceptions.IWProtocolException;
import exceptions.IllegalMsgException;

public class CPCommandResponseMsg extends CPMsg {
    protected static final String CP_CMD_RESP_HEADER = "command_response";
    private static final Gson GSON = new Gson();
    protected int id;
    protected boolean success;

    protected CPCommandResponseMsg() {
        super();
    }

    protected CPCommandResponseMsg(int id, boolean success) throws IllegalMsgException {
        super();
        if (id > 65535 || id < 0) {
            throw new IllegalMsgException();
        }
        this.id = id;
        this.success = success;
    }

    public int getId() {
        return this.id;
    }

    public boolean getSuccess() {
        return this.success;
    }

    /*
     * Create command response message.
     * The cp header is prepended in the super-class.
     */
    @Override
    protected void create(String data) {
        // prepend command response header
        data = CP_CMD_RESP_HEADER + " " + this.id + " " + (this.success ? "ok" : "error") + " " + data.length()
                + (data.isEmpty() ? "" : " " + data);
        data += " " + super.getCrc(data); // append checksum
        // super class prepends cp header
        super.create(data);
    }

    protected String create(int numSuccessfulCommands, Integer cookieTtl) {
        CookieStatus status = new CookieStatus(numSuccessfulCommands, cookieTtl);
        create(GSON.toJson(status));
        return this.getData();
    }

    @Override
    protected Msg parse(String sentence) throws IWProtocolException {
        if (!sentence.startsWith(CP_CMD_RESP_HEADER)) {
            throw new IllegalMsgException();
        }

        String[] parts = sentence.split("\\s+", 5);
        if (parts.length < 5)
            throw new IllegalMsgException();

        try {
            this.id = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalMsgException();
        }
        if (this.id > 65535 || this.id < 0) {
            throw new IllegalMsgException();
        }
        this.success = parts[2].equals("ok");

        this.data = super.getMsgField(sentence, parts, true);

        return this;
    }
}

record CookieStatus(
        int numSuccessfulCommands,
        Integer cookieTtl // null = N/A
) {
}