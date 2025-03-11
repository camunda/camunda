/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security;

import static io.camunda.operate.webapp.security.OperateURIs.*;
import static io.camunda.webapps.util.HttpUtils.REQUESTED_URL;
import static io.camunda.webapps.util.HttpUtils.getRequestedUrl;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import io.camunda.operate.OperateProfileService;
import io.camunda.config.operate.OperateProperties;
import io.camunda.config.operate.WebSecurityProperties;
import jakarta.json.Json;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.logging.LoggersEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

public abstract class BaseWebConfigurer {

  protected final Logger logger = LoggerFactory.getLogger(getClass());

  protected OperateProperties operateProperties;
  OperateProfileService errorMessageService;
  final CookieCsrfTokenRepository cookieCsrfTokenRepository = new CookieCsrfTokenRepository();
  private final WebSecurityProperties webSecurityProperties;

  public BaseWebConfigurer(
      final OperateProperties operateProperties, final OperateProfileService errorMessageService) {
    this.operateProperties = operateProperties;
    this.errorMessageService = errorMessageService;
    webSecurityProperties = operateProperties.getWebSecurity();
  }

  public static void sendJSONErrorMessage(final HttpServletResponse response, final String message)
      throws IOException {
    response.reset();
    response.setCharacterEncoding(RESPONSE_CHARACTER_ENCODING);

    final PrintWriter writer = response.getWriter();
    response.setContentType(APPLICATION_JSON.getMimeType());

    final String jsonResponse =
        Json.createObjectBuilder().add("message", message).build().toString();

    writer.append(jsonResponse);
    response.setStatus(UNAUTHORIZED.value());
  }

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public SecurityFilterChain actuatorFilterChain(final HttpSecurity http) throws Exception {
    http.securityMatchers(
        (matchers) -> {
          matchers
              // all actuator endpoints
              .requestMatchers(EndpointRequest.toAnyEndpoint())
              // allows forwarding the failure when request failed
              // for example when an endpoint could not be found
              .requestMatchers("/error");
        });

    return configureActuatorSecurity(http)
        .authorizeHttpRequests(spec -> spec.anyRequest().permitAll())
        .build();
  }

  private HttpSecurity configureActuatorSecurity(final HttpSecurity http) throws Exception {
    return http.csrf(CsrfConfigurer::disable)
        .cors(CorsConfigurer::disable)
        .logout(LogoutConfigurer::disable)
        .formLogin(FormLoginConfigurer::disable)
        .httpBasic(HttpBasicConfigurer::disable)
        .anonymous(AnonymousConfigurer::disable);
  }

  @Bean
  public SecurityFilterChain filterChain(final HttpSecurity http) throws Exception {

    final var authenticationManagerBuilder =
        http.getSharedObject(AuthenticationManagerBuilder.class);

    applySecurityHeadersSettings(http);
    applySecurityFilterSettings(http);
    applyAuthenticationSettings(authenticationManagerBuilder);
    applyOAuth2Settings(http);

    return http.build();
  }

  protected void applySecurityHeadersSettings(final HttpSecurity http) throws Exception {
    final WebSecurityProperties webSecurityConfig = operateProperties.getWebSecurity();

    final String policyDirectives = getContentSecurityPolicy();

    http.headers(
        headers -> {
          headers
              .contentSecurityPolicy(
                  cps -> {
                    cps.policyDirectives(policyDirectives);
                  })
              .httpStrictTransportSecurity(
                  sts -> {
                    sts.maxAgeInSeconds(
                            webSecurityConfig.getHttpStrictTransportSecurityMaxAgeInSeconds())
                        .includeSubDomains(
                            webSecurityConfig.getHttpStrictTransportSecurityIncludeSubDomains());
                  });
        });
  }

  protected String getContentSecurityPolicy() {
    if (operateProperties.getCloud().getClusterId() == null) {
      return (webSecurityProperties.getContentSecurityPolicy() == null)
          ? webSecurityProperties.DEFAULT_SM_SECURITY_POLICY
          : webSecurityProperties.getContentSecurityPolicy();
    }
    return (webSecurityProperties.getContentSecurityPolicy() == null)
        ? webSecurityProperties.DEFAULT_SAAS_SECURITY_POLICY
        : webSecurityProperties.getContentSecurityPolicy();
  }

  protected void applySecurityFilterSettings(final HttpSecurity http) throws Exception {
    defaultFilterSettings(http);
  }

