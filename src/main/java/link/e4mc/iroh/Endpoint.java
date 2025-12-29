package link.e4mc.iroh;

import java.util.concurrent.CompletableFuture;

public class Endpoint implements AutoCloseable {
    @SuppressWarnings({"unused", "FieldMayBeFinal"})
    private long ptrEndpointBundle = 0;
    @SuppressWarnings({"unused", "FieldMayBeFinal"})
    private long ptrCallbackReceiver = 0;

    public Endpoint(byte[][] alpns) {
        this(alpns, null);
    }

    public Endpoint(byte[][] alpns, String[] relays) {
        Native.initEndpointBundle(this, alpns, relays);
    }

    public Runnable pollCallbackLoop() {
        return Native.pollEndpointBundle(this);
    }

    public String address() {
        return Native.addrEndpointBundle(this);
    }

    public CompletableFuture<String> waitOnline() {
        CompletableFuture<String> fut = new CompletableFuture<>();
        Native.onlineEndpointBundle(this, fut);
        return fut;
    }

    public CompletableFuture<CompletableFuture<Connection>> accept() {
        CompletableFuture<Connection> fut = new CompletableFuture<>();
        CompletableFuture<Void> preFuture = new CompletableFuture<>();
        Native.acceptEndpointBundle(this, preFuture, fut);
        return preFuture.thenApply(nothing -> fut);
    }

    public CompletableFuture<Connection> connect(String addr, byte[] alpn) {
        CompletableFuture<Connection> fut = new CompletableFuture<>();
        Native.connectEndpointBundle(this, addr, alpn, fut);
        return fut;
    }

    public CompletableFuture<Void> closeAsync() {
        CompletableFuture<Void> fut = new CompletableFuture<>();
        Native.closeEndpointBundle(this, fut);
        return fut;
    }

    @Override
    public void close() {
        Native.freeEndpointBundle(this);
    }
}
