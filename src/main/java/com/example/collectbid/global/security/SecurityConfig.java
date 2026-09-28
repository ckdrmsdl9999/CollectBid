package com.example.collectbid.global.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, TokenAuthenticator authenticator) throws Exception {
        return http
                // Authentication is exclusively an explicit Bearer header; cookies are not accepted.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/members", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/api/categories",
                                "/api/products", "/api/products/*", "/api/auctions", "/api/auctions/*",
                                "/api/auctions/*/bids").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/problem+json;charset=UTF-8");
                            response.getWriter().write("{\"status\":401,\"code\":\"UNAUTHORIZED\",\"detail\":\"로그인이 필요합니다.\"}");
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType("application/problem+json;charset=UTF-8");
                            response.getWriter().write("{\"status\":403,\"code\":\"FORBIDDEN\",\"detail\":\"접근 권한이 없습니다.\"}");
                        }))
                .addFilterBefore(new BearerTokenFilter(authenticator), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
