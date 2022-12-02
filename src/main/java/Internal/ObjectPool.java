package Internal;

import delegates.Action1;
import delegates.Func1;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**
 * 对象池管理类
 */
public class ObjectPool<T> implements AutoCloseable {
    public IPolicy<T> Policy;
    private List<ObjectWarp<T>> _allObjects = new ArrayList<>();

    public ConcurrentLinkedDeque<ObjectWarp<T>> _freeObjects = new ConcurrentLinkedDeque<>();
    private BlockingQueue<GetSyncQueueInfo> _getSyncQueue = new LinkedBlockingQueue<>();
    private BlockingQueue<Boolean> _getQueue = new LinkedBlockingQueue<>();

    public boolean IsAvailable = this.UnavailableException == null;
    public Exception UnavailableException = null;
    public Date UnavailableTime;
    private boolean running = true;

    private Lock unavailableLock = new ReentrantLock();
    private Lock getFreeLock = new ReentrantLock();

    public boolean SetUnavailable(Exception exception) {

        boolean isseted = false;

        if (exception != null && UnavailableException == null) {
            unavailableLock.lock();
            try {
                if (UnavailableException == null) {

                    UnavailableException = exception;
                    UnavailableTime = new Date();
                    isseted = true;
                }
            } catch (Exception e) {

            } finally {
                unavailableLock.unlock();
            }
        }

        if (isseted) {

            Policy.OnUnavailable();
            CheckAvailable(Policy.getCheckAvailableInterval());
        }

        return isseted;
    }

    /*
     * 后台定时检查可用性
     */
    private void CheckAvailable(int interval) {
        new Thread(() ->
        {

            if (UnavailableException != null) {
                System.out.println(Policy.getName() + "恢复检查时间：" + interval / 1000 + "秒后");
            }

            while (UnavailableException != null) {

                if (running == false) return;
                try {

                    var conn = getFree(false);
                    if (conn == null) {
                        try {
                            throw new Exception("CheckAvailable 无法获得资源");
                        } catch (Exception exception) {
                            exception.printStackTrace();
                        }
                    }

                    try {
                        if (Policy.OnCheckAvailable(conn) == false)
                            throw new Exception("CheckAvailable 应抛出异常，代表仍然不可用。");
                        break;
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        Return(conn);
                    }
                } catch (Exception ex) {
                    System.out.println(Policy.getName() + "仍然不可用，下一次恢复检查时间：" + interval / 1000 + "秒后，错误：" + ex.toString());
                }
            }
            RestoreToAvailable();

        }).start();
    }

    private void RestoreToAvailable() {
        boolean isRestored = false;
        if (UnavailableException != null) {
            unavailableLock.lock();
            try {
                if (UnavailableException == null) {
                    UnavailableException = null;
                    UnavailableTime = null;
                    isRestored = true;
                }
            } catch (Exception e) {

            } finally {
                unavailableLock.unlock();
            }

        }

        if (isRestored) {

            Lock lock = new ReentrantLock();
            try {
                lock.lock();
                _allObjects.forEach(a -> a.LastGetTime = a.LastReturnTime = new Date(2000, 1, 1));
            } finally {
                lock.unlock();
            }

            Policy.OnAvailable();

            System.out.println(Policy.getName() + "已恢复工作");
        }
    }

