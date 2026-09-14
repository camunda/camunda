/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.time.Duration;
import java.util.Set;
import org.springframework.util.unit.DataSize;

public class Bulk {

  private static final Duration DEFAULT_DELAY = Duration.ofSeconds(1);
  private static final int DEFAULT_SIZE = 5_000;
  private static final DataSize DEFAULT_MEMORY_LIMIT = DataSize.ofMegabytes(20);

  private final String prefix;

  /** Delay before forced flush (in seconds) */
  private Duration delay = DEFAULT_DELAY;

  /** Bulk size before flush */
  private int size = DEFAULT_SIZE;

  /** Bulk memory utilisation before flush (in MB) */
  private DataSize memoryLimit = DEFAULT_MEMORY_LIMIT;

  /**
   * No-arg constructor solely so spring-boot-configuration-processor does not treat this class as
   * constructor-bound — with a single parameterized constructor, the processor derives metadata
   * only from that constructor's parameters and silently ignores every getter/setter below.
   * Deliberately unused and {@code private}: nothing — not even a test — should ever call it;
   * {@link #Bulk(String)} remains the only real construction path (see {@link
   * DocumentBasedSecondaryStorageDatabase}). Do not remove as dead code.
   */
  private Bulk() {
    prefix = null;
  }

  public Bulk(final String databaseName) {
    prefix = "camunda.data.secondary-storage.%s.bulk".formatted(databaseName);
  }

  public Duration getDelay() {
    final var delayInt = Math.toIntExact(delay.toSeconds());
    final var result =
        UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
            prefix + ".delay",
            delayInt,
            Integer.class,
            BackwardsCompatibilityMode.SUPPORTED,
            Set.of("zeebe.broker.exporters.camundaexporter.args.bulk.delay"));
    return Duration.ofSeconds(result);
  }

  public void setDelay(final Duration delay) {
    this.delay = delay;
  }

  public int getSize() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        prefix + ".size",
        size,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of("zeebe.broker.exporters.camundaexporter.args.bulk.size"));
  }

  public void setSize(final int size) {
    this.size = size;
  }

  public DataSize getMemoryLimit() {
    final var memoryLimitInt = Math.toIntExact(memoryLimit.toMegabytes());
    final var result =
        UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
            prefix + ".memory-limit",
            memoryLimitInt,
            Integer.class,
            BackwardsCompatibilityMode.SUPPORTED,
            Set.of("zeebe.broker.exporters.camundaexporter.args.bulk.memoryLimit"));
    return DataSize.ofMegabytes(result);
  }

  public void setMemoryLimit(final DataSize memoryLimit) {
    this.memoryLimit = memoryLimit;
  }
}
