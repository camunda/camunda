/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.camunda.configuration.Secrets;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.SecretStoreRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class SecretStoreRegistriesTest {

  private final Map<String, SecretStore> nextStore = new HashMap<>();
  private final SecretStoreRegistries registries =
      new SecretStoreRegistries(
          (tenantId, secrets) -> {
            final var store = nextStore.remove(tenantId);
            if (store == null) {
              throw new IllegalStateException("failed to build the stores of " + tenantId);
            }
            final var resources = new SecretStoreResources();
            resources.addStore(store);
            return new TenantSecretStores(
                new SecretStoreRegistry(Map.of(SecretStoreRegistry.DEFAULT_STORE_ID, store)),
                resources);
          });

  @Test
  void shouldReleaseTheResourcesWhenTheApplicationContextCloses() {
    // given a context holding registries that own a store
    final var store = givenStoresOf("tenanta");
    final var context = new AnnotationConfigApplicationContext();
    context.registerBean(SecretStoreRegistries.class, () -> registries);
    context.refresh();

    // when
    context.close();

    // then Spring's own bean destruction closed the store, nothing had to call close explicitly
    verify(store).close();
  }

  @Test
  void shouldCloseOnlyTheReplacedStoresOfOneTenant() {
    // given
    final var storeA = givenStoresOf("tenanta");
    final var storeB = givenStoresOf("tenantb");
    final var registryA = registries.forPhysicalTenant("tenanta");
    final var registryB = registries.forPhysicalTenant("tenantb");
    nextStore.put("tenanta", mock(SecretStore.class));

    // when
    registries.put("tenanta", new Secrets());

    // then
    verify(storeA).close();
    verify(storeB, never()).close();
    assertThat(registries.forPhysicalTenant("tenanta")).isNotSameAs(registryA);
    assertThat(registries.forPhysicalTenant("tenantb")).isSameAs(registryB);
  }

  @Test
  void shouldKeepTheCurrentStoresWhenTheNewOnesFailToBuild() {
    // given
    final var store = givenStoresOf("tenanta");
    final var registry = registries.forPhysicalTenant("tenanta");

    // when
    assertThatThrownBy(() -> registries.put("tenanta", new Secrets()))
        .hasMessage("failed to build the stores of tenanta");

    // then
    verify(store, never()).close();
    assertThat(registries.forPhysicalTenant("tenanta")).isSameAs(registry);
  }

  @Test
  void shouldCloseTheStoresOfARemovedTenantOnly() {
    // given
    final var storeA = givenStoresOf("tenanta");
    final var storeB = givenStoresOf("tenantb");

    // when
    registries.remove("tenanta");

    // then
    verify(storeA).close();
    verify(storeB, never()).close();
    assertThat(registries.byPhysicalTenant()).containsOnlyKeys("tenantb");
  }

  @Test
  void shouldNotBuildStoresAfterTheRegistriesAreClosed() {
    // given
    givenStoresOf("tenanta");
    registries.close();
    final var store = mock(SecretStore.class);
    nextStore.put("tenanta", store);

    // when
    assertThatThrownBy(() -> registries.put("tenanta", new Secrets()))
        .isInstanceOf(IllegalStateException.class);

    // then
    assertThat(nextStore).containsEntry("tenanta", store);
    assertThat(registries.byPhysicalTenant()).isEmpty();
  }

  @Test
  void shouldPublishTheMetersOfNewStoresOnlyAfterTheCurrentStoresAreClosed() {
    // given
    final var events = new ArrayList<String>();
    final var tenantRegistries =
        new SecretStoreRegistries(
            (tenantId, secrets) -> {
              final var generation =
                  "generation" + (events.stream().filter("built"::equals).count() + 1);
              events.add("built");
              final var store = mock(SecretStore.class);
              doAnswer(
                      invocation -> {
                        events.add("closed " + generation);
                        return null;
                      })
                  .when(store)
                  .close();
              final var resources = new SecretStoreResources();
              resources.addStore(store);
              resources.onPublish(() -> events.add("published " + generation));
              return new TenantSecretStores(
                  new SecretStoreRegistry(Map.of(SecretStoreRegistry.DEFAULT_STORE_ID, store)),
                  resources);
            });
    tenantRegistries.put("tenanta", new Secrets());

    // when
    tenantRegistries.put("tenanta", new Secrets());

    // then
    assertThat(events)
        .containsExactly(
            "built",
            "published generation1",
            "built",
            "closed generation1",
            "published generation2");
  }

  @Test
  void shouldLeaveTheCurrentStoresAndTheirMetersAloneWhenTheNewOnesFailToBuild() {
    // given
    final var meters = new SimpleMeterRegistry();
    final var current = mock(SecretStore.class);
    final var resources = new SecretStoreResources();
    resources.addStore(current);
    resources.addMeterRegistry(meters);
    final var first = new AtomicBoolean(true);
    final var tenantRegistries =
        new SecretStoreRegistries(
            (tenantId, secrets) -> {
              if (!first.getAndSet(false)) {
                throw new IllegalStateException("failed to build");
              }
              return new TenantSecretStores(
                  new SecretStoreRegistry(Map.of(SecretStoreRegistry.DEFAULT_STORE_ID, current)),
                  resources);
            });
    tenantRegistries.put("tenanta", new Secrets());
    final var registry = tenantRegistries.forPhysicalTenant("tenanta");

    // when
    assertThatThrownBy(() -> tenantRegistries.put("tenanta", new Secrets()))
        .hasMessage("failed to build");

    // then
    verify(current, never()).close();
    assertThat(meters.isClosed()).isFalse();
    assertThat(tenantRegistries.forPhysicalTenant("tenanta")).isSameAs(registry);
  }

  private SecretStore givenStoresOf(final String tenantId) {
    final var store = mock(SecretStore.class);
    nextStore.put(tenantId, store);
    registries.put(tenantId, new Secrets());
    return store;
  }
}
