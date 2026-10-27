/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.search.clients.PersistentWebSessionClient;
import io.camunda.search.clients.PhysicalTenantScopedPersistentWebSessionClient;
import io.camunda.search.entities.PersistentWebSessionEntity;
import io.camunda.security.api.model.session.PersistentSession;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PhysicalTenantScopedSessionStorePortProvider}. */
class PhysicalTenantScopedSessionStorePortProviderTest {

  private static PhysicalTenantScopedSessionStorePortProvider providerWith(
      final Map<String, PersistentWebSessionClient> clientsByTenant) {
    return new PhysicalTenantScopedSessionStorePortProvider(
        new PhysicalTenantScopedPersistentWebSessionClient(clientsByTenant));
  }

  @Test
  void shouldRouteBasePathToItsPhysicalTenantStore() {
    // given — a distinct client per tenant
    final var tenantA = new PersistentWebSessionClientStub();
    final var tenantB = new PersistentWebSessionClientStub();
    final var provider = providerWith(Map.of("tenant-a", tenantA, "tenant-b", tenantB));

    // when — the port for tenant-a's basePath writes a session
    provider
        .forBasePath("/physical-tenants/tenant-a")
        .upsert(new PersistentSession("s1", 1L, 2L, 1800L, Map.of()));

    // then — it lands only in tenant-a's store
    assertThat(tenantA.getPersistentWebSession("s1")).isNotNull();
    assertThat(tenantB.getPersistentWebSession("s1")).isNull();
  }

  @Test
  void shouldRouteDefaultBasePathToDefaultTenantStore() {
    // given
    final var defaultClient = new PersistentWebSessionClientStub();
    defaultClient.upsertPersistentWebSession(
        new PersistentWebSessionEntity("s1", 1L, 2L, 1800L, Map.of()));
    final var provider =
        providerWith(Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, defaultClient));

    // when / then
    final var session =
        provider
            .forBasePath("/physical-tenants/" + PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
            .get("s1");
    assertThat(session).isNotNull();
    assertThat(session.id()).isEqualTo("s1");
  }

  @Test
  void shouldCacheAndShareTheAdapterForTheSamePhysicalTenant() {
    // given
    final var provider = providerWith(Map.of("tenant-a", new PersistentWebSessionClientStub()));

    // when — resolved via forBasePath, then again via forPhysicalTenant for the same tenant
    final var viaBasePath = provider.forBasePath("/physical-tenants/tenant-a");
    final var viaPhysicalTenant = provider.forPhysicalTenant("tenant-a");

    // then — same cached instance, so callers outside a basePath context (e.g. the global session
    // filter's default-surface store) can share it with a scope's store (ADR-0029 §4)
    assertThat(viaBasePath).isSameAs(viaPhysicalTenant);
  }

  @Test
  void shouldRejectBasePathWithoutPhysicalTenantPrefix() {
    final var provider = providerWith(Map.of("tenant-a", new PersistentWebSessionClientStub()));

    assertThatThrownBy(() -> provider.forBasePath("/some/other/scope"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("physical tenant id");
  }

  @Test
  void shouldRejectBasePathWithEmptyTenantSegment() {
    final var provider = providerWith(Map.of("tenant-a", new PersistentWebSessionClientStub()));

    assertThatThrownBy(() -> provider.forBasePath("/physical-tenants/"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
