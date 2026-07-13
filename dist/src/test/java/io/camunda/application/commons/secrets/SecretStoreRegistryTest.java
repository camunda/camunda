/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.camunda.secretstore.NoopSecretStore;
import io.camunda.secretstore.SecretStore;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SecretStoreRegistryTest {

  private static final NoopSecretStore NOOP = new NoopSecretStore();

  @Test
  void shouldThrowForUnknownTenant() {
    // given
    final var registry = new SecretStoreRegistry(Map.of("known-tenant", Map.of()));

    // when / then
    assertThatIllegalArgumentException()
        .isThrownBy(() -> registry.getStores("unknown-tenant"))
        .withMessageContaining("unknown-tenant");
  }

  @Test
  void shouldReturnKnownTenants() {
    // given
    final var registry =
        new SecretStoreRegistry(Map.of("tenant-a", Map.of(), "tenant-b", Map.of()));

    // when
    final var tenants = registry.tenants();

    // then
    assertThat(tenants).containsExactlyInAnyOrder("tenant-a", "tenant-b");
  }

  @Test
  void shouldGetStoresForKnownTenant() {
    // given
    final var store = (SecretStore<?>) NOOP;
    final var registry = new SecretStoreRegistry(Map.of("tenant", Map.of("default", store)));

    // when
    final var stores = registry.getStores("tenant");

    // then
    assertThat(stores).containsKey("default");
    assertThat(stores.get("default")).isSameAs(store);
  }
}
