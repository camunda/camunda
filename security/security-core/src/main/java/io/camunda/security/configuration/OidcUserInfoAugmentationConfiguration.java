/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import java.time.Duration;

public class OidcUserInfoAugmentationConfiguration {

  public static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(5);
  public static final int DEFAULT_CACHE_MAX_SIZE = 10_000;
  public static final Duration DEFAULT_NEGATIVE_CACHE_TTL = Duration.ofSeconds(5);

  private boolean enabled = false;
  private Duration cacheTtl = DEFAULT_CACHE_TTL;
  private int cacheMaxSize = DEFAULT_CACHE_MAX_SIZE;
  private Duration negativeCacheTtl = DEFAULT_NEGATIVE_CACHE_TTL;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public Duration getCacheTtl() {
    return cacheTtl;
  }

  public void setCacheTtl(final Duration cacheTtl) {
    this.cacheTtl = cacheTtl;
  }

  public int getCacheMaxSize() {
    return cacheMaxSize;
  }

  public void setCacheMaxSize(final int cacheMaxSize) {
    this.cacheMaxSize = cacheMaxSize;
  }

  /**
   * TTL for "degraded" cache entries — entries that hold JWT-only claims because the UserInfo fetch
   * or merge failed (IdP down, non-2xx, parse failure, {@code sub} mismatch). Lower values recover
   * faster once the IdP returns; higher values dampen retry traffic harder during an outage.
   * Default {@link #DEFAULT_NEGATIVE_CACHE_TTL}.
   */
  public Duration getNegativeCacheTtl() {
    return negativeCacheTtl;
  }

  public void setNegativeCacheTtl(final Duration negativeCacheTtl) {
    this.negativeCacheTtl = negativeCacheTtl;
  }
}
