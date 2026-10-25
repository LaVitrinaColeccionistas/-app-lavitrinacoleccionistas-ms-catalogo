package py.com.ms1lvccatalogo.Config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;

import java.util.concurrent.Callable;

@Slf4j
class LoggingCache implements Cache {

    private final Cache delegado;

    LoggingCache(Cache delegado) {
        this.delegado = delegado;
    }

    @Override
    public String getName() {
        return delegado.getName();
    }

    @Override
    public Object getNativeCache() {
        return delegado.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        ValueWrapper valor = delegado.get(key);
        log.debug("[cache:{}] GET {} -> {}", getName(), key, valor == null ? "MISS" : "HIT");
        return valor;
    }

    @Override
    public <T> T get(Object key, Class<T> tipo) {
        T valor = delegado.get(key, tipo);
        log.debug("[cache:{}] GET {} -> {}", getName(), key, valor == null ? "MISS" : "HIT");
        return valor;
    }

    @Override
    public <T> T get(Object key, Callable<T> cargador) {
        log.debug("[cache:{}] GET {} (con carga si falta)", getName(), key);
        return delegado.get(key, cargador);
    }

    @Override
    public void put(Object key, Object valor) {
        delegado.put(key, valor);
        log.debug("[cache:{}] PUT {}", getName(), key);
    }

    @Override
    public void evict(Object key) {
        delegado.evict(key);
        log.debug("[cache:{}] EVICT {}", getName(), key);
    }

    @Override
    public void clear() {
        delegado.clear();
        log.debug("[cache:{}] CLEAR (dominio completo)", getName());
    }
}