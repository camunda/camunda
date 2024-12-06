/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.authentication.CamundaUserDetailsService;
import io.camunda.authentication.filters.TenantRequestAttributeFilter;
import io.camunda.authentication.handler.AuthFailureHandler;
import io.camunda.authentication.handler.CustomMethodSecurityExpressionHandler;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.UserServices;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("auth-basic|auth-oidc")
public class WebSecurityConfig {
  public static final String[] UNAUTHENTICATED_PATHS =
      new String[] {
        "/login",
        "/logout",
        // these are handled by the frontend
        "/identity/**",
        // endpoint for failure forwarding
        "/error",
        // all actuator endpoints
        "/actuator/**",
        // endpoints defined in BrokerHealthRoutes
        "/ready",
        "/health",
        "/startup"
      };
  public static final String CSRF_TOKEN_HEADER = "X-CSRF-Token";

  private static final Logger LOG = LoggerFactory.getLogger(WebSecurityConfig.class);

  @Bean
  @ConditionalOnMissingBean(MethodSecurityExpressionHandler.class)
  public MethodSecurityExpressionHandler methodSecurityExpressionHandler(
      final AuthorizationServices authorizationServices) {
    return new CustomMethodSecurityExpressionHandler(authorizationServices);
  }

  @Bean
  @Profile("auth-basic")
  public CamundaUserDetailsService camundaUserDetailsService(
      final UserServices userServices,
      final AuthorizationServices authorizationServices,
      final RoleServices roleServices,
      final TenantServices tenantServices) {
    return new CamundaUserDetailsService(
        userServices, authorizationServices, roleServices, tenantServices);
  }

  @Bean
  public SecurityFilterChain securityFilterChain(final HttpSecurity httpSecurity) {
    try {
      return httpSecurity.build();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Bean
  @Primary
  @Profile("auth-oidc")
  public HttpSecurity oidcHttpSecurity(
      final HttpSecurity httpSecurity,
      final AuthFailureHandler authFailureHandler,
      final ClientRegistrationRepository clientRegistrationRepository)
      throws Exception {
    return baseHttpSecurity(httpSecurity, authFailureHandler)
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(
                    jwtConfigurer ->
                        jwtConfigurer.jwkSetUri(
                            clientRegistrationRepository
                                .findByRegistrationId("oidcclient")
                                .getProviderDetails()
                                .getJwkSetUri())))
        .oauth2Login(oauthLoginConfigurer -> {})
        .oidcLogout(httpSecurityOidcLogoutConfigurer -> {})
        .logout((logout) -> logout.logoutSuccessUrl("/"));
  }

  @Bean
  @Primary
  @Profile("auth-basic")
  public HttpSecurity localHttpSecurity(
      final HttpSecurity httpSecurity, final AuthFailureHandler authFailureHandler)
      throws Exception {
    LOG.info("Configuring basic auth login");
    final var cookieCsrfTokenRepository = new CookieCsrfTokenRepository();
    cookieCsrfTokenRepository.setHeaderName(CSRF_TOKEN_HEADER);
    cookieCsrfTokenRepository.setCookieCustomizer(
        responseCookieBuilder -> responseCookieBuilder.httpOnly(false));
    return baseHttpSecurity(httpSecurity, authFailureHandler)
        // .httpBasic(withDefaults())
        .formLogin(
            formLogin ->
                formLogin
                    .loginProcessingUrl("/login")
                    .failureHandler(authFailureHandler)
                    .successHandler(this::genericSuccessHandler))
        .logout(
            (logout) ->
                logout.logoutUrl("/logout").logoutSuccessHandler(this::genericSuccessHandler))
        .exceptionHandling(
            exceptionHandling ->
                exceptionHandling
                    .authenticationEntryPoint(authFailureHandler)
                    .accessDeniedHandler(authFailureHandler))
        .addFilterAfter(
            new OncePerRequestFilter() {
              @Override
              protected void doFilterInternal(
                  final HttpServletRequest request,
                  final HttpServletResponse response,
                  final FilterChain filterChain)
                  throws ServletException, IOException {
                final var existingToken = cookieCsrfTokenRepository.loadToken(request);
                if (existingToken == null) {
                  final var token = cookieCsrfTokenRepository.generateToken(request);
                  cookieCsrfTokenRepository.saveToken(token, request, response);
                }
                filterChain.doFilter(request, response);
              }
            },
            CsrfFilter.class)
        .csrf(
            csrfConfigurer ->
                csrfConfigurer
                    .csrfTokenRepository(cookieCsrfTokenRepository)
                    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()));
  }

  private void genericSuccessHandler(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final Authentication authentication) {
    response.setStatus(HttpStatus.NO_CONTENT.value());
  }

  private HttpSecurity baseHttpSecurity(
      final HttpSecurity httpSecurity, final AuthFailureHandler authFailureHandler) {
    try {
      return httpSecurity
          .authorizeHttpRequests(
              (authorizeHttpRequests) ->
                  authorizeHttpRequests
                      .requestMatchers(UNAUTHENTICATED_PATHS)
                      .permitAll()
                      .anyRequest()
                      .authenticated())
          .headers(
              (headers) ->
                  headers.httpStrictTransportSecurity(
                      (httpStrictTransportSecurity) ->
                          httpStrictTransportSecurity
                              .includeSubDomains(true)
                              .maxAgeInSeconds(63072000)
                              .preload(true)))
          .exceptionHandling(
              (exceptionHandling) -> exceptionHandling.accessDeniedHandler(authFailureHandler))
          .csrf(AbstractHttpConfigurer::disable)
          .cors(AbstractHttpConfigurer::disable)
          .formLogin(AbstractHttpConfigurer::disable)
          .anonymous(AbstractHttpConfigurer::disable);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Bean
  public FilterRegistrationBean<TenantRequestAttributeFilter>
      tenantRequestAttributeFilterRegistration(final MultiTenancyConfiguration configuration) {
    return new FilterRegistrationBean<>(new TenantRequestAttributeFilter(configuration));
  }

  final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(
        final HttpServletRequest request,
        final HttpServletResponse response,
        final Supplier<CsrfToken> csrfToken) {
      xor.handle(request, response, csrfToken);
      csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(
        final HttpServletRequest request, final CsrfToken csrfToken) {
      final String headerValue = request.getHeader(csrfToken.getHeaderName());
      return (StringUtils.hasText(headerValue) ? plain : xor)
          .resolveCsrfTokenValue(request, csrfToken);
    }
  }
}
