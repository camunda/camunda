/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

import com.google.common.collect.Sets;
import io.camunda.authentication.CamundaUserDetailsService;
import io.camunda.authentication.ConditionalOnAuthenticationMethod;
import io.camunda.authentication.filters.TenantRequestAttributeFilter;
import io.camunda.authentication.handler.AuthFailureHandler;
import io.camunda.authentication.handler.CustomMethodSecurityExpressionHandler;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
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
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("consolidated-auth")
public class WebSecurityConfig {
  public static final String SESSION_COOKIE = "camunda-session";
  private static final int ORDER_WEBAPP_API_PROTECTION = 1;
  private static final int ORDER_UNPROTECTED_API = 2;
  private static final int ORDER_LOGIN_LOGOUT_HANDLING = 3;
  private static final Logger LOG = LoggerFactory.getLogger(WebSecurityConfig.class);
  private static final String LOGIN_URL = "/login";
  private static final String LOGOUT_URL = "/logout";
  private static final Set<String> API_PATHS = Set.of("/v1/**", "/v2/**");
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

  @Bean
  @ConditionalOnMissingBean(MethodSecurityExpressionHandler.class)
  public MethodSecurityExpressionHandler methodSecurityExpressionHandler(
      final AuthorizationServices authorizationServices) {
    return new CustomMethodSecurityExpressionHandler(authorizationServices);
  }

  @Bean
  @ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
  public CamundaUserDetailsService camundaUserDetailsService(
      final UserServices userServices,
      final AuthorizationServices authorizationServices,
      final RoleServices roleServices,
      final TenantServices tenantServices) {
    return new CamundaUserDetailsService(
        userServices, authorizationServices, roleServices, tenantServices);
  }

  @Bean
  @ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
  public ClientRegistrationRepository clientRegistrationRepository(
      final SecurityConfiguration securityConfiguration) {
    return new InMemoryClientRegistrationRepository(
        OidcClientRegistration.create(securityConfiguration.getAuthentication().getOidc()));
  }

  @Bean
  @ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
  @Order(ORDER_WEBAPP_API_PROTECTION)
  public SecurityFilterChain oidcHttpSecurity(
      final HttpSecurity httpSecurity,
      final AuthFailureHandler authFailureHandler,
      final ClientRegistrationRepository clientRegistrationRepository)
      throws Exception {
    return baseHttpSecurity(httpSecurity, authFailureHandler, UNAUTHENTICATED_PATHS)
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(
                    jwtConfigurer ->
                        jwtConfigurer.jwkSetUri(
                            clientRegistrationRepository
                                .findByRegistrationId(OidcClientRegistration.REGISTRATION_ID)
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
  @ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
  @Order(ORDER_WEBAPP_API_PROTECTION)
  public SecurityFilterChain httpBasicAuthSecurityFilterChain(
      final HttpSecurity httpSecurity,
      final AuthFailureHandler authFailureHandler,
      final SecurityConfiguration securityConfiguration)
      throws Exception {
    LOG.info("HTTP Basic authentication is enabled");
    return baseHttpSecurity(
            withSecurityMatcher(
                httpSecurity,
                request ->
                    isBasicAuthRequest(request)
                        // If authentication isn't disabled for the API, all API requests without a
                        // session cookie or Referer header are treated as basic auth requests to
                        // work around a limitation of Java's HTTP client, which only sends an
                        // Authorization header after the server sends a WWW-Authenticate header in
                        // a 401 response.
                        || securityConfiguration.isApiProtected()
                            && isApiRequest(request)
                            && !isFrontendRequest(request)),
            authFailureHandler,
            UNAUTHENTICATED_PATHS)
        .httpBasic(Customizer.withDefaults())
        .build();
  }

  @Bean
  @Order(ORDER_UNPROTECTED_API)
  public SecurityFilterChain unprotectedApiAccessSecurityFilterChain(
      final HttpSecurity httpSecurity,
      final AuthFailureHandler authFailureHandler,
      final SecurityConfiguration securityConfiguration)
      throws Exception {
    if (securityConfiguration.isApiProtected()) {
      return null;
    }

    LOG.warn(
        "The API is accessible without authentication. Please disable {} for any deployment.",
        AuthenticationProperties.API_UNPROTECTED);
    return baseHttpSecurity(
            withSecurityMatcher(
                httpSecurity, request -> isApiRequest(request) && !hasSessionCookie(request)),
            authFailureHandler,
            Sets.union(UNAUTHENTICATED_PATHS, API_PATHS))
        .build();
  }

  @Bean
  @ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
  @Order(ORDER_LOGIN_LOGOUT_HANDLING)
  public SecurityFilterChain loginAuthSecurityFilterChain(
      final HttpSecurity httpSecurity, final AuthFailureHandler authFailureHandler)
      throws Exception {
    LOG.info("Configuring login auth");
    return baseHttpSecurity(httpSecurity, authFailureHandler, UNAUTHENTICATED_PATHS)
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
    return authorizationHeader != null && authorizationHeader.startsWith("Basic ");
  }

  private static boolean isFrontendRequest(final HttpServletRequest request) {
    return hasSessionCookie(request) || request.getHeader(HttpHeaders.REFERER) != null;
  }

  private static boolean hasSessionCookie(final HttpServletRequest request) {
    if (request.getCookies() == null) {
      return false;
    }
    return Arrays.stream(request.getCookies())
        .anyMatch(cookie -> cookie.getName().equals(SESSION_COOKIE));
  }

  private static boolean isApiRequest(final HttpServletRequest request) {
    return API_PATHS.stream().anyMatch(path -> antMatcher(path).matches(request));
  }

  private static HttpSecurity withSecurityMatcher(
      final HttpSecurity httpSecurity, final RequestMatcher requestMatcher) {
    return httpSecurity.securityMatchers(matchers -> matchers.requestMatchers(requestMatcher));
  }

  private HttpSecurity baseHttpSecurity(
      final HttpSecurity httpSecurity,
      final AuthFailureHandler authFailureHandler,
      final Set<String> unauthenticatedPaths)
      throws Exception {
    return httpSecurity
        .authorizeHttpRequests(
            (authorizeHttpRequests) ->
                authorizeHttpRequests
                    .requestMatchers(unauthenticatedPaths.toArray(String[]::new))
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
