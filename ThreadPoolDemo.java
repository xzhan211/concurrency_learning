import java.time.LocalTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadPoolDemo {
    
    public static final class ExecutorFactories {
        public static ThreadPoolExecutor newBoundedExecutor(
            String poolName,
            int core,
            int max,
            int queueCapacity,
            long keepAlive,
            TimeUnit unit) {
            
            if(core <= 0 || max < core || queueCapacity <= 0) {
                throw new IllegalArgumentException("bad params");
            }

            BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(queueCapacity);

            ThreadFactory tf = new ThreadFactory() {
                private final AtomicInteger idx = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r);
                    t.setName(poolName + "-" + idx.getAndIncrement());
                    t.setDaemon(false);
                    t.setUncaughtExceptionHandler((th, ex) -> System.err.println(ts() + "Uncaught in " + th.getName() + ": " + ex));
                    return t;
                }
            };

            RejectedExecutionHandler reh = new ThreadPoolExecutor.CallerRunsPolicy();

            ThreadPoolExecutor ex = new ThreadPoolExecutor(core, max, keepAlive, unit, queue, tf, reh);
            return ex;
        }
    }

    public static void main(String[] args) throws Exception {
        ThreadPoolExecutor pool = ExecutorFactories.newBoundedExecutor("demo-pool", 2, 4, 4, 10, TimeUnit.SECONDS);

        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("monitor");
            t.setDaemon(true);
            return t;
        });

        monitor.scheduleAtFixedRate(() -> {
            System.out.printf("%s [MON] poolSize=%d active=%d queued=%d completed=%d%n",
                ts(),
                pool.getPoolSize(),
                pool.getActiveCount(),
                pool.getQueue().size(),
                pool.getCompletedTaskCount());
        }, 0, 200, TimeUnit.MILLISECONDS);

        int taskCount = 30;
        System.out.println(ts() + " Start submitting " + taskCount + " tasks on thread=" + Thread.currentThread().getName());
        
        long start = System.currentTimeMillis();

        for(int i=1; i<=taskCount; i++) {
            final int id = i;

            pool.execute(() -> {
                String tn = Thread.currentThread().getName();
                System.out.printf("%s [RUN] task=%02d running on %s%n", ts(), id, tn);
                sleepSilently(500);
                System.out.printf("%s [END] task=%02d finished on %s%n", ts(), id, tn);
            });
        }

        long submitDone = System.currentTimeMillis();
        System.out.println(ts() + " Finished submitting. submitTimeMs=" + (submitDone - start));

        // pool.shutdown();

        sleepSilently(8000);
        pool.shutdown();

        boolean ok = pool.awaitTermination(80, TimeUnit.SECONDS);
        System.out.println(ts() + " pool terminated=" + ok);

        monitor.shutdownNow();
    }

    private static void sleepSilently(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String ts() {
        return LocalTime.now().toString();
    }
}
