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
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.UserServices;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
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
  public static final String LOGIN_URL = "/login";
  public static final String LOGOUT_URL = "/logout";

  public static final Set<String> UNAUTHENTICATED_PATHS =
      Set.of(
          // these are redirected on GET and handled by filters on POST.
          LOGIN_URL,
          LOGOUT_URL,
          "/",
          // these are handled by the frontend
          "/identity/**",
          "/tasklist/**",
          "/operate/**",
          // license information is displayed on the login form
          "/v2/license",
          // configuration is used for login
          "/v2/authentication/**",
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
  public static final String SESSION_COOKIE_NAME = "camunda-session";

  private static final Logger LOG = LoggerFactory.getLogger(WebSecurityConfig.class);

  @Bean
  @ConditionalOnMissingBean(MethodSecurityExpressionHandler.class)
  public MethodSecurityExpressionHandler methodSecurityExpressionHandler(
      final AuthorizationServices authorizationServices) {
    return new CustomMethodSecurityExpressionHandler(authorizationServices);
  }

  @Bean
  @Primary
  @Profile("auth-oidc")
  public SecurityFilterChain oidcSecurityFilterChain(
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
                                .findByRegistrationId("oidcclient")
                                .getProviderDetails()
                                .getJwkSetUri())))
        .oauth2Login(oauthLoginConfigurer -> {})
        .oidcLogout(httpSecurityOidcLogoutConfigurer -> {})
        .logout((logout) -> logout.logoutSuccessUrl("/"))
        .build();
  }

  @Bean
  @Profile({"auth-basic"})
  public CamundaUserDetailsService camundaUserDetailsService(
      final UserServices userServices,
      final RoleServices roleServices,
      final TenantServices tenantServices,
      final AuthorizationServices authorizationServices) {
    return new CamundaUserDetailsService(
        userServices, authorizationServices, roleServices, tenantServices, true);
  }

  @Bean
  @Profile({"auth-basic"})
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
    // Require valid credentials when a basic auth header is present.
    return baseHttpSecurity(
            withSecurityMatcher(httpSecurity, this::isBasicAuthRequest),
            authFailureHandler,
            UNAUTHENTICATED_PATHS)
        .httpBasic(Customizer.withDefaults())
        .build();
  }

  @Bean
  @Profile("auth-basic")
  @Order(2)
  public SecurityFilterChain loginAuthSecurityFilterChain(
      final HttpSecurity httpSecurity, final AuthFailureHandler authFailureHandler)
      throws Exception {
    LOG.info("Configuring login auth");
    return baseHttpSecurity(httpSecurity, authFailureHandler, UNAUTHENTICATED_PATHS)
        .formLogin(
            formLogin ->
                formLogin
                    .loginProcessingUrl(LOGIN_URL)
                    .failureHandler(authFailureHandler)
                    .successHandler(
                        (request, response, authentication) -> {
                          response.setStatus(HttpStatus.NO_CONTENT.value());
                        }))
        .logout(
            (logout) ->
                logout
                    .logoutUrl(LOGOUT_URL)
                    .logoutSuccessHandler(this::genericSuccessHandler)
                    .deleteCookies(SESSION_COOKIE_NAME))
        .exceptionHandling(
            exceptionHandling ->
                exceptionHandling
                    .authenticationEntryPoint(authFailureHandler)
                    .accessDeniedHandler(authFailureHandler))
        .build();
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

  private HttpSecurity baseHttpSecurity(
      final HttpSecurity httpSecurity,
      final AuthFailureHandler authFailureHandler,
      final Set<String> unauthenticatedPaths) {
    try {
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
                              .maxAgeInSeconds(Duration.ofDays(2 * 365).toSeconds())
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

  private static HttpSecurity withSecurityMatcher(
      final HttpSecurity httpSecurity, final RequestMatcher requestMatcher) {
    return httpSecurity.securityMatchers(matchers -> matchers.requestMatchers(requestMatcher));
  }
}
