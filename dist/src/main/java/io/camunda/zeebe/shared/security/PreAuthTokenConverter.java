/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared.security;

import java.util.Collections;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public final class PreAuthTokenConverter implements ServerAuthenticationConverter {
  private static final NoToken EMPTY_TOKEN = new NoToken();

  @Override
  public Mono<Authentication> convert(final ServerWebExchange exchange) {
    return Mono.just(
        getTokenFromHeader(exchange.getRequest().getHeaders())
            .<Authentication>map(PreAuthToken::new)
            .orElse(EMPTY_TOKEN));
  }

  private Optional<String> getTokenFromHeader(final HttpHeaders headers) {
    final var values = headers.getOrEmpty(HttpHeaders.AUTHORIZATION);
    if (values.isEmpty()) {
      return Optional.empty();
    }

    final var token = values.get(0).strip().replaceFirst("^Bearer ", "");
    return token.isBlank() ? Optional.empty() : Optional.of(token);
  }

  private static final class NoToken extends AbstractAuthenticationToken {
    private final NoCredentials credentials = new NoCredentials();
    private final NoPrincipal principal = new NoPrincipal();

    public NoToken() {
      super(Collections.emptyList());
    }

    @Override
    public Object getCredentials() {
      return credentials;
    }

    @Override
    public Object getPrincipal() {
      return principal;
    }

    @Override
    public String getName() {
      return "NoToken";
    }

    @Override
    public boolean isAuthenticated() {
      return false;
    }

    @Override
    public void setAuthenticated(final boolean isAuthenticated) throws IllegalArgumentException {
      if (isAuthenticated) {
        throw new UnsupportedOperationException(
            "Cannot authenticate an empty token authentication");
      }
    }

    private record NoCredentials() {}

    private record NoPrincipal() {}
  }
}
