package Internal;

import java.text.SimpleDateFormat;
import java.util.Date;

public class ObjectWarp<T> {

    public ObjectWarp(ObjectPool<T> pool, int id, T value) {
        Pool = pool;
        Id = id;
        Value = value;
    }

    public ObjectWarp(ObjectPool<T> pool, int id) {
        Pool = pool;
        Id = id;
    }

    //所属对象池
    public ObjectPool<T> Pool;

    //在对象池中的唯一标识
    public int Id;
    //资源对象
    public T Value;

    long _getTimes;
    //被获取的总次数
    public long GetTimes = _getTimes;

    //最后获取时的时间
    public Date LastGetTime;

    //最后归还时的时间
    public Date LastReturnTime;

    //创建时间
    public Date CreateTime = new Date();

    //最后获取时的线程id
    public long LastGetThreadId;

    //最后归还时的线程id
    public long LastReturnThreadId;

    @Override
    public String toString() {
        var dateFormat = new SimpleDateFormat("yyyy-MM-dd :hh:mm:ss");

        var sb = new StringBuilder();
        var result = sb.append(this.Value)
                .append(",Times:")
                .append(dateFormat.format(this.GetTimes))
                .append("ThreadId(R/G): ")
                .append(this.LastReturnThreadId).append("/").append(this.LastGetThreadId)
                .append("Time(R/G):").append(dateFormat.format(this.LastReturnTime)).append(dateFormat.format(this.LastGetTime)).toString();
        return result;
    }

    /**
     * 重置 Value 值
     */
    public void ResetValue() {
        if (this.Value != null) {
            try {
                this.Pool.Policy.OnDestroy(this.Value);
            } catch (Exception exception) {
            }
            try {
//                (this.Value as IDisposable)?.Dispose();
            } catch (Exception ex) {
            }
        }
        T value = null;
        try {
            value = this.Pool.Policy.OnCreate();
        } catch (Exception ex) {
        }
        this.Value = value;
        this.LastReturnTime = new Date();
    }

    boolean _isReturned = false;
}
