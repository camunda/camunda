/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security;

import static io.camunda.operate.webapp.security.OperateURIs.*;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import io.camunda.operate.property.OperateProperties;
import java.io.IOException;
import java.io.PrintWriter;
import jakarta.json.Json;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.camunda.operate.property.WebSecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;

public abstract class BaseWebConfigurer {

  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  @Autowired
  protected OperateProperties operateProperties;

  @Autowired
  OperateProfileService errorMessageService;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    final var authenticationManagerBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);

    applySecurityHeadersSettings(http);
    applySecurityFilterSettings(http);
    applyAuthenticationSettings(authenticationManagerBuilder);
    applyOAuth2Settings(http);

    return http.build();
  }

  protected void applySecurityHeadersSettings(HttpSecurity http) throws Exception {
    final WebSecurityProperties webSecurityConfig = operateProperties.getWebSecurity();
    http.headers()
        .contentSecurityPolicy(webSecurityConfig.getContentSecurityPolicy())
        .and()
        .httpStrictTransportSecurity()
          .maxAgeInSeconds(webSecurityConfig.getHttpStrictTransportSecurityMaxAgeInSeconds())
          .includeSubDomains(webSecurityConfig.getHttpStrictTransportSecurityIncludeSubDomains());
  }

  protected void applySecurityFilterSettings(final HttpSecurity http) throws Exception {
    defaultFilterSettings(http);
  }

  private void defaultFilterSettings(final HttpSecurity http) throws Exception {
    http
      .csrf((csrf) -> csrf.disable())
      .authorizeRequests((authorize) -> {
        authorize
          .requestMatchers(AUTH_WHITELIST).permitAll()
          .requestMatchers(API, PUBLIC_API).authenticated();
      })
      .formLogin((login) -> {
        login
          .loginProcessingUrl(LOGIN_RESOURCE)
          .successHandler(this::successHandler)
          .failureHandler(this::failureHandler)
          .permitAll();
      })
      .logout((logout) -> {
        logout
          .logoutUrl(LOGOUT_RESOURCE)
          .logoutSuccessHandler(this::logoutSuccessHandler)
          .permitAll()
          .deleteCookies(COOKIE_JSESSIONID)
          .clearAuthentication(true)
          .invalidateHttpSession(true);
      })
      .exceptionHandling((handling) -> {
        handling.authenticationEntryPoint(this::failureHandler);
      });
  }

  protected void applyAuthenticationSettings(final AuthenticationManagerBuilder builder) throws Exception {
    // noop
  }

  protected abstract void applyOAuth2Settings(final HttpSecurity http) throws Exception;

  protected void logoutSuccessHandler(HttpServletRequest request, HttpServletResponse response,
      Authentication authentication) {
    response.setStatus(NO_CONTENT.value());
  }

  protected void failureHandler(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException ex) throws IOException {
    String requestedUrl = request.getRequestURI().substring(request.getContextPath().length());
    if (requestedUrl.contains("/api/") || requestedUrl.contains("/v1/")) {
      sendError(request,response, ex);
    } else {
      storeRequestedUrlAndRedirectToLogin(request, response, requestedUrl);
    }
  }

  private void storeRequestedUrlAndRedirectToLogin(final HttpServletRequest request, final HttpServletResponse response,
      String requestedUrl) throws IOException {
    if(request.getQueryString() !=null && !request.getQueryString().isEmpty()) {
      requestedUrl = requestedUrl + "?" + request.getQueryString();
    }
    logger.warn("Try to access protected resource {}. Save it for later redirect", requestedUrl);
    request.getSession(true).setAttribute(REQUESTED_URL, requestedUrl);
    response.sendRedirect(request.getContextPath() + LOGIN_RESOURCE);
  }

  private void successHandler(HttpServletRequest request, HttpServletResponse response,
      Authentication authentication) {
    response.setStatus(NO_CONTENT.value());
  }

  protected void sendError(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
      throws IOException{
    request.getSession().invalidate();
    sendJSONErrorMessage(response, errorMessageService.getMessageByProfileFor(ex));
  }

  public static void sendJSONErrorMessage(final HttpServletResponse response, final String message)
      throws IOException {
    response.reset();
    response.setCharacterEncoding(RESPONSE_CHARACTER_ENCODING);

    PrintWriter writer = response.getWriter();
    response.setContentType(APPLICATION_JSON.getMimeType());

    String jsonResponse = Json.createObjectBuilder()
        .add("message", message)
        .build()
        .toString();

    writer.append(jsonResponse);
    response.setStatus(UNAUTHORIZED.value());
  }

}
