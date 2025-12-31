package link.e4mc.iroh;

record CallbackResolve<T>(Resolvable<T> future, DeferredInitializer<T> value) implements Runnable {
    @Override
    public void run() {
        T resolved;
        try {
            resolved = value.resolve();
        } catch (Throwable ex) {
            future.reject(ex);
            return;
        }
        future.resolve(resolved);
    }
}
