package cp;

import core.Msg;
import exceptions.IllegalMsgException;

import java.util.zip.CRC32;

class CPCommandMsg extends CPMsg {
    protected static final String CP_CMD_HEADER = "command";
    protected int id;
    private int cookie;

    protected CPCommandMsg() {
        super();
    }

    protected CPCommandMsg(int id, int cookie) throws IllegalMsgException {
        super();
        if (id > 65535 || id < 0) {
            throw new IllegalMsgException();
        }
        this.id = id;
        this.cookie = cookie;
    }

    /*
     * Create command message.
     * The cp header is prepended in the super-class.
     */
    @Override
    protected void create(String data) {
        // prepend command header
        data = CP_CMD_HEADER + " " + this.id + " " + this.cookie + " " + data.length() + " " + data;
        CRC32 crc32 = new CRC32();
        crc32.update(data.getBytes()); // calculate checksum
        data += " " + crc32.getValue(); // append checksum
        // super class prepends cp header
        super.create(data);
    }

    @Override
    protected Msg parse(String sentence) throws IllegalMsgException {
        if (!sentence.startsWith(CP_CMD_HEADER)) {
            throw new IllegalMsgException();
        }
        return this;
    }
}