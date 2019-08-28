package gov.sag.bucket4jJCache.tests;

import gov.sag.bucket4jJCache.JCacheUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.processor.EntryProcessor;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;

public class CompatibilityTestMain {
    private static final Logger logger = LoggerFactory.getLogger(CompatibilityTestMain.class);

    final Cache<String, Integer> cache;

    public static void main(String[] args) throws InterruptedException, URISyntaxException {
        CacheManager manager = JCacheUtils.getCacheManager();
        Cache<String, Integer> readyCache = JCacheUtils.getCache(manager, "CompatibilityTest", String.class, Integer.class);
        new CompatibilityTestMain(readyCache).test();
    }

    public CompatibilityTestMain(Cache cache) {
        this.cache = cache;
    }

    public void test() throws InterruptedException {
        String key = "42";
        int threads = 4;
        int iterations = 1000;
        cache.put(key, 0);
        CountDownLatch latch = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    for (int j = 0; j < iterations; j++) {
                        EntryProcessor<String, Integer, Void> processor = (EntryProcessor<String, Integer, Void> & Serializable) (mutableEntry, objects) -> {
                            int value = mutableEntry.getValue();
                            mutableEntry.setValue(value + 1);
                            return null;
                        };
                        cache.invoke(key, processor);
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        latch.await();
        int value = cache.get(key);
        if (value == threads * iterations) {
            System.out.println("Implementation which you use is compatible with Bucket4j");
        } else {
            String msg = "Implementation which you use is not compatible with Bucket4j";
            msg += ", " + (threads * iterations - value) + " writes are missed";
            throw new IllegalStateException(msg);
        }
    }
}