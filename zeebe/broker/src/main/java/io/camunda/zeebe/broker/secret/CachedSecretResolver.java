/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.secret;

import io.camunda.zeebe.engine.processing.job.SecretResolver;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link SecretResolver} backed by an in-memory cache of resolved secret values, keyed by the
 * {@link SecretReference} record. A reference resolves only when the background secret-resolution
 * flow has already stored its value via {@link #put}. The broker shares one instance across
 * partitions.
 *
 * <p>The backing map is a {@link ConcurrentHashMap}, so it is safe to populate and read
 * concurrently and rejects {@code null} keys and values.
 */
public final class CachedSecretResolver implements SecretResolver {

  private final Map<SecretReference, String> secretCache = new ConcurrentHashMap<>();

  /** Stores the resolved value for the reference so subsequent activations can resolve it. */
  public void put(final SecretReference reference, final String value) {
    secretCache.put(reference, value);
  }

  @Override
  public Map<SecretReference, String> resolve(final Set<SecretReference> references) {
    final Map<SecretReference, String> values = HashMap.newHashMap(references.size());
    for (final SecretReference reference : references) {
      final String value = secretCache.get(reference);
      if (value != null) {
        values.put(reference, value);
      }
    }
    return values;
  }
}
