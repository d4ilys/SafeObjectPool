package Internal;

import cn.hutool.core.date.StopWatch;
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


/// <summary>
/// 对象池管理类
/// </summary>
/// <typeparam name="T">对象类型</typeparam>
public class ObjectPool<T> implements AutoCloseable {
    public DefaultPolicy<T> Policy;
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
            CheckAvailable(Policy.CheckAvailableInterval);
        }

        return isseted;
    }

    /// <summary>
    /// 后台定时检查可用性
    /// </summary>
    /// <param name="interval"></param>
    private void CheckAvailable(int interval) {
        new Thread(() ->
        {

            if (UnavailableException != null) {
                System.out.println(Policy.Name + "恢复检查时间：" + interval / 1000 + "秒后");
            }

            while (UnavailableException != null) {

                if (running == false) return;
                try {

                    var conn = getFree(false);
                    if (conn == null) throw new Exception("CheckAvailable 无法获得资源");

                    try {

                        if (Policy.OnCheckAvailable(conn) == false)
                            throw new Exception("CheckAvailable 应抛出异常，代表仍然不可用。");
                        break;

                    } finally {

                        Return(conn);
                    }

                } catch (Exception ex) {
                    System.out.println(Policy.Name + "仍然不可用，下一次恢复检查时间：" + interval / 1000 + "秒后，错误：" + ex.toString());
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

            System.out.println(Policy.Name + "已恢复工作");
        }
    }

    protected boolean LiveCheckAvailable() throws Exception {

        try {

            var conn = getFree(false);
            if (conn == null) throw new Exception("LiveCheckAvailable 无法获得资源，{this.Statistics}");

            try {

                if (Policy.OnCheckAvailable(conn) == false)
                    throw new Exception("LiveCheckAvailable 应抛出异常，代表仍然不可用。");

            } finally {

                Return(conn);
            }

        } catch (Exception e) {
            return false;
        }

        RestoreToAvailable();

        return true;
    }

    public String Statistics() {
        return "Pool: " + _freeObjects.stream().count() + "/" + _allObjects.stream().count() + ", Get wait: " + _getSyncQueue.stream().count();
    }

    ;

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

    public ObjectPool(int poolSize, Func1<T> createObject) {
        this(poolSize, createObject, null);
    }

    /// <summary>
    /// 创建对象池
    /// </summary>
    /// <param name="policy">策略</param>
    public ObjectPool(int poolSize, Func1<T> createObject, Action1<ObjectWarp<T>> onGetObject) {
        var p = new DefaultPolicy<T>();
        p.PoolSize = poolSize;
        p.CreateObject = createObject;
        p.OnGetObject = onGetObject;
        Policy = p;
    }

    /// <summary>
    /// 获取可用资源，或创建资源
    /// </summary>
    /// <returns></returns>
    private ObjectWarp<T> getFree(boolean checkAvailable) throws Exception {

        if (running == false)
            throw new Exception(Policy.Name + "对象池已释放，无法访问。");

        if (checkAvailable && UnavailableException != null)
            throw new Exception("状态不可用，等待后台检查程序恢复方可使用。" + UnavailableException.getClass());

        ObjectWarp obj = null;
        if (_freeObjects.isEmpty() && _allObjects.stream().count() < Policy.PoolSize) {
            try {
                getFreeLock.lock();
                if (_allObjects.stream().count() < Policy.PoolSize) {
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
                obj != null && Policy.IdleTimeout > 0 && (new Date().getTime() - lastReturnTime) > Policy.IdleTimeout) {
            try {
                obj.ResetValue();
            } catch (Exception ex) {
                Return(obj);
                throw new Exception(ex);
            }
        }

        return obj;
    }

    public ObjectWarp<T> Get() throws Exception {
        var obj = getFree(true);

        if (obj == null) {
            var queueItem = new GetSyncQueueInfo();
            _getSyncQueue.add(queueItem);
            _getQueue.add(false);
            var timeout = Policy.SyncGetTimeout;
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
                    throw new Exception("SafeObjectPool.Get 获取超时（" + timeout / 1000 + "秒）。");

                return null;
            }
        }

        try {
            Policy.OnGet(obj);
        } catch (Exception ex) {
            Return(obj);
            throw new Exception(ex);
        }
        obj.LastGetThreadId = Thread.currentThread().getId();
        obj.LastGetTime = new Date();
        var atomicLong = new AtomicLong(obj.GetTimes);
        obj.GetTimes = atomicLong.incrementAndGet();

        return obj;
    }

    public void Return(ObjectWarp<T> obj) throws Exception {
        Return(obj, false);
    }

    public void Return(ObjectWarp<T> obj, boolean isReset) throws Exception {

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
                throw new Exception(ex);
            } finally {
                obj.LastReturnThreadId = Thread.currentThread().getId();
                obj.LastReturnTime = new Date();
                obj._isReturned = true;

                _freeObjects.add(obj);
            }
        }
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
