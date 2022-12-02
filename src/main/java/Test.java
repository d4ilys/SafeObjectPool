import Internal.ObjectPool;
import cn.hutool.core.thread.ThreadUtil;

/**
 * @className: Test
 * @description: TODO 类描述
 * @author: Daily
 * @date: 2022/12/01 11:18
 **/
public class Test {
    public static void main(String[] args) throws Exception {
        var pool = new ObjectPool<Person>(10, () -> new Person(1, "Daily"));
//        for (int i = 0; i < 100; i++) {
//            var p = pool.Get();
//            System.out.println(pool.Statistics());
//
//            pool.Return(p);
//        }

        ThreadUtil.concurrencyTest(1000, () -> {
            var p = pool.Get();
            System.out.println(pool.Statistics() + "," + p.Value.Name);
            pool.Return(p);
        });
    }
}

