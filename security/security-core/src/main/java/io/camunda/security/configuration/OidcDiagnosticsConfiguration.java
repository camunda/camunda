/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

/**
 * Optional OIDC redirect diagnostics. When enabled, the auth stack logs the inputs that drive the
 * OIDC redirect computation (forwarded headers, computed external base URL, configured callback
 * path, expected vs. actual {@code redirect_uri}) and warns on the typical redirect-loop
 * signatures. Purely diagnostic and off by default.
 */
public class OidcDiagnosticsConfiguration {

  private boolean enabled = false;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }
}
