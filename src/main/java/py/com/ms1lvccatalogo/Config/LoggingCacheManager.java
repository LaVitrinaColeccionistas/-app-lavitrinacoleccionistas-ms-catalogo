package py.com.ms1lvccatalogo.Config;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class LoggingCacheManager implements CacheManager {

    private final CacheManager delegado;
    private final Map<String, Cache> caches = new ConcurrentHashMap<>();

    LoggingCacheManager(CacheManager delegado) {
        this.delegado = delegado;
    }

    @Override
    public Cache getCache(String nombre) {
        Cache cache = delegado.getCache(nombre);
        return cache == null ? null : caches.computeIfAbsent(nombre, n -> new LoggingCache(cache));
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegado.getCacheNames();
    }
}