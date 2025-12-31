package link.e4mc.iroh;

record CallbackReject(Resolvable<?> future, Throwable ex) implements Runnable {
    @Override
    public void run() {
        future.reject(ex);
    }
}
