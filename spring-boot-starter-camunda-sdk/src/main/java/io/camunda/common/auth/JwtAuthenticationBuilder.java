/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.common.auth;

import io.camunda.common.auth.Authentication.AuthenticationBuilder;

public abstract class JwtAuthenticationBuilder<T extends JwtAuthenticationBuilder<?>>
    implements AuthenticationBuilder {
  private JwtConfig jwtConfig;

  public final T withJwtConfig(final JwtConfig jwtConfig) {
    this.jwtConfig = jwtConfig;
    return self();
  }

  @Override
  public final Authentication build() {
    return build(jwtConfig);
  }

  protected abstract T self();

  protected abstract Authentication build(JwtConfig jwtConfig);
}
