package link.e4mc.iroh;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public class Stream implements AutoCloseable {
    @SuppressWarnings({"unused", "FieldMayBeFinal"})
    private long ptrIrohStream = 0;

    // Must only be constructed from JNI
    private Stream() {}

    public CompletableFuture<Long> readIrohStreamByteBuffer(ByteBuffer buffer, long offset, long maxlen) {
        CompletableFuture<Long> fut = new CompletableFuture<>();
        Native.readIrohStreamByteBuffer(this, buffer, offset, maxlen, fut);
        return fut;
    }

    public CompletableFuture<byte[]> readIrohStreamByteArray(long maxlen) {
        CompletableFuture<byte[]> fut = new CompletableFuture<>();
        Native.readIrohStreamByteArray(this, maxlen, fut);
        return fut;
    }

    public CompletableFuture<Void> writeIrohStreamByteBuffer(ByteBuffer buffer, long offset, long len) {
        CompletableFuture<Void> fut = new CompletableFuture<>();
        Native.writeIrohStreamByteBuffer(this, buffer, offset, len, fut);
        return fut;
    }

    public CompletableFuture<Void> writeIrohStreamByteArray(byte[] array, long offset, long len) {
        CompletableFuture<Void> fut = new CompletableFuture<>();
        Native.writeIrohStreamByteArray(this, array, offset, len, fut);
        return fut;
    }

    @Override
    public void close() {
        Native.freeIrohStream(this);
    }
}
