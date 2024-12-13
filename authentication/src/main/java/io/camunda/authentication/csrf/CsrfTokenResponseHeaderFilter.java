/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.csrf;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * This filter echos the CSRF token value provided by the client in a response header to make it
 * readable for the client. When the token is refreshed it's the responsibility of the token
 * repository to update the header {@see ResponseHeaderCsrfTokenRepository}.
 */
public class CsrfTokenResponseHeaderFilter extends OncePerRequestFilter {
  private final CsrfTokenRepository tokenRepository;
  private final String headerName;

  public CsrfTokenResponseHeaderFilter(
      final CsrfTokenRepository tokenRepository, final String headerName) {
    this.tokenRepository = tokenRepository;
    this.headerName = headerName;
  }

  @Override
  public void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws IOException, ServletException {
    final var token = tokenRepository.loadToken(request);
    if (token != null) {
      response.setHeader(headerName, token.getToken());
    }
    filterChain.doFilter(request, response);
  }
}
