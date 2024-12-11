/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.authentication.CamundaUserDetailsService;
import io.camunda.authentication.csrf.SpaCsrfTokenRequestHandler;
import io.camunda.authentication.filters.TenantRequestAttributeFilter;
import io.camunda.authentication.handler.AuthFailureHandler;
import io.camunda.authentication.handler.CustomMethodSecurityExpressionHandler;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.UserServices;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
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
        "/tasklist/**",
        "/operate/**",
        // license information is displayed on the login form
        "/v2/license",
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
  public static final String SESSION_COOKIE_NAME = "camunda-session";

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
        userServices,
        authorizationServices,
        roleServices,
        tenantServices,
        true);
  }

  @Bean
  @Primary
  @Profile("auth-oidc")
  public SecurityFilterChain oidcSecurityFilterChain(
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
        .logout((logout) -> logout.logoutSuccessUrl("/"))
        .build();
  }

  @Bean
  @Profile("auth-basic")
  @Order(1)
  public SecurityFilterChain basicAuthSecurityFilterChain(
      final HttpSecurity httpSecurity,
      final AuthFailureHandler authFailureHandler,
      final SecurityConfiguration securityConfiguration)
      throws Exception {
    final boolean isEnabled = securityConfiguration.getBasicAuth().isHttpBasicAuthEnabled();
    LOG.info("HTTP Basic authentication is {}", isEnabled ? "enabled" : "disabled");
    if (!isEnabled) {
      return null;
    }
    return baseHttpSecurity(
            httpSecurity.securityMatchers(
                matchers -> matchers.requestMatchers(this::isBasicAuthRequest)),
            authFailureHandler)
        .httpBasic(Customizer.withDefaults())
        .build();
  }

  @Bean
  @Profile("auth-basic")
  @Order(2)
  public SecurityFilterChain unauthenticatedSecurityFilterChain(
      final HttpSecurity httpSecurity,
      final AuthFailureHandler authFailureHandler,
      final SecurityConfiguration securityConfiguration)
      throws Exception {
    if (securityConfiguration.isEnabled()) {
      return null;
    }
    LOG.warn("Authentication is disabled");
    final RequestMatcher requestMatcher = request -> !hasSessionCookie(request);

    return httpSecurity
        .securityMatchers(matchers -> matchers.requestMatchers(requestMatcher))
        .authorizeHttpRequests(requests -> requests.requestMatchers(requestMatcher).permitAll())
        .csrf(CsrfConfigurer::disable)
        .build();
  }

  @Bean
  @Profile("auth-basic")
  @Order(3)
  public SecurityFilterChain loginAuthSecurityFilterChain(
      final HttpSecurity httpSecurity, final AuthFailureHandler authFailureHandler)
      throws Exception {
    LOG.info("Configuring login auth");
    final var cookieCsrfTokenRepository = new CookieCsrfTokenRepository();
    cookieCsrfTokenRepository.setHeaderName(CSRF_TOKEN_HEADER);
    cookieCsrfTokenRepository.setCookieCustomizer(
        responseCookieBuilder -> responseCookieBuilder.httpOnly(false));
    return baseHttpSecurity(httpSecurity, authFailureHandler)
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
        .csrf(
            csrfConfigurer ->
                csrfConfigurer
                    .csrfTokenRepository(cookieCsrfTokenRepository)
                    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
        .addFilterAfter(new CsrfTokenCookieFilter(cookieCsrfTokenRepository), CsrfFilter.class)
        .build();
  }

  @Bean
  public CookieSerializer cookieSerializer() {
    final DefaultCookieSerializer serializer = new DefaultCookieSerializer();
    serializer.setCookieName(SESSION_COOKIE_NAME);
    serializer.setCookiePath("/");
    serializer.setUseHttpOnlyCookie(true);
    return serializer;
  }

  private void genericSuccessHandler(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final Authentication authentication) {
    response.setStatus(HttpStatus.NO_CONTENT.value());
  }

  private boolean isBasicAuthRequest(final HttpServletRequest request) {
    final String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
    return authorizationHeader != null && authorizationHeader.startsWith("Basic ");
  }

  private boolean isApiRequest(final HttpServletRequest request) {
    final String requestURI = request.getRequestURI();
    return requestURI.startsWith("/v1") || requestURI.startsWith("/v2");
  }

  private boolean hasSessionCookie(final HttpServletRequest request) {
    if (request.getCookies() == null) {
      return false;
    }
    return Arrays.stream(request.getCookies())
        .anyMatch(cookie -> cookie.getName().equals(SESSION_COOKIE_NAME));
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

  private static class CsrfTokenCookieFilter extends OncePerRequestFilter {

    private final CookieCsrfTokenRepository tokenRepository;

    public CsrfTokenCookieFilter(final CookieCsrfTokenRepository tokenRepository) {
      this.tokenRepository = tokenRepository;
    }

    @Override
    protected void doFilterInternal(
        final HttpServletRequest request,
        final HttpServletResponse response,
        final FilterChain filterChain)
        throws ServletException, IOException {
      final var existingToken = tokenRepository.loadToken(request);
      if (existingToken == null) {
        final var token = tokenRepository.generateToken(request);
        tokenRepository.saveToken(token, request, response);
      }
      filterChain.doFilter(request, response);
    }
  }
}
