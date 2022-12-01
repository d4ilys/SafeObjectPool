package Internal;

import delegates.Action1;
import delegates.Func1;

public class DefaultPolicy<T> {
    public String Name = DefaultPolicy.class.getName();
    public int PoolSize = 100;
    public long SyncGetTimeout = 50000;
    public long IdleTimeout = 50000;
    public int AsyncGetCapacity = 10000;
    public boolean IsThrowGetTimeoutException = true;
    public boolean IsAutoDisposeWithSystem = true;
    public int CheckAvailableInterval = 5;

    public Func1<T> CreateObject;
    public Action1<ObjectWarp<T>> OnGetObject;

    public T OnCreate() {
        return CreateObject.invoke();
    }

    public void OnDestroy(T obj) {

    }

    public void OnGet(ObjectWarp<T> obj) {
        if (OnGetObject != null)
            OnGetObject.invoke(obj);
    }

    public void OnGetTimeout() {

    }

    public void OnReturn(ObjectWarp<T> obj) {

    }

    public boolean OnCheckAvailable(ObjectWarp<T> obj) {
        return true;
    }

    public void OnAvailable() {

    }

    public void OnUnavailable() {

    }
}
