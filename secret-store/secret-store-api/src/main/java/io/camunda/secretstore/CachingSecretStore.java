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
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/**
 * Makes any {@link SecretStore} a {@link LocallyCachedSecretStore} by putting a {@link SecretCache}
 * in front of it, so a caller resolves secrets through a single call instead of driving store and
 * cache itself. The cache is this store's own: no caller is handed one alongside it, and nothing
 * reaches the wrapped store except through here.
 *
 * <p>Only a resolved value is cached: a failure has to stay retryable, or a secret that was briefly
 * unreadable would stay unresolvable until the process restarts. {@link #list()} goes straight to
 * the store, since a cache holds the values read so far rather than the store's set of secrets.
 *
 * <p>What the cache holds is also what {@link #lookupLocal} answers with, so a value read by one
 * caller is what a later cache-only lookup of another sees.
 *
 * <p>Does not close the store it wraps, and closing this releases nothing: the wrapped store's
 * lifecycle belongs to whoever configured it.
 */
@NullMarked
public final class CachingSecretStore implements LocallyCachedSecretStore {

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

    results.putAll(resolveAndCache(misses));
    return results;
  }

  @Override
  public Map<String, SecretResolutionResult> resolveFromStore(final Set<String> names) {
    return resolveAndCache(names);
  }

  /** Serves what the cache holds, never reading the wrapped store. */
  @Override
  public Optional<String> lookupLocal(final String name) {
    return cache.get(name);
  }

  @Override
  public List<String> list() {
    return store.list();
  }

  /**
   * Whether the store this caches for is of the given type. Visible for testing only: it lets a
   * configuration test tell the store that was built apart without reading it, which for a cloud
   * store would mean a call to the cloud provider.
   *
   * <p>Deliberately a predicate rather than an accessor for the store: handing the store out is the
   * uncached read path this wrapper exists to close.
   */
  public boolean wraps(final Class<? extends SecretStore> storeType) {
    return storeType.isInstance(store);
  }

  /** Asks the store for the given names and caches every value it answers with. */
  private Map<String, SecretResolutionResult> resolveAndCache(final Set<String> names) {
    final Map<String, SecretResolutionResult> results = new LinkedHashMap<>();
    store
        .resolve(names)
        .forEach(
            (name, result) -> {
              if (result instanceof final SecretResolutionResult.Resolved resolved) {
                cache.put(name, resolved.value());
              }
              results.put(name, result);
            });
    return results;
  }
}
