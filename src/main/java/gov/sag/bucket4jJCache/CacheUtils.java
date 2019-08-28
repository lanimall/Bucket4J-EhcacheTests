package gov.sag.bucket4jJCache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cache.Cache;
import javax.cache.CacheException;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * @author Fabien Sanglier
 */
public class CacheUtils {
    public static final String ENV_JCACHE_PROVIDER = "jcache.provider";
    public static final String ENV_CACHE_CONFIGPATH = "ehcache.config.path";
    public static final String ENV_CACHE_NAME = "ehcache.config.cachename";
    public static final String ENV_EHCACHE_RELEASE = "ehcache.major.release";
    public static final String ENV_EHCACHE_RELEASE_DEFAULT = "0";

    private static Logger log = LoggerFactory.getLogger(CacheUtils.class);

    public static Cache getCache(CacheManager manager) {
        String cacheName = "";
        if (null != System.getProperty(ENV_CACHE_NAME)) {
            cacheName = System.getProperty(ENV_CACHE_NAME);
        }

        return getCache(manager, cacheName);
    }

    public static Cache getCache(CacheManager manager, String cacheName) {
        Cache cache = manager.getCache(cacheName);
        if(null == cache)
            throw new IllegalStateException("Cache should not be null at this point...check if you requested the right cacheName");

        return cache;
    }

    public static Cache getCache(CacheManager manager, Class key, Class value) {
        String cacheName = "";
        if (null != System.getProperty(ENV_CACHE_NAME)) {
            cacheName = System.getProperty(ENV_CACHE_NAME);
        }

        return getCache(manager, cacheName, key, value);
    }

    public static Cache getCache(CacheManager manager, String cacheName, Class key, Class value) {
        Cache cache = null;

        //little trick because the ehcache 2x implementation does not like the call with Class key, Class value (throws ClassCastException)
        int ehcacheRelease = Integer.parseInt(System.getProperty(ENV_EHCACHE_RELEASE,ENV_EHCACHE_RELEASE_DEFAULT));
        if(ehcacheRelease == 2)
            cache = getCache(manager, cacheName);
        else
            cache = manager.getCache(cacheName, key, value);

        if(null == cache)
            throw new IllegalStateException("Cache should not be null at this point...check if you requested the right cacheName");

        return cache;
    }

    public static CacheManager getCacheManager() {
        return getCacheManager(null);
    }

    public static CacheManager getCacheManager(String resourcePath) {
        CacheManager cm = null;

        String cachingProviderStr = System.getProperty(ENV_JCACHE_PROVIDER,"");
        CachingProvider cachingProvider = null;
        if(!"".equals(cachingProviderStr))
            cachingProvider = Caching.getCachingProvider(cachingProviderStr);
        else
            cachingProvider = Caching.getCachingProvider();

        String configLocationToLoad = null;
        if (null != resourcePath && !"".equals(resourcePath)) {
            configLocationToLoad = resourcePath;
        } else if (null != System.getProperty(ENV_CACHE_CONFIGPATH)) {
            configLocationToLoad = System.getProperty(ENV_CACHE_CONFIGPATH);
        }

        if (null != configLocationToLoad) {
            URL input = null;
            try {
                if (configLocationToLoad.indexOf("file:") > -1) {
                    input = new File(configLocationToLoad.substring("file:".length())).toURL();
                } else if (configLocationToLoad.indexOf("classpath:") > -1) {
                    input = CacheUtils.class.getClassLoader().getResource(configLocationToLoad.substring("classpath:".length()));
                } else { //default to classpath if no prefix is specified
                    input = CacheUtils.class.getClassLoader().getResource(configLocationToLoad);
                }

                if (input == null) {
                    throw new FileNotFoundException("File at '" + configLocationToLoad + "' not found");
                }

                log.info("Loading Cache manager from " + configLocationToLoad);
                try {
                    cm = cachingProvider.getCacheManager(input.toURI(), CacheUtils.class.getClassLoader());
                } catch (URISyntaxException e) {
                    throw new IllegalArgumentException("Path '" + ENV_CACHE_CONFIGPATH + "' not valid");
                }
            } catch (IOException ioe) {
                throw new CacheException(ioe);
            }
        } else {
            log.info("Loading Cache manager from default classpath");
            cm = cachingProvider.getCacheManager();
        }

        if(null == cm)
            throw new IllegalStateException("Cache Manager should not be null at this point...");

        return cm;
    }
}
