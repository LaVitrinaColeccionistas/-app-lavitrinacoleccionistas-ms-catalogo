package py.com.ms1lvccatalogo.Config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableCaching
@EnableConfigurationProperties(RedisCacheConfig.CacheProperties.class)
public class RedisCacheConfig {

    public static final String PRODUCTOS = "productos";
    public static final String PUBLICACIONES = "publicaciones";
    public static final String OFERTAS = "ofertas";

    @ConfigurationProperties(prefix = "app.cache")
    public record CacheProperties(String prefix,
                                  Duration defaultTtl,
                                  List<String> allowedPackages,
                                  Map<String, Duration> domains) {
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory,
                                     CacheProperties props) {

        BasicPolymorphicTypeValidator.Builder ptv = BasicPolymorphicTypeValidator.builder();
        props.allowedPackages().forEach(ptv::allowIfSubType);

        GenericJacksonJsonRedisSerializer serializer =
                GenericJacksonJsonRedisSerializer.builder()
                        .enableDefaultTyping(ptv.build())
                        .build();

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(props.defaultTtl())
                .disableCachingNullValues()
                .computePrefixWith(name -> props.prefix() + ":" + name + "::")
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(serializer));

        Map<String, RedisCacheConfiguration> configs = new HashMap<>();
        props.domains().forEach((domain, ttl) -> configs.put(domain, base.entryTtl(ttl)));

        RedisCacheManager redis = RedisCacheManager.builder(
                        RedisCacheWriter.nonLockingRedisCacheWriter(
                                redisConnectionFactory, BatchStrategies.scan(1000)))
                .cacheDefaults(base)
                .withInitialCacheConfigurations(configs)
                .enableStatistics()
                .build();
        redis.afterPropertiesSet();

        return new LoggingCacheManager(redis);
    }
}