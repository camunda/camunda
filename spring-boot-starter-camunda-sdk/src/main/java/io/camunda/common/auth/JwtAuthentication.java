/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.common.auth;

import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public abstract class JwtAuthentication implements Authentication {

  private final JwtConfig jwtConfig;
  private final Map<Product, JwtToken> tokens = new HashMap<>();

  protected JwtAuthentication(final JwtConfig jwtConfig) {
    this.jwtConfig = jwtConfig;
  }

  @Override
  public final Entry<String, String> getTokenHeader(final Product product) {
    if (!tokens.containsKey(product) || !isValid(tokens.get(product))) {
      final JwtToken newToken = generateToken(product, jwtConfig.getProduct(product));
      tokens.put(product, newToken);
    }
    return authHeader(tokens.get(product).getToken());
  }

  @Override
  public final void resetToken(final Product product) {
    tokens.remove(product);
  }

  protected abstract JwtToken generateToken(Product product, JwtCredential credential);

  private Entry<String, String> authHeader(final String token) {
    return new AbstractMap.SimpleEntry<>("Authorization", "Bearer " + token);
  }

  private boolean isValid(final JwtToken jwtToken) {
    // a token is only counted valid if it is only valid for at least 30 seconds
    return jwtToken.getExpiry().isAfter(LocalDateTime.now().minusSeconds(30));
  }

  protected static class JwtToken {
    private final String token;
    private final LocalDateTime expiry;

    public JwtToken(final String token, final LocalDateTime expiry) {
      this.token = token;
      this.expiry = expiry;
    }

    public String getToken() {
      return token;
    }

    public LocalDateTime getExpiry() {
      return expiry;
    }
  }
}
