package Internal;

import delegates.Action1;
import delegates.Func1;

public class DefaultPolicy<T> implements IPolicy {

    public String Name = DefaultPolicy.class.getName();

    @Override
    public int getPoolSize() {
        return PoolSize;
    }
    @Override
    public void setPoolSize(int poolSize) {
        PoolSize = poolSize;
    }

    public int PoolSize = 100;
    public long SyncGetTimeout = 50000;
    public long IdleTimeout = 50000;
    @Override
    public String getName() {
        return Name;
    }
    @Override
    public void setName(String name) {
        Name = name;
    }
    @Override
    public long getSyncGetTimeout() {
        return SyncGetTimeout;
    }
    @Override
    public void setSyncGetTimeout(long syncGetTimeout) {
        SyncGetTimeout = syncGetTimeout;
    }
    @Override
    public long getIdleTimeout() {
        return IdleTimeout;
    }
    @Override
    public void setIdleTimeout(long idleTimeout) {
        IdleTimeout = idleTimeout;
    }
    @Override
    public int getAsyncGetCapacity() {
        return AsyncGetCapacity;
    }
    @Override
    public void setAsyncGetCapacity(int asyncGetCapacity) {
        AsyncGetCapacity = asyncGetCapacity;
    }
    @Override
    public boolean isThrowGetTimeoutException() {
        return IsThrowGetTimeoutException;
    }
    @Override
    public void setThrowGetTimeoutException(boolean throwGetTimeoutException) {
        IsThrowGetTimeoutException = throwGetTimeoutException;
    }
    @Override
    public boolean isAutoDisposeWithSystem() {
        return IsAutoDisposeWithSystem;
    }
    @Override
    public void setAutoDisposeWithSystem(boolean autoDisposeWithSystem) {
        IsAutoDisposeWithSystem = autoDisposeWithSystem;
    }
    @Override
    public int getCheckAvailableInterval() {
        return CheckAvailableInterval;
    }
    @Override
    public void setCheckAvailableInterval(int checkAvailableInterval) {
        CheckAvailableInterval = checkAvailableInterval;
    }

    public int AsyncGetCapacity = 10000;
    public boolean IsThrowGetTimeoutException = true;
    public boolean IsAutoDisposeWithSystem = true;
    public int CheckAvailableInterval = 5;

    public Func1<T> CreateObject;

    public Action1<ObjectWarp<T>> OnGetObject;

    public T OnCreate() {
        return CreateObject.invoke();
    }

    @Override
    public void OnDestroy(Object obj) {

    }

    public void OnGetTimeout() {

    }

    @Override
    public void OnGet(ObjectWarp obj) {
        if (OnGetObject != null)
            OnGetObject.invoke(obj);
    }

    @Override
    public void OnReturn(ObjectWarp obj) {

    }

    @Override
    public boolean OnCheckAvailable(ObjectWarp obj) {
        return false;
    }

    public void OnAvailable() {

    }

    public void OnUnavailable() {

    }
}
