/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.source;

import io.camunda.security.api.context.OidcClaimsProvider;
import java.util.Map;
import java.util.Objects;

/**
 * Adds the login source to the claims of a bearer token, resolved through the token's issuer. Any
 * login source already present in the claims is dropped, so a token cannot choose its own.
 */
public final class LoginSourceClaimsProvider implements OidcClaimsProvider {

  private final OidcClaimsProvider delegate;
  private final LoginSources loginSources;

  public LoginSourceClaimsProvider(
      final OidcClaimsProvider delegate, final LoginSources loginSources) {
    this.delegate = delegate;
    this.loginSources = loginSources;
  }

  @Override
  public Map<String, Object> claimsFor(
      final Map<String, Object> jwtClaims, final String tokenValue) {
    final var claims = delegate.claimsFor(jwtClaims, tokenValue);
    final var issuer = Objects.toString(jwtClaims.get("iss"), null);
    return LoginSources.withLoginSource(claims, loginSources.sourceOfIssuer(issuer));
  }
}
