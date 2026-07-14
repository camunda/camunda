/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.secretstore.NoopSecretStore;
import io.camunda.secretstore.SecretStore;
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
    final var store = (SecretStore<?>) NOOP;
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
    final var storeA = (SecretStore<?>) new NoopSecretStore();
    final var storeB = (SecretStore<?>) new NoopSecretStore();
    final var registry = new SecretStoreRegistry(Map.of("store-a", storeA, "store-b", storeB));

    // when
    final var stores = registry.getStores();

    // then
    assertThat(stores).containsKeys("store-a", "store-b");
  }
}
