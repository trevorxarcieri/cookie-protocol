package cp;

import core.Msg;
import exceptions.BadChecksumException;
import exceptions.IWProtocolException;
import exceptions.IllegalMsgException;

class CPCommandResponseMsg extends CPMsg {
    protected static final String CP_CMD_RESP_HEADER = "command_response";
    protected int id;
    protected boolean success;

    protected CPCommandResponseMsg() {
        super();
    }

    protected CPCommandResponseMsg(int id, boolean success) {
        super();
        this.id = id;
        this.success = success;
    }

    protected int getId() {
        return this.id;
    }

    protected boolean getSuccess() {
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
        this.success = parts[2].equals("ok");

        int crcDelim = parts[4].lastIndexOf(" ");
        String msgField = (crcDelim != -1) ? parts[4].substring(0, crcDelim) : "";
        String crcField = parts[4].substring(crcDelim + 1);
        try {
            int len = Integer.parseInt(parts[3]);
            if (len < 0)
                throw new IllegalMsgException();
            if (len != msgField.length()) {// if length field does not match message length
                throw new IllegalMsgException(); // message is illegal, so fail
            }
            if (len != 0)
                this.data = msgField;
        } catch (NumberFormatException e) {
            throw new IllegalMsgException();
        }

        String[] protectedData = !msgField.isEmpty() ? new String[] { parts[0], parts[1], parts[2], parts[3], msgField }
                : new String[] { parts[0], parts[1], parts[2], parts[3] };
        long crc = super.getCrc(String.join(" ", protectedData)); // calculate checksum
        try {
            if (crc != Long.parseLong(crcField)) {
                throw new BadChecksumException();
            }
        } catch (NumberFormatException e) {
            throw new IllegalMsgException();
        }

        return this;
    }
}