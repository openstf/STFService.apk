package jp.co.cyberagent.stf.query;

import android.content.Context;

import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;

import jp.co.cyberagent.stf.proto.Wire;

abstract public class AbstractResponder {
    Context context;

    protected AbstractResponder(Context context) {
        this.context = context;
    }

    abstract public GeneratedMessageLite respond(Wire.Envelope envelope) throws InvalidProtocolBufferException;
    abstract public void cleanup();
}