    protected boolean LiveCheckAvailable() {

        try {

            var conn = getFree(false);
            if (conn == null) {
                try {
                    throw new Exception("LiveCheckAvailable 无法获得资源，{this.Statistics}");
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            }

            try {
                if (Policy.OnCheckAvailable(conn) == false)
                    throw new Exception("LiveCheckAvailable 应抛出异常，代表仍然不可用。");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Return(conn);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        RestoreToAvailable();

        return true;
    }

    /**
     * 对象池统计
     *
     * @return 统计字符串
     */
    public String Statistics() {
        return "Pool: " + _freeObjects.stream().count() + "/" + _allObjects.stream().count() + ", Get wait: " + _getSyncQueue.stream().count();
    }


    /**
     * 详细统计
     *
     * @return
     */
    public String StatisticsFully() {
        var dateFormat = new SimpleDateFormat("yyyy-MM-dd :hh:mm:ss");
        var sb = new StringBuilder();
        sb.append(Statistics() + " |");
        sb.append(" ");
        for (ObjectWarp<T> o : _allObjects) {
            sb.append(o.Value)
                    .append(", Times: ")
                    .append(o.GetTimes)
                    .append(", ThreadId(R/G):")
                    .append(o.LastReturnThreadId + "/" + o.LastGetThreadId)
                    .append(", Time(R/G): ").append(dateFormat.format(o.LastReturnTime))
                    .append(", Time(R/G): ").append(dateFormat.format(o.LastGetTime));
        }

        return sb.toString();
    }

    /**
     * 创建对象池
     *
     * @param poolSize     对象池的大小
     * @param createObject 创建对象的动作
     */
    public ObjectPool(int poolSize, Func1<T> createObject) {
        this(poolSize, createObject, null);
    }

    /**
     * 创建对象池
     *
     * @param poolSize     对象池的大小
     * @param createObject 创建对象的动作
     * @param onGetObject  获取对象时候触发的动作
     */
    public ObjectPool(int poolSize, Func1<T> createObject, Action1<ObjectWarp<T>> onGetObject) {
        DefaultPolicy<T> p = new DefaultPolicy<T>();
        p.PoolSize = poolSize;
        p.CreateObject = createObject;
        p.OnGetObject = onGetObject;
        Policy = p;
    }

    /**
     * 获取可用资源，或创建资源
     */
    private ObjectWarp<T> getFree(boolean checkAvailable) {

        if (running == false) {
            try {
                throw new Exception(Policy.Name + "对象池已释放，无法访问。");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (checkAvailable && UnavailableException != null) {
            try {
                throw new Exception("状态不可用，等待后台检查程序恢复方可使用。" + UnavailableException.getClass());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        ObjectWarp obj = null;
        if (_freeObjects.isEmpty() && _allObjects.stream().count() < Policy.getPoolSize()) {
            try {
                getFreeLock.lock();
                if (_allObjects.stream().count() < Policy.getPoolSize()) {
                    long idTemp = _allObjects.stream().count() + 1;
                    obj = new ObjectWarp<T>(this, Long.valueOf(idTemp).intValue());
                    _allObjects.add(obj);
                }
            } catch (Exception e) {
            } finally {
                getFreeLock.unlock();
            }
        }

        if (obj != null)
            obj._isReturned = false;
        long lastReturnTime = 0;
        if (obj != null && obj.LastReturnTime != null)
            lastReturnTime = obj.LastReturnTime.getTime();
        if (obj != null && obj.Value == null ||
                obj != null && Policy.getIdleTimeout() > 0 && (new Date().getTime() - lastReturnTime) > Policy.getIdleTimeout()) {
            try {
                obj.ResetValue();
            } catch (Exception ex) {
                Return(obj);
                ex.printStackTrace();
            }
        }

        return obj;
    }

    /**
     * 获取资源
     */
    public ObjectWarp<T> Get() {
        var obj = getFree(true);

        if (obj == null) {
            var queueItem = new GetSyncQueueInfo();
            _getSyncQueue.add(queueItem);
            _getQueue.add(false);
            var timeout = Policy.getSyncGetTimeout();
            //线程等待
            try {
                if (queueItem.Wait.waitOne(timeout)) {
                    obj = queueItem.ReturnValue;
                }
            } catch (Exception ex) {
            }

            if (obj == null) obj = queueItem.ReturnValue;
            if (obj == null) {
                Lock lock = new ReentrantLock();
                try {
                    lock.lock();
                    queueItem.IsTimeout = (obj = queueItem.ReturnValue) == null;
                } finally {
                    lock.unlock();
                }

            }
            if (obj == null) obj = queueItem.ReturnValue;

            if (obj == null) {

                Policy.OnGetTimeout();

                if (Policy.IsThrowGetTimeoutException)
                    try {
                        throw new Exception("SafeObjectPool.Get 获取超时（" + timeout / 1000 + "秒）。");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                return null;
            }
        }

        try {
            Policy.OnGet(obj);
        } catch (Exception ex) {
            Return(obj);
            ex.printStackTrace();
        }
        obj.LastGetThreadId = Thread.currentThread().getId();
        obj.LastGetTime = new Date();
        var atomicLong = new AtomicLong(obj.GetTimes);
        obj.GetTimes = atomicLong.incrementAndGet();

        return obj;
    }

    /**
     * @param timeout 获取超时时间
     * @return 包装对象
     */
    public ObjectWarp Get(long timeout) {
        Policy.setSyncGetTimeout(timeout);
        return Get();
    }

    /**
     * 归还连接池
     */
    public void Return(ObjectWarp<T> obj, boolean isReset) {

        if (obj == null) return;

        if (obj._isReturned) return;

        if (running == false) {

            Policy.OnDestroy(obj.Value);
//            try {
//                (obj.Value as IDisposable)?.Dispose();
//            } catch {
//            }

            return;
        }

        if (isReset) obj.ResetValue();

        boolean isReturn = false;
        while (isReturn == false && !_getQueue.isEmpty()) {
            var isAsync = _getQueue.poll();
            if (isAsync == false) {
                //放行线程
                if (!_getSyncQueue.isEmpty()) {
                    var queueItem = _getSyncQueue.poll();
                    if (queueItem != null) {
                        Lock lock = new ReentrantLock();
                        try {
                            lock.lock();
                            if (queueItem.IsTimeout == false)
                                queueItem.ReturnValue = obj;
                        } finally {
                            lock.unlock();
                        }

                        if (queueItem.ReturnValue != null) {
                            obj.LastReturnThreadId = Thread.currentThread().getId();
                            obj.LastReturnTime = new Date();

                            try {
                                queueItem.Wait.set();
                                isReturn = true;
                            } catch (Exception ex) {
                            }
                        }

                        try {
                            queueItem.dispose();
                        } catch (Exception ex) {
                        }
                    }

                }
            }
        }

        //无排队，直接归还
        if (isReturn == false) {
            try {
                Policy.OnReturn(obj);
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                obj.LastReturnThreadId = Thread.currentThread().getId();
                obj.LastReturnTime = new Date();
                obj._isReturned = true;

                _freeObjects.add(obj);
            }
        }
    }

    /**
     * 归还连接池
     */
    public void Return(ObjectWarp obj) {
        Return(obj, false);
    }

    @Override
    public void close() {

        running = false;

        while (_freeObjects.pop() != null) ;

        while (_getSyncQueue.poll() != null)

            while (_getQueue.poll() != null) ;

        for (var a = 0; a < _allObjects.stream().count(); a++) {
            Policy.OnDestroy(_allObjects.get(a).Value);
//            try {
//                (_allObjects[a].Value as IDisposable)?.Dispose();
//            } catch {
//            }
        }

        _allObjects.clear();
    }

    class GetSyncQueueInfo {

        ManualResetEvent Wait = new ManualResetEvent(true);

        ObjectWarp<T> ReturnValue;

        boolean IsTimeout = false;

        public void dispose() {
            try {
                if (Wait != null)
                    Wait.close();
            } catch (Exception ex) {
            }
        }
    }
}
