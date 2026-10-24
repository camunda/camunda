/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import java.util.Map;

/** Reads the issuer of a token from its claims. */
public final class TokenIssuer {

  private static final String ISSUER_CLAIM = "iss";

  private TokenIssuer() {}

  /**
   * @param claims the token's claims, or {@code null}
   * @return the {@code iss} claim as a string, or {@code null} when it is absent. An ID token's
   *     issuer is a {@code URL}, an access token's a {@code String}; both give the same string.
   */
  public static String of(final Map<String, Object> claims) {
    if (claims == null) {
      return null;
    }
    final var issuer = claims.get(ISSUER_CLAIM);
    return issuer != null ? issuer.toString() : null;
  }
}
