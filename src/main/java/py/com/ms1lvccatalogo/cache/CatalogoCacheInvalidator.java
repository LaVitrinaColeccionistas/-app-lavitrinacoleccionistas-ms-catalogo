package py.com.ms1lvccatalogo.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import py.com.ms1lvccatalogo.config.RedisCacheConfig;
import py.com.ms1lvccatalogo.repository.IDetallePublicacionRepository;

import java.util.Collection;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogoCacheInvalidator {

    private final CacheManager cacheManager;
    private final IDetallePublicacionRepository detalleRepository;

    public void evict(String cacheName, Collection<Long> ids) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null || ids == null || ids.isEmpty()) return;
        ids.forEach(cache::evict);
        log.debug("CACHE EVICT cache={} ids={}", cacheName, ids);
    }

    public void evictOfertas(Collection<Long> ids) {
        evict(RedisCacheConfig.OFERTAS, ids);
    }

    public void evictPublicaciones(Collection<Long> ids) {
        evict(RedisCacheConfig.PUBLICACIONES, ids);
    }

    public void evictPublicacionesDeProductos(Collection<Long> idsProducto) {
        if (idsProducto == null || idsProducto.isEmpty()) return;
        evictPublicaciones(detalleRepository.findIdsPublicacionPorProductos(idsProducto));
    }

    public void evictPublicacionesDeProducto(Long idProducto) {
        evictPublicacionesDeProductos(List.of(idProducto));
    }
}