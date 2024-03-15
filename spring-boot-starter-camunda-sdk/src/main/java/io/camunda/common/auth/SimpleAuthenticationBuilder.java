/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.common.auth;

import io.camunda.common.auth.Authentication.AuthenticationBuilder;

public class SimpleAuthenticationBuilder implements AuthenticationBuilder {
  private String simpleUrl;
  private SimpleConfig simpleConfig;

  public SimpleAuthenticationBuilder withSimpleUrl(final String simpleUrl) {
    this.simpleUrl = simpleUrl;
    return this;
  }

  public SimpleAuthenticationBuilder withSimpleConfig(final SimpleConfig simpleConfig) {
    this.simpleConfig = simpleConfig;
    return this;
  }

  @Override
  public Authentication build() {
    return new SimpleAuthentication(simpleUrl, simpleConfig);
  }
}
