package cp;

import core.Msg;
import exceptions.IllegalMsgException;

class CPCommandMsg extends CPMsg {
    protected static final String CP_CMD_HEADER = "command";

    /*
     * Create command message.
     * The cp header is prepended in the super-class.
     */
    @Override
    protected void create(String data) {
        // prepend command header
        data = CP_CMD_HEADER + data;
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