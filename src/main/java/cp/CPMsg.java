package cp;

import core.Msg;
import exceptions.IWProtocolException;
import exceptions.IllegalMsgException;
import java.util.zip.CRC32;

class CPMsg extends Msg {
    protected static final String CP_HEADER = "cp";

    protected static long getCrc(String data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data.getBytes());
        return crc32.getValue();
    }

    @Override
    protected void create(String sentence) {
        data = CP_HEADER + " " + sentence;
        this.dataBytes = data.getBytes();
    }

    @Override
    protected Msg parse(String sentence) throws IWProtocolException {
        CPMsg parsedMsg;
        if (!sentence.startsWith(CP_HEADER))
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

}
