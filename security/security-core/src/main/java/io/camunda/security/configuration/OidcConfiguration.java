/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

public class OidcConfiguration {

  private static final boolean DEFAULT_OIDC_ENABLED = false;
  private static final String DEFAULT_USERNAME_CLAIM = "username";

  private boolean enabled = DEFAULT_OIDC_ENABLED;
  private String username = DEFAULT_USERNAME_CLAIM;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(final String username) {
    this.username = username;
  }
}
