/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.security;

import static io.camunda.operate.webapp.security.OperateURIs.LOGIN_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.LOGOUT_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.X_CSRF_HEADER;
import static io.camunda.operate.webapp.security.OperateURIs.X_CSRF_PARAM;
import static io.camunda.operate.webapp.security.OperateURIs.X_CSRF_TOKEN;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.logging.LoggersEndpoint;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

public interface CSRFProtectable {

  final CookieCsrfTokenRepository cookieCSRFTokenRepository = new CookieCsrfTokenRepository();

  default void configureCSRF(final HttpSecurity http) throws Exception {
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

  default OncePerRequestFilter getCSRFHeaderFilter() {
    return new OncePerRequestFilter() {
      @Override
      protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
          FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(request, addCSRFTokenWhenAvailable(request, response));
      }
    };
  }

  default HttpServletResponse addCSRFTokenWhenAvailable(HttpServletRequest request,
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

  default boolean shouldAddCSRF(HttpServletRequest request) {
    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String path = request.getRequestURI();
    return auth != null && auth.isAuthenticated() && (path == null || !path.contains("logout"));
  }
}
