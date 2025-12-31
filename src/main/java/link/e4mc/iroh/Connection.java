package link.e4mc.iroh;

import java.util.concurrent.CompletableFuture;

public class Connection implements AutoCloseable {
    @SuppressWarnings({"unused", "FieldMayBeFinal"})
    private long ptrIrohConnection = 0;

    // Must only be constructed from JNI
    private Connection() {}

    public CompletableFuture<Stream> acceptBi() {
        CompletableFuture<Stream> fut = new CompletableFuture<>();
        Native.acceptBiIrohConnection(this, new CompletableFutureResolvable<>(fut));
        return fut;
    }

    public CompletableFuture<Stream> acceptUni() {
        CompletableFuture<Stream> fut = new CompletableFuture<>();
        Native.acceptUniIrohConnection(this, new CompletableFutureResolvable<>(fut));
        return fut;
    }

    public CompletableFuture<Stream> openBi() {
        CompletableFuture<Stream> fut = new CompletableFuture<>();
        Native.openBiIrohConnection(this, new CompletableFutureResolvable<>(fut));
        return fut;
    }

    public CompletableFuture<Stream> openUni() {
        CompletableFuture<Stream> fut = new CompletableFuture<>();
        Native.openUniIrohConnection(this, new CompletableFutureResolvable<>(fut));
        return fut;
    }

    public String peerAddress() {
        return Native.addrIrohConnection(this);
    }

    public byte[] exportKeyingMaterial(byte[] label, byte[] context, int length) {
        return Native.exportKeyingMaterialIrohConnection(this, label, context, length);
    }

    public void close(long code, byte[] reason) {
        Native.closeIrohConnection(this, code, reason);
    }

    @Override
    public void close() {
        close(0, new byte[0]);
    }
}
