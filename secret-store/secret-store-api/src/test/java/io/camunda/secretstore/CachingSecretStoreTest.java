/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CachingSecretStoreTest {

  private final RecordingSecretStore store = new RecordingSecretStore();
  private final InMemorySecretCache cache = new InMemorySecretCache();
  private final CachingSecretStore cachingStore = new CachingSecretStore(store, cache);

  @Test
  void shouldServeACachedNameWithoutAskingTheStore() {
    // given
    cache.put("token", "cached-value");

    // when
    final var results = cachingStore.resolve(Set.of("token"));

    // then
    assertThat(results.get("token")).isEqualTo(new Resolved("cached-value"));
    assertThat(store.resolveCalls).isEmpty();
  }

  @Test
  void shouldOnlyAskTheStoreForUncachedNames() {
    // given one of two names is cached
    store.holds("b", "b-value");
    cache.put("a", "cached-a-value");

    // when
    final var results = cachingStore.resolve(Set.of("a", "b"));

    // then only the miss reaches the store, and both names are still answered
    assertThat(store.resolveCalls).containsExactly(Set.of("b"));
    assertThat(results)
        .containsEntry("a", new Resolved("cached-a-value"))
        .containsEntry("b", new Resolved("b-value"));
  }

  @Test
  void shouldCacheAValueReadFromTheStore() {
    // given
    store.holds("token", "token-value");

    // when the same name is resolved twice
    cachingStore.resolve(Set.of("token"));
    final var second = cachingStore.resolve(Set.of("token"));

    // then the store was read once and the cached value answers the second call
    assertThat(store.resolveCalls).containsExactly(Set.of("token"));
    assertThat(second.get("token")).isEqualTo(new Resolved("token-value"));
    assertThat(cache.get("token")).contains("token-value");
  }

  @Test
  void shouldNotCacheAFailure() {
    // given a name the store cannot read
    store.fails("token", SecretErrorCode.UNREADABLE);

    // when the same name is resolved twice
    cachingStore.resolve(Set.of("token"));
    cachingStore.resolve(Set.of("token"));

    // then the store is asked again: caching the failure would keep the secret unresolvable until
    // the process restarts
    assertThat(store.resolveCalls).containsExactly(Set.of("token"), Set.of("token"));
    assertThat(cache.get("token")).isEmpty();
  }

  @Test
  void shouldLeaveOutANameTheStoreDidNotAnswerFor() {
    // given a store that answers without a result for the name it was asked for
    store.omitsResults();

    // when
    final var results = cachingStore.resolve(Set.of("token"));

    // then the name is absent rather than made up into a failure, so a caller can still tell an
    // unanswered name apart from a failed one
    assertThat(results).doesNotContainKey("token");
  }

  @Test
  void shouldListFromTheStoreRatherThanTheCache() {
    // given a cached value the store no longer holds
    store.holds("token", "token-value");
    cache.put("ghost", "cached-value");

    // when
    final var names = cachingStore.list();

    // then the listing is the store's own: a cache holds the values read so far, not the store's
    // set of secrets
    assertThat(names).containsExactly("token");
  }

  private static final class RecordingSecretStore implements SecretStore {

    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, SecretErrorCode> failures = new LinkedHashMap<>();
    private final List<Set<String>> resolveCalls = new ArrayList<>();
    private boolean omitResults;

    @Override
    public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
      resolveCalls.add(Set.copyOf(names));
      final Map<String, SecretResolutionResult> results = new LinkedHashMap<>();
      if (omitResults) {
        return results;
      }
      names.forEach(name -> results.put(name, resultFor(name)));
      return results;
    }

    @Override
    public List<String> list() {
      return List.copyOf(values.keySet());
    }

    void holds(final String name, final String value) {
      values.put(name, value);
    }

    void fails(final String name, final SecretErrorCode code) {
      failures.put(name, code);
    }

    void omitsResults() {
      omitResults = true;
    }

    private SecretResolutionResult resultFor(final String name) {
      final var failure = failures.get(name);
      if (failure != null) {
        return new Failed(failure, "failed: " + name, null);
      }
      final var value = values.get(name);
      return value == null
          ? new Failed(SecretErrorCode.NOT_FOUND, "no secret found: " + name, null)
          : new Resolved(value);
    }
  }
}
