/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
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
    final var store = NOOP;
    final var registry = new SecretStoreRegistry(Map.of("default", store));

    // when
    final var stores = registry.getStores();

    // then
    assertThat(stores).containsKey("default");
    assertThat(stores.get("default")).isSameAs(store);
  }

  @Test
  void shouldReturnAllConfiguredStores() {
    // given
    final var storeA = new NoopSecretStore();
    final var storeB = new NoopSecretStore();
    final var registry = new SecretStoreRegistry(Map.of("store-a", storeA, "store-b", storeB));

    // when
    final var stores = registry.getStores();

    // then
    assertThat(stores).containsKeys("store-a", "store-b");
  }

  @Test
  void shouldUseProvidedCaches() {
    // given
    final var cache = new InMemorySecretCache();
    cache.put("token", "value");

    // when
    final var registry = new SecretStoreRegistry(Map.of("default", NOOP), Map.of("default", cache));

    // then
    assertThat(registry.getCaches().get("default")).isSameAs(cache);
  }

  @Test
  void shouldCreateOneCachePerConfiguredStore() {
    // given
    final var registry =
        new SecretStoreRegistry(Map.of("store-a", NOOP, "store-b", new NoopSecretStore()));

    // when
    final var caches = registry.getCaches();

    // then - one cache per store, keyed by the same ID
    assertThat(caches).containsKeys("store-a", "store-b");
  }
}
