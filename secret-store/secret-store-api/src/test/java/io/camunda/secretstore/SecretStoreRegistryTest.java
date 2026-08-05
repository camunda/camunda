/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecretStoreRegistryTest {

  private static final NoopSecretStore NOOP = new NoopSecretStore();

  @Test
  void shouldReturnEmptyStoresWhenNoneConfigured() {
    // given
    final var registry = new SecretStoreRegistry(Map.of());

    // when
    final var stores = registry.getStores();

    // then
    assertThat(stores).isEmpty();
  }

  @Test
  void shouldReturnConfiguredStore() {
    // given
    final var registry = new SecretStoreRegistry(Map.of("default", storeHolding("token", "value")));

    // when
    final var stores = registry.getStores();

    // then the configured store is what answers under its ID
    assertThat(stores).containsKey("default");
    assertThat(stores.get("default").resolve(Set.of("token")))
        .containsEntry("token", new Resolved("value"));
  }

  @Test
  void shouldReturnAllConfiguredStores() {
    // given
    final var registry =
        new SecretStoreRegistry(Map.of("store-a", NOOP, "store-b", new NoopSecretStore()));

    // when
    final var stores = registry.getStores();

    // then
    assertThat(stores).containsKeys("store-a", "store-b");
  }

  @Test
  void shouldNotAllowTheStoresToBeModified() {
    // given
    final var registry = new SecretStoreRegistry(Map.of("default", NOOP));

    // when / then the configured stores cannot be swapped out from the outside
    assertThatThrownBy(() -> registry.getStores().remove("default"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldServeALocalLookupWithAValueAStoreResolved() {
    // given a store that has resolved a secret once
    final var registry = new SecretStoreRegistry(Map.of("default", storeHolding("token", "value")));
    registry.getStores().get("default").resolve(Set.of("token"));

    // when
    final var local = lookupLocal(registry, "default", "token");

    // then the value a resolution read is what a later local lookup sees: this is what lets a
    // background resolution unblock the activation that requested it
    assertThat(local).contains("value");
  }

  @Test
  void shouldNotServeALocalLookupForAnUnresolvedName() {
    // given
    final var registry = new SecretStoreRegistry(Map.of("default", storeHolding("token", "value")));

    // when / then nothing is held locally until something resolved it
    assertThat(lookupLocal(registry, "default", "token")).isEmpty();
  }

  @Test
  void shouldUseProvidedCaches() {
    // given
    final var cache = new InMemorySecretCache();
    cache.put("token", "value");

    // when
    final var registry = new SecretStoreRegistry(Map.of("default", NOOP), Map.of("default", cache));

    // then the store caches in the given cache, so what it holds answers a local lookup
    assertThat(lookupLocal(registry, "default", "token")).contains("value");
  }

  @Test
  void shouldCacheAStoreTheProvidedCachesDoNotCover() {
    // given caches covering only one of the two configured stores
    final var cache = new InMemorySecretCache();
    cache.put("token", "covered-value");
    final var registry =
        new SecretStoreRegistry(
            Map.of("store-a", NOOP, "store-b", storeHolding("token", "uncovered-value")),
            Map.of("store-a", cache));

    // when the uncovered store resolves a secret
    registry.getStores().get("store-b").resolve(Set.of("token"));

    // then it cached it too, so a caller never has to handle a store that cannot cache
    assertThat(lookupLocal(registry, "store-a", "token")).contains("covered-value");
    assertThat(lookupLocal(registry, "store-b", "token")).contains("uncovered-value");
  }

  @Test
  void shouldKeepTheCachedValuesOfTwoStoresApart() {
    // given two stores holding a different value under the same name
    final var registry =
        new SecretStoreRegistry(
            Map.of("store-a", storeHolding("token", "a"), "store-b", storeHolding("token", "b")));

    // when only one of them resolves it
    registry.getStores().get("store-a").resolve(Set.of("token"));

    // then the other one holds nothing: what a store caches is its own
    assertThat(lookupLocal(registry, "store-a", "token")).contains("a");
    assertThat(lookupLocal(registry, "store-b", "token")).isEmpty();
  }

  @Test
  void shouldHandOutAStoreThatCachesNativelyAsItIs() {
    // given a store that already holds what it resolves, as a cloud SDK with a client-side cache
    // does
    final var nativelyCaching = new NativelyCachingSecretStore();

    // when
    final var registry = new SecretStoreRegistry(Map.of("default", nativelyCaching));

    // then it is handed out unwrapped: wrapping it would put a second cache in front of its own
    assertThat(registry.getStores().get("default")).isSameAs(nativelyCaching);
  }

  @Test
  void shouldNotAddressAStoreByAnEmptyStoreIdInTheStoresMap() {
    // given a single configured store
    final var registry = new SecretStoreRegistry(Map.of("default", storeHolding("token", "value")));

    // when / then a lookup in the map is exact: only findStoreForReference reads an empty store ID
    // as the sole store, and only for the caller that addresses a store by secret reference
    assertThat(registry.getStores().get("")).isNull();
  }

  @Test
  void shouldFindTheSoleStoreWhenTheReferenceNamesNone() {
    // given a single configured store
    final var store = storeHolding("token", "value");
    final var registry = new SecretStoreRegistry(Map.of("default", store));

    // when the reference carries no store ID, as camunda.secrets.<name> has no store dimension
    final var found = registry.findStoreForReference("");

    // then the sole store is addressed rather than none
    assertThat(found).isPresent();
    assertThat(found.get().resolve(Set.of("token"))).containsEntry("token", new Resolved("value"));
  }

  @Test
  void shouldNotFindAStoreWithoutAStoreIdWhenSeveralStoresAreConfigured() {
    // given two configured stores
    final var registry =
        new SecretStoreRegistry(
            Map.of("store-a", storeHolding("token", "a"), "store-b", storeHolding("token", "b")));

    // when / then an empty store ID is ambiguous with more than one store, so nothing is guessed
    assertThat(registry.findStoreForReference("")).isEmpty();
  }

  @Test
  void shouldNotFindAnUnconfiguredStore() {
    // given
    final var registry = new SecretStoreRegistry(Map.of("default", storeHolding("token", "value")));

    // when / then a store ID nothing is configured for addresses no store rather than any store
    assertThat(registry.findStoreForReference("other")).isEmpty();
  }

  @Test
  void shouldNotFindAStoreWhenNoneIsConfigured() {
    // given
    final var registry = new SecretStoreRegistry(Map.of());

    // when / then
    assertThat(registry.findStoreForReference("")).isEmpty();
    assertThat(registry.findStoreForReference("default")).isEmpty();
  }

  @Test
  void shouldNotReadTheStoreOnALocalLookup() {
    // given a store that fails the test if it is read at all
    final var registry = new SecretStoreRegistry(Map.of("default", unreadableStore()));

    // when / then a local lookup runs on the stream processor, where a store read would block
    // processing; nothing structural stops an implementer from adding one, so it is asserted here
    assertThat(lookupLocal(registry, "default", "token")).isEmpty();
    assertThat(lookupLocal(registry, "", "token")).isEmpty();
  }

  @Test
  void shouldResolveFromTheStoreDespiteAHeldValue() {
    // given a store whose held value is stale: it now fails for the name it was resolved for
    final var store = new MutableSecretStore("token", "value");
    final var registry = new SecretStoreRegistry(Map.of("default", store));
    registry.getStores().get("default").resolve(Set.of("token"));
    store.loses("token");

    // when
    final var results = registry.getStores().get("default").resolveFromStore(Set.of("token"));

    // then the store is asked and its answer wins over the held value: the background resolution
    // records a durable outcome, so it must not complete off a value the store no longer has
    assertThat(store.resolveCalls).containsExactly(Set.of("token"), Set.of("token"));
    assertThat(results.get("token")).isInstanceOf(Failed.class);
  }

  @Test
  void shouldHoldOnToWhatAResolveFromTheStoreRead() {
    // given a store nothing resolved from yet
    final var registry = new SecretStoreRegistry(Map.of("default", storeHolding("token", "value")));

    // when it is resolved bypassing what is held, as the background resolution does
    registry.getStores().get("default").resolveFromStore(Set.of("token"));

    // then the value is held afterwards: this is what unblocks the activation that requested the
    // background resolution
    assertThat(lookupLocal(registry, "default", "token")).contains("value");
  }

  private static Optional<String> lookupLocal(
      final SecretStoreRegistry registry, final String storeId, final String name) {
    return registry.findStoreForReference(storeId).flatMap(store -> store.lookupLocal(name));
  }

  /** A store answering with the given value for the given name, and NOT_FOUND for anything else. */
  private static SecretStore storeHolding(final String name, final String value) {
    return new SecretStore() {
      @Override
      public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
        return resultsFor(names, Map.of(name, value));
      }

      @Override
      public List<String> list() {
        return List.of(name);
      }
    };
  }

  /** A store that fails the test when it is read, to assert a code path never reaches one. */
  private static SecretStore unreadableStore() {
    return new SecretStore() {
      @Override
      public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
        return fail("the store must not be resolved from");
      }

      @Override
      public List<String> list() {
        return fail("the store must not be listed");
      }
    };
  }

  private static Map<String, SecretResolutionResult> resultsFor(
      final Set<String> names, final Map<String, String> values) {
    final Map<String, SecretResolutionResult> results = new LinkedHashMap<>();
    names.forEach(
        requested -> {
          final var value = values.get(requested);
          results.put(
              requested,
              value == null
                  ? new Failed(SecretErrorCode.NOT_FOUND, "no secret found: " + requested, null)
                  : new Resolved(value));
        });
    return results;
  }

  /** A store whose set of secrets can change, to tell a held value apart from the store's own. */
  private static final class MutableSecretStore implements SecretStore {

    private final Map<String, String> values = new LinkedHashMap<>();
    private final List<Set<String>> resolveCalls = new ArrayList<>();

    private MutableSecretStore(final String name, final String value) {
      values.put(name, value);
    }

    @Override
    public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
      resolveCalls.add(Set.copyOf(names));
      return resultsFor(names, values);
    }

    @Override
    public List<String> list() {
      return List.copyOf(values.keySet());
    }

    void loses(final String name) {
      values.remove(name);
    }
  }

  /**
   * A store that holds what it resolved itself, standing in for one whose SDK caches client-side.
   */
  private static final class NativelyCachingSecretStore implements LocallyCachedSecretStore {

    private final Map<String, String> held = new LinkedHashMap<>();

    @Override
    public Optional<String> lookupLocal(final String name) {
      return Optional.ofNullable(held.get(name));
    }

    @Override
    public Map<String, SecretResolutionResult> resolveFromStore(final Set<String> names) {
      return resultsFor(names, held);
    }

    @Override
    public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
      return resolveFromStore(names);
    }

    @Override
    public List<String> list() {
      return List.copyOf(held.keySet());
    }
  }
}
