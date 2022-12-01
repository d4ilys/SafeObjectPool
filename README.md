# SafeObjectPool
安全的对象池，应用场景：连接池，资源池等

参考叶老板的C#版本SafeObjectPool，在Java中实现一个功能差不多的对象池

[2881099/SafeObjectPool](https://github.com/2881099/SafeObjectPool)

对象池容器化管理一批对象，重复使用从而提升性能，有序排队申请，使用完后归还资源。

ObjectPool 解决池用尽后，再请求不报错，排队等待机制。
