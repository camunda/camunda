/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.cluster.PhysicalTenantIds;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SchemaInitializationPhysicalTenantAvailabilityTest {

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";

  @Test
  void shouldDelegateServiceabilityToSchemaInitializedPredicate() {
    // given
    final var availability =
        new SchemaInitializationPhysicalTenantAvailability(
            () -> Set.of(TENANT_A), TENANT_A::equals);

    // when/then
    assertThat(availability.isServiceable(TENANT_A)).isTrue();
    assertThat(availability.isServiceable(TENANT_B)).isFalse();
  }

  @Test
  void shouldReportUnknownTenantAsNotServiceable() {
    // given - mirrors the real schema-init predicates (SchemaManagerContainer,
    // RdbmsSchemaManagerRegistry), which are backed by a per-tenant map and report false for a
    // key they don't hold
    final var availability =
        new SchemaInitializationPhysicalTenantAvailability(
            () -> Set.of(TENANT_A), Set.of(TENANT_A)::contains);

    // when/then
    assertThat(availability.isServiceable("unknown")).isFalse();
  }

  @Test
  void shouldReportNoTenantsServiceableWhenNoneAreInitialized() {
    // given
    final var availability =
        new SchemaInitializationPhysicalTenantAvailability(
            () -> Set.of(TENANT_A, TENANT_B), tenantId -> false);

    // when/then
    assertThat(availability.anyServiceable()).isFalse();
  }

  @Test
  void shouldReportAnyServiceableWhenSomeTenantsAreInitialized() {
    // given
    final var availability =
        new SchemaInitializationPhysicalTenantAvailability(
            () -> Set.of(TENANT_A, TENANT_B), TENANT_A::equals);

    // when/then
    assertThat(availability.anyServiceable()).isTrue();
  }

  @Test
  void shouldReportAnyServiceableWhenAllTenantsAreInitialized() {
    // given
    final var availability =
        new SchemaInitializationPhysicalTenantAvailability(
            () -> Set.of(TENANT_A, TENANT_B), tenantId -> true);

    // when/then
    assertThat(availability.anyServiceable()).isTrue();
  }

  @Test
  void shouldReportDefaultTenantKnownFromPhysicalTenantIdsDefault() {
    // given
    final var availability =
        new SchemaInitializationPhysicalTenantAvailability(
            PhysicalTenantIds.DEFAULT, tenantId -> false);

    // when/then
    assertThat(availability.isServiceable(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)).isFalse();
    assertThat(availability.anyServiceable()).isFalse();
  }
}
