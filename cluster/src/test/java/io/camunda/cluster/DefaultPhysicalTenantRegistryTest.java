/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.camunda.cluster.PhysicalTenantHealth.State;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class DefaultPhysicalTenantRegistryTest {

  @Test
  void shouldExposeInitialTenantsAsKnownIds() {
    // given
    final var tenant = PhysicalTenant.newIncarnation("tenanta", "Tenant A");

    // when
    final var registry = new DefaultPhysicalTenantRegistry(List.of(tenant));

    // then
    assertThat(registry.known()).containsExactly("tenanta");
    assertThat(registry.find("tenanta")).contains(tenant);
    assertThat(registry.health("tenanta")).contains(PhysicalTenantHealth.of(State.STARTING));
  }

  @Test
  void shouldReflectAddedTenantInKnownIdsReadBeforeTheAdd() {
    // given
    final var registry = new DefaultPhysicalTenantRegistry(List.of());
    final PhysicalTenantIds ids = registry;

    // when
    registry.add(PhysicalTenant.newIncarnation("tenantb", null));

    // then
    assertThat(ids.known()).containsExactly("tenantb");
  }

  @Test
  void shouldNotifyListenersOfTheWholeLifecycle() {
    // given
    final var registry = new DefaultPhysicalTenantRegistry(List.of());
    final var events = new ArrayList<String>();
    registry.addListener(new RecordingListener(events));
    final var tenant = PhysicalTenant.newIncarnation("tenantc", "C");

    // when
    registry.add(tenant);
    registry.reportHealth("tenantc", PhysicalTenantHealth.of(State.HEALTHY));
    registry.replace(tenant.withName("Renamed"));
    registry.remove("tenantc");

    // then
    assertThat(events)
        .containsExactly(
            "added tenantc",
            "health tenantc STARTING",
            "health tenantc HEALTHY",
            "replaced tenantc C -> Renamed",
            "removed tenantc",
            "health tenantc REMOVED");
  }

  @Test
  void shouldKeepRemovedIdReserved() {
    // given
    final var registry =
        new DefaultPhysicalTenantRegistry(List.of(PhysicalTenant.newIncarnation("tenantd", null)));

    // when
    registry.remove("tenantd");

    // then
    assertThat(registry.known()).isEmpty();
    assertThat(registry.isRemoved("tenantd")).isTrue();
    assertThat(registry.health("tenantd")).contains(PhysicalTenantHealth.of(State.REMOVED));
    assertThat(catchThrowable(() -> registry.add(PhysicalTenant.newIncarnation("tenantd", null))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("stays reserved");
  }

  @Test
  void shouldRejectReplaceWithAnotherIncarnation() {
    // given
    final var registry =
        new DefaultPhysicalTenantRegistry(List.of(PhysicalTenant.newIncarnation("tenante", null)));

    // when
    final var thrown =
        catchThrowable(
            () -> registry.replace(new PhysicalTenant("tenante", null, UUID.randomUUID())));

    // then
    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must keep its incarnation");
  }

  @Test
  void shouldGiveEveryAddANewIncarnation() {
    // given
    final var first = PhysicalTenant.newIncarnation("tenantf", null);

    // when
    final var second = PhysicalTenant.newIncarnation("tenantf", null);

    // then
    assertThat(second.incarnationId()).isNotEqualTo(first.incarnationId());
  }

  @Test
  void shouldAllowTwoTenantsWithTheSameName() {
    // given
    final var registry = new DefaultPhysicalTenantRegistry(List.of());

    // when
    registry.add(PhysicalTenant.newIncarnation("tenantg", "Shared"));

    // then
    assertThatCode(() -> registry.add(PhysicalTenant.newIncarnation("tenanth", "Shared")))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldLetAComponentMarkATenantDegraded() {
    // given
    final var registry =
        new DefaultPhysicalTenantRegistry(List.of(PhysicalTenant.newIncarnation("tenanti", null)));

    // when
    registry.reportHealth("tenanti", new PhysicalTenantHealth(State.DEGRADED, "secret is stale"));

    // then
    assertThat(registry.health("tenanti"))
        .contains(new PhysicalTenantHealth(State.DEGRADED, "secret is stale"));
  }

  @Test
  void shouldRejectReportingRemovedOrUnknownTenantHealth() {
    // given
    final var registry =
        new DefaultPhysicalTenantRegistry(List.of(PhysicalTenant.newIncarnation("tenantj", null)));

    // when
    final var removed =
        catchThrowable(
            () -> registry.reportHealth("tenantj", PhysicalTenantHealth.of(State.REMOVED)));
    final var unknown =
        catchThrowable(
            () -> registry.reportHealth("unknown", PhysicalTenantHealth.of(State.HEALTHY)));

    // then
    assertThat(removed).isInstanceOf(IllegalArgumentException.class);
    assertThat(unknown).isInstanceOf(IllegalArgumentException.class);
  }

  private record RecordingListener(List<String> events) implements PhysicalTenantRegistry.Listener {

    @Override
    public void onAdded(final PhysicalTenant tenant) {
      events.add("added " + tenant.id());
    }

    @Override
    public void onReplaced(final PhysicalTenant previous, final PhysicalTenant current) {
      events.add("replaced %s %s -> %s".formatted(current.id(), previous.name(), current.name()));
    }

    @Override
    public void onRemoved(final PhysicalTenant tenant) {
      events.add("removed " + tenant.id());
    }

    @Override
    public void onHealthChanged(final String physicalTenantId, final PhysicalTenantHealth health) {
      events.add("health %s %s".formatted(physicalTenantId, health.state()));
    }
  }
}
