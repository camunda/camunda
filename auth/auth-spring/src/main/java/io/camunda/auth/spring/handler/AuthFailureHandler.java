/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

public class AuthFailureHandler
    implements AuthenticationFailureHandler, AccessDeniedHandler, AuthenticationEntryPoint {

  private static final Logger LOG = LoggerFactory.getLogger(AuthFailureHandler.class);
  private final tools.jackson.databind.ObjectMapper objectMapper =
      new tools.jackson.databind.ObjectMapper();

  @Override
  public void onAuthenticationFailure(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AuthenticationException exception)
      throws IOException, ServletException {
    LOG.debug("Authentication failure: {}", exception.getMessage());
    sendErrorResponse(response, HttpStatus.UNAUTHORIZED, exception.getMessage());
  }

  @Override
  public void handle(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AccessDeniedException accessDeniedException)
      throws IOException, ServletException {
    LOG.debug("Access denied: {}", accessDeniedException.getMessage());
    sendErrorResponse(response, HttpStatus.FORBIDDEN, accessDeniedException.getMessage());
  }

  @Override
  public void commence(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AuthenticationException authException)
      throws IOException, ServletException {
    LOG.debug("Authentication required: {}", authException.getMessage());
    sendErrorResponse(response, HttpStatus.UNAUTHORIZED, authException.getMessage());
  }

  private void sendErrorResponse(
      final HttpServletResponse response, final HttpStatus status, final String detail)
      throws IOException {
    final ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), problem);
  }
}
