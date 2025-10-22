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

  public Cache(final String databaseName, final String cacheName) {
    prefix = "camunda.data.secondary-storage.%s.%s-cache".formatted(databaseName, cacheName);
    legacyProperty =
        "zeebe.broker.exporters.camundaexporter.args.%sCache.maxCacheSize".formatted(cacheName);
  }

  public int getMaxSize() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
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
