package link.e4mc.iroh;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public class Native {
    static {
        System.loadLibrary("iroh_java");
    }
    static native Object resolveDeferredInitializer(DeferredInitializer<?> deferredInitializer);
    static native void initEndpointBundle(Endpoint bundle, byte[][] alpns);
    static native void freeEndpointBundle(Endpoint bundle);
    static native Runnable pollEndpointBundle(Endpoint bundle);
    static native String addrEndpointBundle(Endpoint bundle);
    static native void onlineEndpointBundle(Endpoint bundle, CompletableFuture<String> future);
    static native void closeEndpointBundle(Endpoint bundle, CompletableFuture<Void> future);
    static native void acceptEndpointBundle(Endpoint bundle, CompletableFuture<Connection> future);
    static native void connectEndpointBundle(Endpoint bundle, String addr, byte[] alpn, CompletableFuture<Connection> future);
    static native void closeIrohConnection(Connection connection, long code, byte[] reason);
    static native String addrIrohConnection(Connection connection);
    static native void acceptBiIrohConnection(Connection connection, CompletableFuture<Stream> future);
    static native void acceptUniIrohConnection(Connection connection, CompletableFuture<Stream> future);
    static native void openBiIrohConnection(Connection connection, CompletableFuture<Stream> future);
    static native void openUniIrohConnection(Connection connection, CompletableFuture<Stream> future);
    static native void readIrohStreamByteBuffer(Stream stream, ByteBuffer buffer, long offset, long maxlen, CompletableFuture<Long> future);
    static native void readIrohStreamByteArray(Stream stream, long maxlen, CompletableFuture<byte[]> future);
    static native void writeIrohStreamByteBuffer(Stream stream, ByteBuffer buffer, long offset, long len, CompletableFuture<Void> future);
    static native void writeIrohStreamByteArray(Stream stream, byte[] array, long offset, long len, CompletableFuture<Void> future);
    static native void freeIrohStream(Stream stream);
}
