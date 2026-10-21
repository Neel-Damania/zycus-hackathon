package com.zycus.hackathon.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
class CorsConfig implements WebMvcConfigurer {

    private final CorsProperties props;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping(props.pathPattern())
                .allowedOrigins(props.allowedOrigins().toArray(String[]::new))
                .allowedMethods(props.allowedMethods().toArray(String[]::new))
                .allowedHeaders(props.allowedHeaders().toArray(String[]::new))
                .allowCredentials(props.allowCredentials())
                .maxAge(props.maxAgeSeconds());
    }
}
