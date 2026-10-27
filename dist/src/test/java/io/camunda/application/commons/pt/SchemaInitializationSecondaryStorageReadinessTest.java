/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.pt.SchemaInitializationStatus.State;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.cluster.SecondaryStorageReadiness.NodeReadiness;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

class SchemaInitializationSecondaryStorageReadinessTest {

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";

  @Test
  void shouldDelegateReadinessToSchemaInitializedPredicate() {
    // given
    final var readiness =
        new SchemaInitializationSecondaryStorageReadiness(
            () -> Set.of(TENANT_A), TENANT_A::equals, tenantId -> false, Map::of);

    // when/then
    assertThat(readiness.isReady(TENANT_A)).isTrue();
    assertThat(readiness.isReady(TENANT_B)).isFalse();
  }

  @Test
  void shouldReportUnknownTenantAsNotReady() {
    // given - mirrors the real schema-init predicates (SchemaManagerContainer,
    // RdbmsSchemaManagerRegistry), which are backed by a per-tenant map and report false for a
    // key they don't hold
    final var readiness =
        new SchemaInitializationSecondaryStorageReadiness(
            () -> Set.of(TENANT_A), Set.of(TENANT_A)::contains, tenantId -> false, Map::of);

    // when/then
    assertThat(readiness.isReady("unknown")).isFalse();
  }

  @Test
  void shouldReportNoTenantsReadyWhenNoneAreInitialized() {
    // given
    final var readiness =
        new SchemaInitializationSecondaryStorageReadiness(
            () -> Set.of(TENANT_A, TENANT_B), tenantId -> false, tenantId -> false, Map::of);

    // when/then
    assertThat(readiness.anyReady()).isFalse();
  }

  @Test
  void shouldReportAnyReadyWhenSomeTenantsAreInitialized() {
    // given
    final var readiness =
        new SchemaInitializationSecondaryStorageReadiness(
            () -> Set.of(TENANT_A, TENANT_B), TENANT_A::equals, tenantId -> false, Map::of);

    // when/then
    assertThat(readiness.anyReady()).isTrue();
  }

  @Test
  void shouldReportAnyReadyWhenAllTenantsAreInitialized() {
    // given
    final var readiness =
        new SchemaInitializationSecondaryStorageReadiness(
            () -> Set.of(TENANT_A, TENANT_B), tenantId -> true, tenantId -> false, Map::of);

    // when/then
    assertThat(readiness.anyReady()).isTrue();
  }

  @Test
  void shouldReportARecoveringTenantAsNotReadyEvenOnceInitialized() {
    // given
    final var readiness =
        new SchemaInitializationSecondaryStorageReadiness(
            () -> Set.of(TENANT_A, TENANT_B), tenantId -> true, TENANT_A::equals, Map::of);

    // when/then
    assertThat(readiness.isRecovering(TENANT_A)).isTrue();
    assertThat(readiness.isReady(TENANT_A)).isFalse();
    assertThat(readiness.isReady(TENANT_B)).isTrue();
  }

  @Test
  void shouldReportDefaultTenantKnownFromPhysicalTenantIdsDefault() {
    // given
    final var readiness =
        new SchemaInitializationSecondaryStorageReadiness(
            PhysicalTenantIds.DEFAULT, tenantId -> false, tenantId -> false, Map::of);

    // when/then
    assertThat(readiness.isReady(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)).isFalse();
    assertThat(readiness.anyReady()).isFalse();
  }

  @Test
  void shouldBeReadyOnceEveryTenantIsInitialized() {
    // given
    final var readiness =
        readinessOf(Map.of(TENANT_A, State.INITIALIZED, TENANT_B, State.INITIALIZED), "");

    // when/then
    assertThat(readiness.nodeReadiness()).isEqualTo(NodeReadiness.READY);
  }

  @ParameterizedTest
  @EnumSource(
      value = State.class,
      mode = Mode.EXCLUDE,
      names = {"INITIALIZED"})
  void shouldBeDegradedWhileOneTenantIsNotInitialized(final State otherTenantState) {
    // given
    final var readiness =
        readinessOf(Map.of(TENANT_A, State.INITIALIZED, TENANT_B, otherTenantState), "");

    // when/then
    assertThat(readiness.nodeReadiness()).isEqualTo(NodeReadiness.DEGRADED);
  }

  @ParameterizedTest
  @EnumSource(
      value = State.class,
      names = {"INITIALIZING", "RETRYING", "RECOVERING"})
  void shouldBeDegradedWhileNoTenantIsInitializedButOneMayStillBe(final State state) {
    // given
    final var readiness = readinessOf(Map.of(TENANT_A, state, TENANT_B, State.FAILED), "");

    // when/then
    assertThat(readiness.nodeReadiness()).isEqualTo(NodeReadiness.DEGRADED);
  }

  @Test
  void shouldNotBeReadyWhenEveryTenantStoppedWithoutBeingInitialized() {
    // given
    final var readiness = readinessOf(Map.of(TENANT_A, State.FAILED, TENANT_B, State.GAVE_UP), "");

    // when/then
    assertThat(readiness.nodeReadiness()).isEqualTo(NodeReadiness.DOWN);
  }

  @Test
  void shouldBeDegradedOnceAnInitializedTenantEntersRecovery() {
    // given
    final var readiness =
        readinessOf(Map.of(TENANT_A, State.INITIALIZED, TENANT_B, State.INITIALIZED), TENANT_A);

    // when/then
    assertThat(readiness.nodeReadiness()).isEqualTo(NodeReadiness.DEGRADED);
  }

  private static SchemaInitializationSecondaryStorageReadiness readinessOf(
      final Map<String, State> states, final String recoveringTenant) {
    return new SchemaInitializationSecondaryStorageReadiness(
        states::keySet,
        tenantId -> states.get(tenantId) == State.INITIALIZED,
        recoveringTenant::equals,
        () -> {
          final var statuses = new LinkedHashMap<String, SchemaInitializationStatus>();
          states.forEach(
              (tenantId, state) ->
                  statuses.put(tenantId, new SchemaInitializationStatus(state, 0, null)));
          return statuses;
        });
  }
}
