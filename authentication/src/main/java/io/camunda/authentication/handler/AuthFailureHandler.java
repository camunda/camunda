/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.gateway.protocol.model.CamundaProblemDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public final class AuthFailureHandler
    implements AuthenticationFailureHandler, AccessDeniedHandler, AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  @Autowired
  public AuthFailureHandler(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void onAuthenticationFailure(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AuthenticationException error)
      throws IOException {
    handleFailure(request, response, HttpStatus.UNAUTHORIZED, error);
  }

  @Override
  public void handle(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AccessDeniedException error)
      throws IOException {
    // if a token was passed but could not be validated, onAuthenticationFailure is called
    // however, if no token was passed, then access is denied here, and we want to distinguish
    // between unauthorized and forbidden; we can do that by checking the session principal to see
    // if it's authenticated or not
    final var principal = request.getUserPrincipal();
    if (principal instanceof final Authentication auth && auth.isAuthenticated()) {
      handleFailure(request, response, HttpStatus.FORBIDDEN, error);
    }

    handleFailure(request, response, HttpStatus.UNAUTHORIZED, error);
  }

  @Override
  public void commence(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AuthenticationException error)
      throws IOException {
    handleFailure(request, response, HttpStatus.UNAUTHORIZED, error);
  }

  private void handleFailure(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final HttpStatus status,
      final Exception error)
      throws IOException {
    final var problem = CamundaProblemDetail.forStatus(status);
    problem.setDetail(error.getMessage());
    problem.setInstance(URI.create(request.getRequestURI()));

    final var problemDetail = objectMapper.writeValueAsString(problem);

    response.reset();
    response.setStatus(status.value());
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.getWriter().append(problemDetail);
  }
}
