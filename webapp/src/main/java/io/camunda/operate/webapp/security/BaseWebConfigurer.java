/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.security;

import static io.camunda.operate.webapp.security.OperateURIs.API;
import static io.camunda.operate.webapp.security.OperateURIs.AUTH_WHITELIST;
import static io.camunda.operate.webapp.security.OperateURIs.COOKIE_JSESSIONID;
import static io.camunda.operate.webapp.security.OperateURIs.LOGIN_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.LOGOUT_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.REQUESTED_URL;
import static io.camunda.operate.webapp.security.OperateURIs.RESPONSE_CHARACTER_ENCODING;
import static io.camunda.operate.webapp.security.OperateURIs.X_CSRF_HEADER;
import static io.camunda.operate.webapp.security.OperateURIs.X_CSRF_PARAM;
import static io.camunda.operate.webapp.security.OperateURIs.X_CSRF_TOKEN;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import io.camunda.operate.property.OperateProperties;
import java.io.IOException;
import java.io.PrintWriter;
import javax.json.Json;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.logging.LoggersEndpoint;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

public abstract class BaseWebConfigurer extends WebSecurityConfigurerAdapter {

  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  final CookieCsrfTokenRepository cookieCSRFTokenRepository = new CookieCsrfTokenRepository();

  @Autowired
  protected OperateProperties operateProperties;

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    if (operateProperties.isCsrfPreventionEnabled()) {
      configureCSRF(http);
    } else {
      http.csrf().disable();
    }
    http
        .authorizeRequests()
        .antMatchers(AUTH_WHITELIST).permitAll()
        .antMatchers(API).authenticated()
        .and()
        .formLogin()
        .loginProcessingUrl(LOGIN_RESOURCE)
        .successHandler(this::successHandler)
        .failureHandler(this::failureHandler)
        .permitAll()
        .and()
        .logout()
        .logoutUrl(LOGOUT_RESOURCE)
        .logoutSuccessHandler(this::logoutSuccessHandler)
        .permitAll()
        .deleteCookies(COOKIE_JSESSIONID, X_CSRF_TOKEN)
        .clearAuthentication(true)
        .invalidateHttpSession(true)
        .and()
        .exceptionHandling().authenticationEntryPoint(this::failureHandler);
  }

  protected void logoutSuccessHandler(HttpServletRequest request, HttpServletResponse response,
      Authentication authentication) {
    response.setStatus(NO_CONTENT.value());
  }

  protected void failureHandler(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException ex) throws IOException {
    String requestedUrl = request.getRequestURI();
    if (requestedUrl.contains("api")) {
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
    logger.debug("Try to access protected resource {}. Save it for later redirect", requestedUrl);
    request.getSession().setAttribute(REQUESTED_URL, requestedUrl);
    response.sendRedirect(request.getContextPath() + LOGIN_RESOURCE);
  }

  /**
   * Stores the CSRF Token in HTTP Response Header (for REST Clients) and as Cookie (for JavaScript
   * Browser applications) The CSRF Token is expected to be set in HTTP Request Header from the
   * client. So an attacker can't trick the user to submit unindented data to the server (by a
   * link).
   */
  private void successHandler(HttpServletRequest request, HttpServletResponse response,
      Authentication authentication) {
    addCSRFTokenWhenAvailable(request, response).setStatus(NO_CONTENT.value());
  }

  protected void sendError(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
      throws IOException{
    request.getSession().invalidate();
    response.reset();
    response.setCharacterEncoding(RESPONSE_CHARACTER_ENCODING);

    PrintWriter writer = response.getWriter();
    response.setContentType(APPLICATION_JSON.getMimeType());
    String jsonResponse = Json.createObjectBuilder()
        .add("message", ex.getMessage())
        .build()
        .toString();

    writer.append(jsonResponse);
    response.setStatus(UNAUTHORIZED.value());
  }

  protected void configureCSRF(final HttpSecurity http) throws Exception {
    cookieCSRFTokenRepository.setCookieName(X_CSRF_TOKEN);
    cookieCSRFTokenRepository.setHeaderName(X_CSRF_TOKEN);
    cookieCSRFTokenRepository.setParameterName(X_CSRF_PARAM);
    cookieCSRFTokenRepository.setCookieHttpOnly(false);
    http.csrf()
        .csrfTokenRepository(cookieCSRFTokenRepository)
        .ignoringRequestMatchers(EndpointRequest.to(LoggersEndpoint.class))
        .ignoringAntMatchers(LOGIN_RESOURCE, LOGOUT_RESOURCE)
        .and()
        .addFilterAfter(getCSRFHeaderFilter(), CsrfFilter.class);
  }

  protected OncePerRequestFilter getCSRFHeaderFilter() {
    return new OncePerRequestFilter() {
      @Override
      protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
          FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(request, addCSRFTokenWhenAvailable(request, response));
      }
    };
  }

  protected HttpServletResponse addCSRFTokenWhenAvailable(HttpServletRequest request,
      HttpServletResponse response) {
    if (shouldAddCSRF(request)) {
      CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
      if (token != null) {
        response.setHeader(X_CSRF_HEADER, token.getHeaderName());
        response.setHeader(X_CSRF_PARAM, token.getParameterName());
        response.setHeader(X_CSRF_TOKEN, token.getToken());
      }
    }
    return response;
  }

   boolean shouldAddCSRF(HttpServletRequest request) {
    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String path = request.getRequestURI();
    return auth != null && auth.isAuthenticated() && (path == null || !path.contains("logout"));
  }
}
