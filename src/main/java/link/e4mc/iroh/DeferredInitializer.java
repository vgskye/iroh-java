package link.e4mc.iroh;

class DeferredInitializer<T> {
    @SuppressWarnings({"unused", "FieldMayBeFinal"})
    private long ptrDeferredObjectMaker = 0;

    // Must only be constructed from JNI
    private DeferredInitializer() {}

    @SuppressWarnings("unchecked")
    T resolve() {
        return (T) Native.resolveDeferredInitializer(this);
    }
}
