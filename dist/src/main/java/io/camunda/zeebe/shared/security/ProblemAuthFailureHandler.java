/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public final class ProblemAuthFailureHandler
    implements ServerAuthenticationFailureHandler,
        ServerAccessDeniedHandler,
        ServerAuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  @Autowired
  public ProblemAuthFailureHandler(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public Mono<Void> onAuthenticationFailure(
      final WebFilterExchange exchange, final AuthenticationException error) {
    return handleFailure(exchange.getExchange(), HttpStatus.UNAUTHORIZED, error);
  }

  @Override
  public Mono<Void> handle(final ServerWebExchange exchange, final AccessDeniedException error) {
    // if a token was passed but could not be validated, onAuthenticationFailure is called
    // however, if no token was passed, then access is denied here, and we want to distinguish
    // between unauthorized and forbidden; we can do that by checking the session principal to see
    // if it's authenticated or not
    return exchange
        .getPrincipal()
        .flatMap(
            principal -> {
              if (principal instanceof final Authentication auth && auth.isAuthenticated()) {
                return handleFailure(exchange, HttpStatus.FORBIDDEN, error);
              }

              return handleFailure(exchange, HttpStatus.UNAUTHORIZED, error);
            });
  }

  @Override
  public Mono<Void> commence(
      final ServerWebExchange exchange, final AuthenticationException error) {
    return handleFailure(exchange, HttpStatus.UNAUTHORIZED, error);
  }

  private Mono<Void> handleFailure(
      final ServerWebExchange exchange, final HttpStatus status, final Exception error) {
    final var request = exchange.getRequest();
    final var response = exchange.getResponse();
    final var problem = ProblemDetail.forStatus(status);
    problem.setInstance(request.getURI());
    problem.setDetail(error.getMessage());

    response.setStatusCode(status);
    response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

    return response.writeWith(
        Mono.fromCallable(() -> objectMapper.writeValueAsBytes(problem))
            .map(bytes -> response.bufferFactory().wrap(bytes)));
  }
}
