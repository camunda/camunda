/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.stereotype.Component;

@Profile("identity-auth")
@Component
public final class PreAuthTokenConverter implements AuthenticationConverter {
  private static final NoToken EMPTY_TOKEN = new NoToken();

  @Override
  public Authentication convert(final HttpServletRequest request) {
    return getTokenFromHeader(request).<Authentication>map(PreAuthToken::new).orElse(EMPTY_TOKEN);
  }

  private Optional<String> getTokenFromHeader(final HttpServletRequest request) {
    final var values = request.getHeaders(HttpHeaders.AUTHORIZATION);
    if (values == null) {
      return Optional.empty();
    }

    final var iterator = values.asIterator();
    if (!iterator.hasNext()) {
      return Optional.empty();
    }

    final var auth = iterator.next().strip();
    final var token = auth.replaceFirst("^Bearer ", "");
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
