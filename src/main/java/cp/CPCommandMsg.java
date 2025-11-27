package cp;

import core.Msg;
import exceptions.IWProtocolException;
import exceptions.IllegalMsgException;

public class CPCommandMsg extends CPMsg {
    protected static final String CP_CMD_HEADER = "command";
    protected int id;
    private int cookie;
    private CommandType commandType;
    private String msgField;

    protected CPCommandMsg() {
        super();
        this.commandType = CommandType.UNKNOWN;
    }

    protected CPCommandMsg(int id, int cookie) throws IllegalMsgException {
        super();
        if (id > 65535 || id < 0) {
            throw new IllegalMsgException();
        }
        this.id = id;
        this.cookie = cookie;
        this.commandType = CommandType.UNKNOWN;
    }

    public int getId() {
        return this.id;
    }

    public int getCookie() {
        return this.cookie;
    }

    public CommandType getCommandType() {
        return this.commandType;
    }

    public String getMsgField() {
        return this.msgField;
    }

    /*
     * Create command message.
     * The cp header is prepended in the super-class.
     */
    @Override
    protected void create(String data) {
        // prepend command header
        data = CP_CMD_HEADER + " " + this.id + " " + this.cookie + " " + data.length() + " " + data;
        data += " " + super.getCrc(data); // append checksum
        // super class prepends cp header
        super.create(data);
    }

    @Override
    protected Msg parse(String sentence) throws IWProtocolException {
        if (!sentence.startsWith(CP_CMD_HEADER)) {
            throw new IllegalMsgException();
        }

        String[] parts = sentence.split("\\s+", 5);
        if (parts.length < 5)
            throw new IllegalMsgException();

        try {
            this.id = Integer.parseInt(parts[1]);
            this.cookie = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            throw new IllegalMsgException();
        }
        if (this.id > 65535 || this.id < 0) {
            throw new IllegalMsgException();
        }

        this.data = super.getMsgField(sentence, parts, true);
        if (this.data.equals("status")) {
            this.commandType = CommandType.STATUS;
        } else if (this.data.startsWith("print ")) {
            this.commandType = CommandType.PRINT;
            this.msgField = this.data.split("\\s+", 2)[1];
        } else {
            this.commandType = CommandType.UNKNOWN;
        }

        return this;
    }
}

enum CommandType {
    STATUS,
    PRINT,
    UNKNOWN
}