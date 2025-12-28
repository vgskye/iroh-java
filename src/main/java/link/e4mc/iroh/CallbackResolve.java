package link.e4mc.iroh;

import java.util.concurrent.CompletableFuture;

record CallbackResolve<T>(CompletableFuture<T> future, DeferredInitializer<T> value) implements Runnable {
    @Override
    public void run() {
        T resolved;
        try {
            resolved = value.resolve();
        } catch (Throwable ex) {
            future.completeExceptionally(ex);
            return;
        }
        future.complete(resolved);
    }
}
