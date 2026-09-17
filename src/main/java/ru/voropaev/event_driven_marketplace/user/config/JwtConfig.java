package ru.voropaev.event_driven_marketplace.user.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.InputStream;

import com.nimbusds.jose.jwk.RSAKey;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
public class JwtConfig {
    @Bean
    public RSAPublicKey rsaPublicKey(
            @Value("${app.security.jwt.public-key}") Resource publicKey
    ) throws Exception {
        try (InputStream inputStream = publicKey.getInputStream()) {
            return RsaKeyConverters.x509().convert(inputStream);
        }
    }

    @Bean
    public RSAPrivateKey rsaPrivateKey(
            @Value("${app.security.jwt.private-key}") Resource privateKey
    ) throws Exception {
        try (InputStream inputStream = privateKey.getInputStream()) {
            return RsaKeyConverters.pkcs8().convert(inputStream);
        }
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        RSAKey jwk = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        var jwkSource = new ImmutableJWKSet<>(new JWKSet(jwk));
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public JwtDecoder jwtDecoder(RSAPublicKey publicKey) {
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }
}
