package cp;

import core.Msg;
import exceptions.BadChecksumException;
import exceptions.IWProtocolException;
import exceptions.IllegalMsgException;
import java.util.zip.CRC32;

public class CPMsg extends Msg {
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
            case CPCommandMsg.CP_CMD_HEADER:
                parsedMsg = new CPCommandMsg();
                break;
            case CPCommandResponseMsg.CP_CMD_RESP_HEADER:
                parsedMsg = new CPCommandResponseMsg();
                break;
            case CPCookieRequestMsg.CP_CREQ_HEADER:
                parsedMsg = new CPCookieRequestMsg();
                break;
            case CPCookieResponseMsg.CP_CRES_HEADER:
                parsedMsg = new CPCookieResponseMsg();
                break;
            case CPCookieVerReqMsg.CP_CK_VER_REQ_HEADER:
                parsedMsg = new CPCookieVerReqMsg();
                break;
            case CPCookieVerRespMsg.CP_CK_VER_RESP_HEADER:
                parsedMsg = new CPCookieVerRespMsg();
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

    public static long getCrc(String data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data.getBytes());
        return crc32.getValue();
    }

    /**
     * Get the message field from the WS-delimited parts of a CPMsg, checking
     * CRC and, optionally, message length.
     * 
     * Such a message field is defined as a part of the CPMSg between some other
     * field and the checksum field (separated from each by whitespace).
     * Importantly, this means that for a command message (which has fields command
     * and message between length and checksum), the returned "message field" will
     * include both the actual command and message fields. If this message field as
     * defined here is empty, null is returned.
     * 
     * The checksum of the full CPMsg message must match the checksum field. If
     * checkLength is true, the message field must be of the length specified
     * in the length field, which is expected to be the field before the message.
     */
    protected static String getMsgField(String fullMsg, String[] parts, boolean checkLength)
            throws IllegalMsgException, BadChecksumException {
        // If checking length, at least length and msg/crc fields must be present,
        // otherwise at least msg/crc fields.
        if ((checkLength && parts.length < 2) || parts.length < 1)
            throw new IllegalMsgException();

        int crcIndex = parts[parts.length - 1].lastIndexOf(" ");
        String msgField = (crcIndex != -1) ? parts[parts.length - 1].substring(0, crcIndex).trim() : "";
        String crcField = parts[parts.length - 1].substring(crcIndex + 1);

        if (checkLength) {
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
