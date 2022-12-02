package Internal;


import delegates.Action1;
import delegates.Func1;

public interface IPolicy<T> {

    Func1 CreateObject = null;

    Action1<ObjectWarp> OnGetObject = null;

    String Name = null;

    //池容量
    int PoolSize = 1;

    int getPoolSize();

    void setPoolSize(int poolSize);

    /// 默认获取超时设置
    long SyncGetTimeout = 10000;

    /// 空闲时间，获取时若超出，则重新创建
    long IdleTimeout = 10000;

    /// 异步获取排队队列大小，小于等于0不生效
    int AsyncGetCapacity = 20;

    /// 获取超时后，是否抛出异常
    boolean IsThrowGetTimeoutException = true;

    /// 监听 AppDomain.CurrentDomain.ProcessExit/Console.CancelKeyPress 事件自动释放
    boolean IsAutoDisposeWithSystem = true;

    /// 后台定时检查可用性间隔秒数
    int CheckAvailableInterval = 10000;

    String getName();

    void setName(String name);

    long getSyncGetTimeout();

    void setSyncGetTimeout(long syncGetTimeout);

    long getIdleTimeout();

    void setIdleTimeout(long idleTimeout);

    int getAsyncGetCapacity();

    void setAsyncGetCapacity(int asyncGetCapacity);

    boolean isThrowGetTimeoutException();

    void setThrowGetTimeoutException(boolean throwGetTimeoutException);

    boolean isAutoDisposeWithSystem();

    void setAutoDisposeWithSystem(boolean autoDisposeWithSystem);

    int getCheckAvailableInterval();

    void setCheckAvailableInterval(int checkAvailableInterval);


    /**
     * 对象池的对象被创建时
     *
     * @return 返回被创建的对象
     */
    T OnCreate();

    /**
     * 销毁对象
     *
     * @param obj 资源对象
     */
    void OnDestroy(T obj);

    /**
     * 从对象池获取对象超时的时候触发，通过该方法统计
     */
    void OnGetTimeout();

    /**
     * 从对象池获取对象成功的时候触发，通过该方法统计或初始化对象
     *
     * @param obj 资源对象
     */
    void OnGet(ObjectWarp<T> obj);

    /**
     * 归还对象给对象池的时候触发
     *
     * @param obj 资源对象
     */
    void OnReturn(ObjectWarp<T> obj);

    /**
     * 检查可用性
     *
     * @param obj 资源对象
     * @return
     */
    boolean OnCheckAvailable(ObjectWarp<T> obj);

    /**
     * 事件：可用时触发
     */
    void OnAvailable();

    /**
     * 事件：不可用时触发
     */
    void OnUnavailable();
}
