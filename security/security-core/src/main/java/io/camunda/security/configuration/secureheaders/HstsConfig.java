/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration.secureheaders;

/**
 * Enables the 'Strict-Transport-Security' header. The default value will be 'max-age=63072000 ;
 * includeSubDomains ; preload'. Enabled by default, however, as per <a
 * href="https://tools.ietf.org/html/rfc6797#section-7.2">RFC6797, section 7.2</a> will not be
 * included in HTTP requests.
 */
public class HstsConfig {

  // =63072000 seconds or 2 years
  private static final long DEFAULT_MAX_AGE_IN_SECONDS = 60 * 60 * 24 * 365 * 2;

  private boolean enabled = true;

  /** The 'max-age' parameter. Default is 2 years. */
  private long maxAgeInSeconds = DEFAULT_MAX_AGE_IN_SECONDS;

  /** Whether to include the 'includeSubDomains' parameter. */
  private boolean includeSubDomains = true;

  /** Whether to include the 'preload' parameter. */
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