  private void defaultFilterSettings(final HttpSecurity http) throws Exception {
    if (operateProperties.isCsrfPreventionEnabled()) {
      logger.info("CSRF Protection is enabled");
      configureCSRF(http);
    } else {
      http.csrf((csrf) -> csrf.disable());
    }
    http.authorizeRequests(
            (authorize) -> {
              authorize
                  .requestMatchers(AUTH_WHITELIST)
                  .permitAll()
                  .requestMatchers(API, PUBLIC_API)
                  .authenticated();
            })
        .formLogin(
            (login) -> {
              login
                  .loginProcessingUrl(LOGIN_RESOURCE)
                  .successHandler(this::successHandler)
                  .failureHandler(this::failureHandler)
                  .permitAll();
            })
        .logout(
            (logout) -> {
              logout
                  .logoutUrl(LOGOUT_RESOURCE)
                  .logoutSuccessHandler(this::logoutSuccessHandler)
                  .permitAll()
                  .deleteCookies(COOKIE_JSESSIONID, X_CSRF_TOKEN)
                  .clearAuthentication(true)
                  .invalidateHttpSession(true);
            })
        .exceptionHandling(
            (handling) -> {
              handling.authenticationEntryPoint(this::failureHandler);
            });
  }

  protected void applyAuthenticationSettings(final AuthenticationManagerBuilder builder)
      throws Exception {
    // noop
  }

  protected abstract void applyOAuth2Settings(final HttpSecurity http) throws Exception;

  protected void logoutSuccessHandler(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final Authentication authentication) {
    response.setStatus(NO_CONTENT.value());
  }

  protected void failureHandler(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AuthenticationException ex)
      throws IOException {
    final String requestedUrl = getRequestedUrl(request);
    if (requestedUrl.contains("/api/")
        || requestedUrl.contains("/v1/")
        || requestedUrl.contains("/v2/")) {
      sendError(request, response, ex);
    } else {
      storeRequestedUrlAndRedirectToLogin(request, response, requestedUrl);
    }
  }

  private void storeRequestedUrlAndRedirectToLogin(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final String requestedUrl)
      throws IOException {
    logger.warn("Try to access protected resource {}. Save it for later redirect", requestedUrl);
    request.getSession(true).setAttribute(REQUESTED_URL, requestedUrl);
    response.sendRedirect(request.getContextPath() + LOGIN_RESOURCE);
  }

  private void successHandler(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final Authentication authentication) {
    addCSRFTokenWhenAvailable(request, response).setStatus(NO_CONTENT.value());
  }

  protected void configureCSRF(final HttpSecurity http) throws Exception {
    cookieCsrfTokenRepository.setHeaderName(X_CSRF_TOKEN);
    cookieCsrfTokenRepository.setCookieHttpOnly(true);
    cookieCsrfTokenRepository.setCookieName(X_CSRF_TOKEN);
    http.csrf(
            (csrf) ->
                csrf.csrfTokenRepository(cookieCsrfTokenRepository)
                    .requireCsrfProtectionMatcher(new CsrfRequireMatcher())
                    .ignoringRequestMatchers(EndpointRequest.to(LoggersEndpoint.class)))
        .addFilterAfter(getCSRFHeaderFilter(), CsrfFilter.class);
  }

  protected OncePerRequestFilter getCSRFHeaderFilter() {
    return new OncePerRequestFilter() {
      @Override
      protected void doFilterInternal(
          final HttpServletRequest request,
          final HttpServletResponse response,
          final FilterChain filterChain)
          throws ServletException, IOException {
        filterChain.doFilter(request, addCSRFTokenWhenAvailable(request, response));
      }
    };
  }

  protected HttpServletResponse addCSRFTokenWhenAvailable(
      final HttpServletRequest request, final HttpServletResponse response) {
    if (shouldAddCSRF(request)) {
      final CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
      if (token != null) {
        response.setHeader(X_CSRF_TOKEN, token.getToken());
      }
    }
    return response;
  }

  boolean shouldAddCSRF(final HttpServletRequest request) {
    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    final String path = request.getRequestURI();
    final String method = request.getMethod();
    return auth != null
        && auth.isAuthenticated()
        && (path == null || !path.contains("logout"))
        && ("GET".equalsIgnoreCase(method) || (path != null && path.contains("login")));
  }

  protected void sendError(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AuthenticationException ex)
      throws IOException {
    request.getSession().invalidate();
    sendJSONErrorMessage(response, errorMessageService.getMessageByProfileFor(ex));
  }
}
