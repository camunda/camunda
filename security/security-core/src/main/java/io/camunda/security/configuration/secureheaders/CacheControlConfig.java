/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration.secureheaders;

/**
 * Sets the cache control headers: 'Cache-Control', 'Pragma', and 'Expires'. When enabled, the
 * following headers will be applied:
 *
 * <ul>
 *   <li>Cache-Control: no-cache, no-store, max-age=0, must-revalidate
 *   <li>Pragma: no-cache
 *   <li>Expires: 0
 * </ul>
 *
 * Enabled by default.
 */
public class CacheControlConfig {

  private boolean enabled = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isDisabled() {
    return !enabled;
  }
}
