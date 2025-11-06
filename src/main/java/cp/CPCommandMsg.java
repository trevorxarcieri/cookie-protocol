package cp;

import core.Msg;
import exceptions.IWProtocolException;
import exceptions.IllegalMsgException;

import java.util.zip.CRC32;
import java.util.zip.Checksum;

class CPCommandMsg extends Msg {
    protected static final String CP_HEADER = "cp";

    @Override
    protected void create(String sentence) {
        data = CP_HEADER + " " + sentence;
        this.dataBytes = data.getBytes();
    }

    @Override
    protected Msg parse(String sentence) throws IWProtocolException {
        CPCommandMsg parsedMsg;
        parsedMsg = new CPCommandMsg();
        return parsedMsg;
    }

}
