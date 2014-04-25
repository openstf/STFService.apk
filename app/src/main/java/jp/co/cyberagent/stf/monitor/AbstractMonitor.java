package jp.co.cyberagent.stf.monitor;

import android.content.Context;

import jp.co.cyberagent.stf.io.MessageWriter;

abstract public class AbstractMonitor extends Thread {
    Context context;
    MessageWriter.Pool writer;

    public AbstractMonitor(Context context, MessageWriter.Pool writer) {
        this.context = context;
        this.writer = writer;
    }

    abstract public void peek();
}
