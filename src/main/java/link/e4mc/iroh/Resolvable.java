package link.e4mc.iroh;

public interface Resolvable<T> {
    void resolve(T value);
    void reject(Throwable cause);
}
