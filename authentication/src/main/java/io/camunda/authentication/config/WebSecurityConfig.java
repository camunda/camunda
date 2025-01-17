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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Set;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("auth-basic|auth-oidc")
public class WebSecurityConfig {
  public static final String SESSION_COOKIE = "camunda-session";
  private static final String LOGIN_URL = "/login";
  private static final String LOGOUT_URL = "/logout";
  private static final Set<String> UNAUTHENTICATED_PATHS =
      Set.of(
          LOGIN_URL,
          "/",
          // webapps are single page apps
          "/identity/**",
          "/operate/**",
          "/tasklist/**",
          // these v2 endpoints are public
          "/v2/license",
          // endpoint for failure forwarding
          "/error",
          // all actuator endpoints
          "/actuator/**",
          // endpoints defined in BrokerHealthRoutes
          "/ready",
          "/health",
          "/startup",
          // deprecated Tasklist v1 Public Endpoints
          "/v1/external/process/**",
          "/new/**");

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
  @Primary
  @Profile("auth-oidc")
  public SecurityFilterChain oidcHttpSecurity(
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
        .logout(
            (logout) ->
                logout
                    .logoutUrl(LOGOUT_URL)
                    .logoutSuccessHandler(this::genericSuccessHandler)
                    .deleteCookies())
        .build();
  }

  private void genericSuccessHandler(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final Authentication authentication) {
    response.setStatus(HttpStatus.NO_CONTENT.value());
  }

  @Bean
  @Profile("auth-basic")
  @Order(1)
  public SecurityFilterChain httpBasicAuthSecurityFilterChain(
      final HttpSecurity httpSecurity, final AuthFailureHandler authFailureHandler)
      throws Exception {
    LOG.info("HTTP Basic authentication is enabled");
    // Require valid credentials when a basic auth header is present.
    return baseHttpSecurity(
            withSecurityMatcher(httpSecurity, WebSecurityConfig::isBasicAuthRequest),
            authFailureHandler)
        .httpBasic(Customizer.withDefaults())
        .build();
  }

  @Bean
  @Profile("auth-basic")
  @Order(2)
  public SecurityFilterChain loginAuthSecurityFilterChain(
      final HttpSecurity httpSecurity, final AuthFailureHandler authFailureHandler)
      throws Exception {
    return buildLoginAuthSecurityFilterChain(httpSecurity, authFailureHandler);
  }

  public SecurityFilterChain buildLoginAuthSecurityFilterChain(
      final HttpSecurity httpSecurity, final AuthFailureHandler authFailureHandler)
      throws Exception {
    LOG.info("Configuring login auth");
    return baseHttpSecurity(httpSecurity, authFailureHandler)
        .formLogin(
            formLogin ->
                formLogin
                    .loginPage(LOGIN_URL)
                    .loginProcessingUrl(LOGIN_URL)
                    .failureHandler(authFailureHandler)
                    .successHandler(this::genericSuccessHandler))
        .logout(
            (logout) ->
                logout
                    .logoutUrl(LOGOUT_URL)
                    .logoutSuccessHandler(this::genericSuccessHandler)
                    .deleteCookies(SESSION_COOKIE))
        .exceptionHandling(
            exceptionHandling ->
                exceptionHandling
                    .authenticationEntryPoint(authFailureHandler)
                    .accessDeniedHandler(authFailureHandler))
        .build();
  }

  @Bean
  public FilterRegistrationBean<TenantRequestAttributeFilter>
      tenantRequestAttributeFilterRegistration(final MultiTenancyConfiguration configuration) {
    return new FilterRegistrationBean<>(new TenantRequestAttributeFilter(configuration));
  }

  private static boolean isBasicAuthRequest(final HttpServletRequest request) {
    final String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
    // If the client sends an `Authorization: Basic` header, treat this as a basic auth request.
    if (authorizationHeader != null && authorizationHeader.startsWith("Basic ")) {
      return true;
    }
    // If it's an API request and there's no session cookie, also treat the request as a basic auth
    // request. This is a workaround for Java's HTTP client, which only sends an Authorization
    // header after the server sends a WWW-Authenticate header in a 401 response.
    if (isApiRequest(request) && !hasSessionCookie(request)) {
      return true;
    }
    return false;
  }

  private static boolean hasSessionCookie(final HttpServletRequest request) {
    if (request.getCookies() == null) {
      return false;
    }
    return Arrays.stream(request.getCookies())
        .anyMatch(cookie -> cookie.getName().equals(SESSION_COOKIE));
  }

  private static boolean isApiRequest(final HttpServletRequest request) {
    return request.getRequestURI().contains("/v2/") || request.getRequestURI().contains("/api/");
  }

  private static HttpSecurity withSecurityMatcher(
      final HttpSecurity httpSecurity, final RequestMatcher requestMatcher) {
    return httpSecurity.securityMatchers(matchers -> matchers.requestMatchers(requestMatcher));
  }

  private HttpSecurity baseHttpSecurity(
      final HttpSecurity httpSecurity, final AuthFailureHandler authFailureHandler)
      throws Exception {
    return httpSecurity
        .authorizeHttpRequests(
            (authorizeHttpRequests) ->
                authorizeHttpRequests
                    .requestMatchers(UNAUTHENTICATED_PATHS.toArray(String[]::new))
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
  }
}
