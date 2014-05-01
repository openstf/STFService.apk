package jp.co.cyberagent.stf.io;

import com.google.protobuf.GeneratedMessage;

public interface MessageWritable {
    public void write(final GeneratedMessage message);
}
