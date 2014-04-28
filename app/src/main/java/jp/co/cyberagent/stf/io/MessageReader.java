package jp.co.cyberagent.stf.io;

import java.io.IOException;
import java.io.InputStream;

import jp.co.cyberagent.stf.proto.Wire;

public class MessageReader {
    private InputStream in;

    public MessageReader(InputStream in) {
        this.in = in;
    }

    public Wire.Envelope read() throws IOException {
        return Wire.Envelope.parseDelimitedFrom(in);
    }
}
