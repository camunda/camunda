/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/**
 * A {@link SecretStore} that serves what its {@link SecretCache} holds and caches what the store
 * answers, so a caller resolves secrets through a single call instead of driving store and cache
 * itself.
 *
 * <p>Only a resolved value is cached: a failure has to stay retryable, or a secret that was briefly
 * unreadable would stay unresolvable until the process restarts. {@link #list()} goes straight to
 * the store, since a cache holds the values read so far rather than the store's set of secrets.
 *
 * <p>Does not close the store it wraps: whoever configured that store owns its lifecycle.
 */
@NullMarked
public final class CachingSecretStore implements SecretStore {

  private final SecretStore store;
  private final SecretCache cache;

  public CachingSecretStore(final SecretStore store, final SecretCache cache) {
    this.store = store;
    this.cache = cache;
  }

  /**
   * Resolves the given names, serving the cached ones and asking the store only for the rest.
   *
   * <p>A name the store answers without a result for is left out of the returned map, exactly as
   * the store left it out, so a caller can still tell an unanswered name apart from a failed one.
   */
  @Override
  public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
    final Map<String, SecretResolutionResult> results = new LinkedHashMap<>();
    final Set<String> misses = new LinkedHashSet<>();
    for (final var name : names) {
      final var cached = cache.get(name).orElse(null);
      if (cached == null) {
        misses.add(name);
      } else {
        results.put(name, new SecretResolutionResult.Resolved(cached));
      }
    }
    if (misses.isEmpty()) {
      return results;
    }

    store
        .resolve(misses)
        .forEach(
            (name, result) -> {
              if (result instanceof final SecretResolutionResult.Resolved resolved) {
                cache.put(name, resolved.value());
              }
              results.put(name, result);
            });
    return results;
  }

  @Override
  public List<String> list() {
    return store.list();
  }
}
