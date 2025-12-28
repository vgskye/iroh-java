package link.e4mc.iroh;

import java.util.concurrent.CompletableFuture;

record CallbackReject(CompletableFuture<?> future, Throwable ex) implements Runnable {
    @Override
    public void run() {
        future.completeExceptionally(ex);
    }
}
