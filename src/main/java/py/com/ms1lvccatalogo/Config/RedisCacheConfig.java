package py.com.ms1lvccatalogo.Config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
@EnableConfigurationProperties(RedisCacheConfig.CacheProperties.class)
public class RedisCacheConfig {

    @ConfigurationProperties(prefix = "app.cache")
    public record CacheProperties(String prefix,
                                  Duration defaultTtl,
                                  Map<String, Duration> domains) {
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory,
                                          CacheProperties props) {

        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("py.com")
                .allowIfSubType("java.util")
                .allowIfSubType("java.time")
                .build();

        GenericJacksonJsonRedisSerializer serializer =
                GenericJacksonJsonRedisSerializer.builder()
                        .enableDefaultTyping(ptv)
                        .build();

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(props.defaultTtl())
                .disableCachingNullValues()
                .computePrefixWith(name -> props.prefix() + ":" + name + "::")
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(serializer));

        Map<String, RedisCacheConfiguration> configs = new HashMap<>();
        props.domains().forEach((domain, ttl) -> configs.put(domain, base.entryTtl(ttl)));

        return RedisCacheManager.builder(
                        RedisCacheWriter.nonLockingRedisCacheWriter(
                                redisConnectionFactory, BatchStrategies.scan(1000)))
                .cacheDefaults(base)
                .withInitialCacheConfigurations(configs)
                .enableStatistics()
                .build();
    }
}