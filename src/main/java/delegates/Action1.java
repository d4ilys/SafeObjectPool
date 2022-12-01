package delegates;


@FunctionalInterface
public interface Action1<T> {
    void invoke(T t);
}
