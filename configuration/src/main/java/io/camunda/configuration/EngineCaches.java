/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_AUTHORIZATIONS_CACHE_CAPACITY;
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_AUTHORIZATIONS_CACHE_TTL;
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_DRG_CACHE_CAPACITY;
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_FORM_CACHE_CAPACITY;
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_PROCESS_CACHE_CAPACITY;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.time.Duration;
import java.util.Set;

public class EngineCaches {
  private static final String PREFIX = "camunda.processing.engine.caches";

  private static final Set<String> LEGACY_DRG_CACHE_CAPACITY_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.caches.drgCacheCapacity");
  private static final Set<String> LEGACY_FORM_CACHE_CAPACITY_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.caches.formCacheCapacity");
  private static final Set<String> LEGACY_PROCESS_CACHE_CAPACITY_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.caches.processCacheCapacity");
  private static final Set<String> LEGACY_RESOURCE_CACHE_CAPACITY_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.caches.resourceCacheCapacity");
  private static final Set<String> LEGACY_AUTHORIZATIONS_CACHE_CAPACITY_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.caches.authorizationsCacheCapacity");
  private static final Set<String> LEGACY_AUTHORIZATIONS_CACHE_TTL_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.caches.authorizationsCacheTtl");

  /** Configures the maximum number of parsed decision requirements graphs to cache. */
  private int drgCacheCapacity = DEFAULT_DRG_CACHE_CAPACITY;

  /** Configures the maximum number of parsed forms to cache. */
  private int formCacheCapacity = DEFAULT_FORM_CACHE_CAPACITY;

  /** Configures the maximum number of parsed processes to cache. */
  private int processCacheCapacity = DEFAULT_PROCESS_CACHE_CAPACITY;

  /** Configures the maximum number of resources (e.g. RPA scripts) to cache. */
  private int resourceCacheCapacity = DEFAULT_FORM_CACHE_CAPACITY;

  /** Configures the maximum number of authorization decisions to cache. */
  private int authorizationsCacheCapacity = DEFAULT_AUTHORIZATIONS_CACHE_CAPACITY;

  /** Configures how long a cached authorization decision remains valid before it expires. */
  private Duration authorizationsCacheTtl = DEFAULT_AUTHORIZATIONS_CACHE_TTL;

  public int getDrgCacheCapacity() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".drg-cache-capacity",
        drgCacheCapacity,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_DRG_CACHE_CAPACITY_PROPERTIES);
  }

  public void setDrgCacheCapacity(final int drgCacheCapacity) {
    this.drgCacheCapacity = drgCacheCapacity;
  }

  public int getFormCacheCapacity() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".form-cache-capacity",
        formCacheCapacity,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_FORM_CACHE_CAPACITY_PROPERTIES);
  }

  public void setFormCacheCapacity(final int formCacheCapacity) {
    this.formCacheCapacity = formCacheCapacity;
  }

  public int getProcessCacheCapacity() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".process-cache-capacity",
        processCacheCapacity,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_PROCESS_CACHE_CAPACITY_PROPERTIES);
  }

  public void setProcessCacheCapacity(final int processCacheCapacity) {
    this.processCacheCapacity = processCacheCapacity;
  }

  public int getResourceCacheCapacity() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".resource-cache-capacity",
        resourceCacheCapacity,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_RESOURCE_CACHE_CAPACITY_PROPERTIES);
  }

  public void setResourceCacheCapacity(final int resourceCacheCapacity) {
    this.resourceCacheCapacity = resourceCacheCapacity;
  }

  public int getAuthorizationsCacheCapacity() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".authorizations-cache-capacity",
        authorizationsCacheCapacity,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_AUTHORIZATIONS_CACHE_CAPACITY_PROPERTIES);
  }

  public void setAuthorizationsCacheCapacity(final int authorizationsCacheCapacity) {
    this.authorizationsCacheCapacity = authorizationsCacheCapacity;
  }

  public Duration getAuthorizationsCacheTtl() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".authorizations-cache-ttl",
        authorizationsCacheTtl,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_AUTHORIZATIONS_CACHE_TTL_PROPERTIES);
  }

  public void setAuthorizationsCacheTtl(final Duration authorizationsCacheTtl) {
    this.authorizationsCacheTtl = authorizationsCacheTtl;
  }
}
