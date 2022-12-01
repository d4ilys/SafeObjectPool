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

    /// <summary>
    /// 所属对象池
    /// </summary>
    public ObjectPool<T> Pool;

    /// <summary>
    /// 在对象池中的唯一标识
    /// </summary>
    public int Id;
    /// <summary>
    /// 资源对象
    /// </summary>
    public T Value;

    long _getTimes;
    /// <summary>
    /// 被获取的总次数
    /// </summary>
    public long GetTimes = _getTimes;

    /// 最后获取时的时间
    public Date LastGetTime;


    /// <summary>
    /// 最后归还时的时间
    /// </summary>
    public Date LastReturnTime;

    /// <summary>
    /// 创建时间
    /// </summary>
    public Date CreateTime = new Date();


    /// <summary>
    /// 最后获取时的线程id
    /// </summary>
    public long LastGetThreadId;

    /// <summary>
    /// 最后归还时的线程id
    /// </summary>
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

    /// <summary>
    /// 重置 Value 值
    /// </summary>
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
