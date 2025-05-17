/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration.secureheaders;

public class HstsConfig {

  private static final long DEFAULT_MAX_AGE_IN_SECONDS = 60 * 60 * 24 * 365 * 2; // 2 years

  private boolean enabled = true;
  private long maxAgeInSeconds = DEFAULT_MAX_AGE_IN_SECONDS;
  private boolean includeSubDomains = true;
  private boolean preload = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isDisabled() {
    return !enabled;
  }

  public long getMaxAgeInSeconds() {
    return maxAgeInSeconds;
  }

  public void setMaxAgeInSeconds(final long maxAgeInSeconds) {
    this.maxAgeInSeconds = maxAgeInSeconds;
  }

  public boolean isIncludeSubDomains() {
    return includeSubDomains;
  }

  public void setIncludeSubDomains(final boolean includeSubDomains) {
    this.includeSubDomains = includeSubDomains;
  }

  public boolean isPreload() {
    return preload;
  }

  public void setPreload(final boolean preload) {
    this.preload = preload;
  }
}
