package jp.co.cyberagent.stf.io;

import com.google.protobuf.GeneratedMessageLite;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MessageWriter implements MessageWritable {
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private OutputStream out;

    public static interface Writable {
        public void write(final GeneratedMessageLite message);
    }

    public MessageWriter(OutputStream out) {
        this.out = out;
    }

    public void write(final GeneratedMessageLite message) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    message.writeDelimitedTo(out);
                }
                catch (IOException e) {
                    // The socket went away
                    executor.shutdownNow();
                }
            }
        });
    }

    public static class Pool implements MessageWritable {
        Set<MessageWriter> writers = Collections.synchronizedSet(new HashSet<MessageWriter>());

        public void add(MessageWriter writer) {
            writers.add(writer);
        }

        public void remove(MessageWriter writer) {
            writers.remove(writer);
        }

        public void write(final GeneratedMessageLite message) {
            for (MessageWriter writer : writers) {
                writer.write(message);
            }
        }
    }
}
