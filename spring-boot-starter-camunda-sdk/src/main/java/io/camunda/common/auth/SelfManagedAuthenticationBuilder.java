/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.common.auth;

import io.camunda.common.auth.identity.IdentityConfig;

public class SelfManagedAuthenticationBuilder
    extends JwtAuthenticationBuilder<SelfManagedAuthenticationBuilder> {
  private IdentityConfig identityConfig;

  public SelfManagedAuthenticationBuilder withIdentityConfig(final IdentityConfig identityConfig) {
    this.identityConfig = identityConfig;
    return this;
  }

  @Override
  protected SelfManagedAuthenticationBuilder self() {
    return this;
  }

  @Override
  protected Authentication build(final JwtConfig jwtConfig) {
    return new SelfManagedAuthentication(jwtConfig, identityConfig);
  }
}
