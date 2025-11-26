package cp;

import core.Msg;
import exceptions.BadChecksumException;
import exceptions.IWProtocolException;
import exceptions.IllegalMsgException;
import java.util.zip.CRC32;

class CPMsg extends Msg {
    protected static final String CP_HEADER = "cp";

    @Override
    protected void create(String sentence) {
        data = CP_HEADER + " " + sentence;
        this.dataBytes = data.getBytes();
    }

    @Override
    protected Msg parse(String sentence) throws IWProtocolException {
        CPMsg parsedMsg;
        if (!sentence.startsWith(CP_HEADER + " "))
            throw new IllegalMsgException();

        String[] parts = sentence.split("\\s+", 2);
        if (parts.length < 2)
            throw new IllegalMsgException();

        String msgType = parts[1].split("\\s+", 2)[0];
        switch (msgType) {
            case CPCookieRequestMsg.CP_CREQ_HEADER:
                parsedMsg = new CPCookieRequestMsg();
                break;
            case CPCookieResponseMsg.CP_CRES_HEADER:
                parsedMsg = new CPCookieResponseMsg();
                break;
            case CPCommandMsg.CP_CMD_HEADER:
                parsedMsg = new CPCommandMsg();
                break;
            case CPCommandResponseMsg.CP_CMD_RESP_HEADER:
                parsedMsg = new CPCommandResponseMsg();
                break;
            default:
                throw new IllegalMsgException();
        }

        parsedMsg = (CPMsg) parsedMsg.parse(parts[1]);
        return parsedMsg;
    }

    protected Msg parse(Msg m) throws IWProtocolException {
        Msg ret = parse(m.getData());
        ret.setConfiguration(m.getConfiguration());
        return ret;
    }

    protected static long getCrc(String data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data.getBytes());
        return crc32.getValue();
    }

    /**
     * Get the message field from the WS-delimited parts of a CPMsg, checking
     * message length and CRC.
     * 
     * Such a message field is defined as a part of the CPMSg between the length
     * field and the checksum field (separated from each by whitespace).
     * Importantly, this means that for a command message (which has fields command
     * and message between length and checksum), the message field will include both
     * command and message. If the message field is empty, null is returned.
     * 
     * The message field must be of the length specified in the length field, and
     * the checksum of the full CPMsg message must match the checksum field.
     */
    protected static String getMsgField(String fullMsg, String[] parts)
            throws IllegalMsgException, BadChecksumException {
        if (parts.length < 2) // at least length and msg/crc fields must be present
            throw new IllegalMsgException();

        int crcIndex = parts[parts.length - 1].lastIndexOf(" ");
        String msgField = (crcIndex != -1) ? parts[parts.length - 1].substring(0, crcIndex).trim() : "";
        String crcField = parts[parts.length - 1].substring(crcIndex + 1);

        try {
            int len = Integer.parseInt(parts[parts.length - 2]);
            if (len < 0)
                throw new IllegalMsgException();
            if (len != msgField.length()) { // if length field does not match message length
                throw new IllegalMsgException(); // message is illegal, so fail
            }
        } catch (NumberFormatException e) {
            throw new IllegalMsgException();
        }

        long receivedCrc;
        try {
            receivedCrc = Long.parseLong(crcField);
        } catch (NumberFormatException e) {
            throw new IllegalMsgException();
        }

        long computedCrc = getCrc(fullMsg.substring(0, fullMsg.lastIndexOf(" " + crcField)));
        if (computedCrc != receivedCrc) {
            throw new BadChecksumException();
        }

        return msgField.isEmpty() ? null : msgField;
    }

}
