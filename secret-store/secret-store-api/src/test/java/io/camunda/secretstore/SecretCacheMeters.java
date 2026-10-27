/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import io.camunda.secretstore.SecretCacheMetricsDoc.SecretCacheEvictionCause;
import io.camunda.secretstore.SecretCacheMetricsDoc.SecretCacheKeyNames;
import io.camunda.secretstore.SecretCacheMetricsDoc.SecretCacheResult;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;

/**
 * Reads {@link SecretCacheMetricsDoc}'s meters back off a registry, so a test asserts on what an
 * operator would see rather than on the counters a cache holds.
 *
 * <p>Every lookup names the {@code store} tag: {@code RequiredSearch} treats a tag as required
 * rather than exhaustive, so a search without it would silently answer with whichever store's meter
 * matched first once a test meters two stores into one registry.
 */
final class SecretCacheMeters {

  private SecretCacheMeters() {}

  static double results(
      final MeterRegistry registry, final String storeId, final SecretCacheResult result) {
    return registry
        .get(SecretCacheMetricsDoc.CACHE_RESULT.getName())
        .tag(SecretCacheKeyNames.STORE.asString(), storeId)
        .tag(SecretCacheKeyNames.RESULT.asString(), result.name())
        .counter()
        .count();
  }

  static double evictions(
      final MeterRegistry registry, final String storeId, final SecretCacheEvictionCause cause) {
    return registry
        .get(SecretCacheMetricsDoc.CACHE_EVICTIONS.getName())
        .tag(SecretCacheKeyNames.STORE.asString(), storeId)
        .tag(SecretCacheKeyNames.CAUSE.asString(), cause.name())
        .counter()
        .count();
  }

  static double size(final MeterRegistry registry, final String storeId) {
    return registry
        .get(SecretCacheMetricsDoc.CACHE_SIZE.getName())
        .tag(SecretCacheKeyNames.STORE.asString(), storeId)
        .gauge()
        .value();
  }

  /**
   * The names of every secret cache meter on the registry, so a test can assert a cache published
   * nothing at all — a meter search would throw instead of reporting an absence.
   */
  static List<String> cacheMeterNames(final MeterRegistry registry) {
    return registry.getMeters().stream()
        .map(meter -> meter.getId().getName())
        .filter(name -> name.startsWith("camunda.secret.cache."))
        .toList();
  }
}
