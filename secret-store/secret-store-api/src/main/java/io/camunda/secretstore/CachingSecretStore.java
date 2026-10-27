/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
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
 * <p>A resolved value is cached by either read, but only {@link #resolveFromStore} treats a failure
 * as authoritative: a permanent {@link SecretErrorCode} there clears any stale cached value for
 * that name, while a {@link SecretErrorCode#UNREADABLE} failure is transient and leaves the stale
 * value in place rather than dropping a still-healthy secret over a read blip. A failure from
 * {@link #resolve}'s own cache-miss read never clears anything: that read only ever runs for a name
 * already absent from the cache, so treating it as authoritative would let a request-time read that
 * failed for reasons of its own erase a value {@link #resolveFromStore} just established as
 * current. {@link #list()} goes straight to the store, since a cache holds the values read so far
 * rather than the store's set of secrets.
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
   * the store left it out, so a caller can still tell an unanswered name apart from a failed one. A
   * store failure here is returned but never clears a cached value: this read only ever reaches the
   * store for a name already absent from the cache, so it is not the authoritative source a stale
   * value should be invalidated against — {@link #resolveFromStore} is.
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
        results.put(name, new Resolved(cached));
      }
    }
    if (misses.isEmpty()) {
      return results;
    }

    store
        .resolve(misses)
        .forEach(
            (name, result) -> {
              if (result instanceof Resolved(final var value)) {
                cache.put(name, value);
              }
              results.put(name, result);
            });
    return results;
  }

  /**
   * Asks the store for the given names regardless of what the cache holds, and updates the cache
   * with what it answers: a resolved value replaces what was held, and a permanent failure clears
   * it. This is the one path allowed to invalidate a stale cached value, since it is the only
   * caller that treats the store's answer as authoritative rather than a request-time convenience.
   */
  @Override
  public Map<String, SecretResolutionResult> resolveFromStore(final Set<String> names) {
    final Map<String, SecretResolutionResult> results = new LinkedHashMap<>();
    store
        .resolve(names)
        .forEach(
            (name, result) -> {
              switch (result) {
                case Resolved(final var value) -> cache.put(name, value);
                case final Failed failed -> invalidateOnPermanentFailure(name, failed.code());
              }
              results.put(name, result);
            });
    return results;
  }

  /**
   * Clears a cached value only for a permanent failure. {@link SecretErrorCode#UNREADABLE} is
   * transient, so the stale value is left in place rather than dropping a still-healthy secret over
   * a read blip. The switch is exhaustive and without a {@code default}, so a code added later has
   * to be classified here explicitly instead of silently falling into either branch.
   */
  private void invalidateOnPermanentFailure(final String name, final SecretErrorCode code) {
    final boolean permanent =
        switch (code) {
          case NOT_FOUND, ACCESS_DENIED, INVALID_REF -> true;
          case UNREADABLE -> false;
        };
    if (permanent) {
      cache.remove(name);
    }
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
   * Answers for the store this caches for rather than for this wrapper, so a caller asking which
   * store was configured gets the same answer whether or not the store had to be wrapped.
   *
   * <p>Delegates rather than testing the wrapped store itself, so the answer still reaches the
   * innermost store if one wrapper ever sits behind another.
   */
  @Override
  public boolean is(final Class<? extends SecretStore> storeType) {
    return store.is(storeType);
  }
}
