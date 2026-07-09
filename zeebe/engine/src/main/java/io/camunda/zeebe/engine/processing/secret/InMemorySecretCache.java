/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.secret;

import io.camunda.zeebe.engine.processing.deployment.model.element.SecretReference;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A minimal {@link SecretCache} backed by a {@link ConcurrentHashMap}, with no eviction or expiry.
 * The concurrent map makes it safe to share across all partitions on a broker and rejects {@code
 * null} keys and values. Entries are keyed by physical tenant and reference, so the {@link
 * #withPhysicalTenant} views over the one shared map never collide.
 */
@NullMarked
public final class InMemorySecretCache implements SecretCache {

  private final Map<CacheKey, String> values;
  private final @Nullable String physicalTenantId;

  public InMemorySecretCache() {
    this(new ConcurrentHashMap<>(), null);
  }

  private InMemorySecretCache(
      final Map<CacheKey, String> values, final @Nullable String physicalTenantId) {
    this.values = values;
    this.physicalTenantId = physicalTenantId;
  }

  @Override
  public Optional<String> get(final SecretReference reference) {
    return Optional.ofNullable(values.get(new CacheKey(physicalTenantId, reference)));
  }

  @Override
  public void put(final SecretReference reference, final String value) {
    values.put(new CacheKey(physicalTenantId, reference), value);
  }

  @Override
  public SecretCache withPhysicalTenant(final String physicalTenantId) {
    return new InMemorySecretCache(values, physicalTenantId);
  }

  private record CacheKey(@Nullable String physicalTenantId, SecretReference reference) {}
}
