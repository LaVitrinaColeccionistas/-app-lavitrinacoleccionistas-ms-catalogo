package py.com.ms1lvccatalogo.Config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import py.com.lavitrinacoleccionistas.enums.TipoRol;
import py.com.ms1lvccatalogo.security.JsonAccessDeniedHandler;
import py.com.ms1lvccatalogo.security.JsonAuthEntryPoint;
import py.com.ms1lvccatalogo.security.JwtProps;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProps.class)
public class SecurityConfig {


    @Bean
    SecretKey jwtKey(JwtProps p) {
        return new SecretKeySpec(Base64.getDecoder().decode(p.secret()), "HmacSHA256");
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key, JwtProps p) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(p.issuer()));

        decoder.setJwtValidator(validator);
        return decoder;
    }
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter g = new JwtGrantedAuthoritiesConverter();
        g.setAuthoritiesClaimName("roles");
        g.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter conv = new JwtAuthenticationConverter();
        conv.setJwtGrantedAuthoritiesConverter(g);
        return conv;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    JwtAuthenticationConverter conv,
                                    JsonAuthEntryPoint entry,
                                    JsonAccessDeniedHandler denied) throws Exception {
        http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Seguridad por URL
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/productos/**", "/publicaciones/**").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/productos/**").hasRole(TipoRol.ADMIN.name())
                        .requestMatchers("/productos/**", "/publicaciones/**")
                        .hasAnyRole(TipoRol.ADMIN.name(), TipoRol.VENDEDOR.name())
                        .requestMatchers("/ofertas/**").authenticated()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(conv))
                        .authenticationEntryPoint(entry)
                        .accessDeniedHandler(denied))
                .exceptionHandling(e -> e.authenticationEntryPoint(entry).accessDeniedHandler(denied));
        return http.build();
    }
}