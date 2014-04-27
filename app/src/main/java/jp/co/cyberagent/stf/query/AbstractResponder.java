package jp.co.cyberagent.stf.query;

import android.content.Context;

import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.InvalidProtocolBufferException;

import jp.co.cyberagent.stf.proto.Wire;

abstract public class AbstractResponder {
    Context context;

    protected AbstractResponder(Context context) {
        this.context = context;
    }

    abstract public GeneratedMessage respond(Wire.RequestEnvelope envelope) throws InvalidProtocolBufferException;
    abstract public void cleanup();
}
