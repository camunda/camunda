/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.common.auth;

import io.camunda.common.auth.identity.IdentityConfig;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.Tokens;
import java.time.LocalDateTime;

public class SelfManagedAuthentication extends JwtAuthentication {

  private final IdentityConfig identityConfig;

  public SelfManagedAuthentication(final JwtConfig jwtConfig, final IdentityConfig identityConfig) {
    super(jwtConfig);
    this.identityConfig = identityConfig;
  }

  public static SelfManagedAuthenticationBuilder builder() {
    return new SelfManagedAuthenticationBuilder();
  }

  @Override
  protected JwtToken generateToken(final Product product, final JwtCredential credential) {
    final Tokens token = getIdentityToken(product, credential);
    return new JwtToken(
        token.getAccessToken(), LocalDateTime.now().plusSeconds(token.getExpiresIn()));
  }

  private Tokens getIdentityToken(final Product product, final JwtCredential credential) {
    final Identity identity = identityConfig.get(product).getIdentity();
    final String audience = credential.getAudience();
    return identity.authentication().requestToken(audience);
  }
}
