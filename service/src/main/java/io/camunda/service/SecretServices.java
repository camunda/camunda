/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.service.secret.SimpleSecretStoreRegistry;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Resolves {@code camunda.secrets.<NAME>} references against a backing store, with a short-lived
 * cache to absorb bursts (e.g. a job-batch activation that materializes many references at once).
 *
 * <p>References whose name does not match the expected pattern, or that the store cannot resolve,
 * are silently omitted from the result. No authorization is performed in this PoC.
 */
public final class SecretServices {

  public static final String REFERENCE_PREFIX = "camunda.secrets.";
  private static final Pattern REFERENCE_PATTERN =
      Pattern.compile("^camunda\\.secrets\\.([A-Za-z0-9_]+)$");

  private final SimpleSecretStoreRegistry registry;
  private final Cache<String, String> cache;

  public SecretServices(final SimpleSecretStoreRegistry registry, final Duration cacheTtl) {
    this.registry = registry;
    cache = Caffeine.newBuilder().expireAfterWrite(cacheTtl).build();
  }

  /**
   * Resolves a batch of references. The result contains only successfully resolved entries; missing
   * or malformed references are silently dropped.
   */
  public Map<String, String> resolve(final List<String> references) {
    final var resolved = new HashMap<String, String>();
    if (references == null || references.isEmpty()) {
      return resolved;
    }
    final var store = registry.getDefaultStore();
    for (final var ref : references) {
      if (ref == null) {
        continue;
      }
      final var cached = cache.getIfPresent(ref);
      if (cached != null) {
        resolved.put(ref, cached);
        continue;
      }
      final var matcher = REFERENCE_PATTERN.matcher(ref);
      if (!matcher.matches()) {
        continue;
      }
      final var name = matcher.group(1);
      store
          .resolve(name)
          .ifPresent(
              value -> {
                cache.put(ref, value);
                resolved.put(ref, value);
              });
    }
    return resolved;
  }
}
