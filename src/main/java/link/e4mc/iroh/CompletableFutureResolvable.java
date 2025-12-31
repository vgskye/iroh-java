package link.e4mc.iroh;

import java.util.concurrent.CompletableFuture;

record CompletableFutureResolvable<T>(CompletableFuture<T> future) implements Resolvable<T> {
    @Override
    public void resolve(T value) {
        future.complete(value);
    }

    @Override
    public void reject(Throwable cause) {
        future.completeExceptionally(cause);
    }
}
