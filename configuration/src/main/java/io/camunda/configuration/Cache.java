/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.util.Set;

public class Cache {

  private static final int DEFAULT_MAX_SIZE = 10_000;

  private final String prefix;
  private final String legacyProperty;

  /** Maximum cache size */
  private int maxSize = DEFAULT_MAX_SIZE;

  /**
   * No-arg constructor solely so spring-boot-configuration-processor does not treat this class as
   * constructor-bound — with a single parameterized constructor, the processor derives metadata
   * only from that constructor's parameters and silently ignores every getter/setter below.
   * Deliberately unused and {@code private}: nothing — not even a test — should ever call it;
   * {@link #Cache(String, String)} remains the only real construction path (see {@link
   * DocumentBasedSecondaryStorageDatabase}). Do not remove as dead code.
   */
  private Cache() {
    prefix = null;
    legacyProperty = null;
  }

  public Cache(final String databaseName, final String cacheName) {
    prefix = "camunda.data.secondary-storage.%s.%s-cache".formatted(databaseName, cacheName);
    legacyProperty =
        "zeebe.broker.exporters.camundaexporter.args.%sCache.maxCacheSize".formatted(cacheName);
  }

  public int getMaxSize() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        prefix + ".max-size",
        maxSize,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyProperty));
  }

  public void setMaxSize(final int maxSize) {
    this.maxSize = maxSize;
  }
}
