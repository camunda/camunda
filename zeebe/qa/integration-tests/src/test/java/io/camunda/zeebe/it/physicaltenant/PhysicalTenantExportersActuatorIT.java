/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import feign.FeignException;
import io.camunda.zeebe.management.cluster.ExporterStatus;
import io.camunda.zeebe.management.cluster.ExporterStatus.StatusEnum;
import io.camunda.zeebe.qa.util.actuator.ExportersActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Verifies that the {@code exporters} actuator's {@code physicalTenant} query parameter scopes both
 * the listing and the mutations to one physical tenant's partition group, while omitting it keeps
 * the whole-cluster meaning the operations always had.
 *
 * <p>The exporter is declared once in the root configuration, so both physical tenants start with
 * the same exporter id enabled — which is exactly the case the parameter exists for: the same id
 * can be in a different state in each tenant, so a status that is not attributed to a tenant would
 * belong to neither.
 */
@Timeout(2 * 60)
@ZeebeIntegration
final class PhysicalTenantExportersActuatorIT {

  private static final String TENANT_A = "tenanta";
  private static final String DEFAULT_TENANT = PhysicalTenantsITHelper.DEFAULT_TENANT_ID;
  private static final String EXPORTER_ID = "recordingExporter";

  // both tenants run broker-only (no secondary storage); declaring tenant A starts a second,
  // fully isolated partition group for it
  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(DEFAULT_TENANT, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS.configure(
          new TestStandaloneBroker().withUnauthenticatedAccess().withRecordingExporter(true));

  private ExportersActuator actuator;

  @BeforeEach
  void beforeEach() {
    actuator = ExportersActuator.of(broker);
    // a tenant's partition group may need a moment to elect a leader after startup, and the
    // exporter state is only reported once its partition is up
    await("every physical tenant reports its exporter")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(actuator.getExporters())
                    .extracting(ExporterStatus::getPhysicalTenant, ExporterStatus::getExporterId)
                    .containsExactlyInAnyOrder(
                        tuple(DEFAULT_TENANT, EXPORTER_ID), tuple(TENANT_A, EXPORTER_ID)));
  }

  @Test
  void shouldListExportersOfEveryPhysicalTenant() {
    // when
    final var exporters = actuator.getExporters();

    // then - one entry per tenant, each attributed to the tenant it belongs to
    assertThat(exporters)
        .extracting(
            ExporterStatus::getPhysicalTenant,
            ExporterStatus::getExporterId,
            ExporterStatus::getStatus)
        .containsExactlyInAnyOrder(
            tuple(DEFAULT_TENANT, EXPORTER_ID, StatusEnum.ENABLED),
            tuple(TENANT_A, EXPORTER_ID, StatusEnum.ENABLED));
  }

  @Test
  void shouldListExportersOfOnePhysicalTenantOnly() {
    // when
    final var exporters = actuator.getExporters(TENANT_A);

    // then
    assertThat(exporters)
        .extracting(ExporterStatus::getPhysicalTenant, ExporterStatus::getExporterId)
        .containsExactly(tuple(TENANT_A, EXPORTER_ID));
  }

  @Test
  void shouldRejectAnUnknownPhysicalTenant() {
    // when - then
    assertThatThrownBy(() -> actuator.getExporters("unknowntenant"))
        .isInstanceOf(FeignException.NotFound.class);
  }

  @Test
  void shouldDisableExporterOnTargetedPhysicalTenantOnly() {
    // when
    actuator.disableExporter(EXPORTER_ID, TENANT_A);

    // then - tenant A's exporter is disabled while the default tenant keeps exporting
    await("only tenant A's exporter is disabled")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                assertThat(statuses())
                    .containsExactlyInAnyOrder(
                        tuple(DEFAULT_TENANT, StatusEnum.ENABLED),
                        tuple(TENANT_A, StatusEnum.DISABLED)));
  }

  @Test
  void shouldDisableExporterOnEveryPhysicalTenantWhenNoneIsGiven() {
    // when
    actuator.disableExporter(EXPORTER_ID);

    // then - the whole-cluster meaning of a disable is kept
    await("every physical tenant's exporter is disabled")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                assertThat(statuses())
                    .containsExactlyInAnyOrder(
                        tuple(DEFAULT_TENANT, StatusEnum.DISABLED),
                        tuple(TENANT_A, StatusEnum.DISABLED)));
  }

  @Test
  void shouldEnableExporterOnTargetedPhysicalTenantOnly() {
    // given - the exporter disabled everywhere
    actuator.disableExporter(EXPORTER_ID);
    await("every physical tenant's exporter is disabled")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                assertThat(statuses())
                    .containsExactlyInAnyOrder(
                        tuple(DEFAULT_TENANT, StatusEnum.DISABLED),
                        tuple(TENANT_A, StatusEnum.DISABLED)));

    // when
    actuator.enableExporterForTenant(EXPORTER_ID, TENANT_A);

    // then - only tenant A exports again
    await("only tenant A's exporter is enabled")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                assertThat(statuses())
                    .containsExactlyInAnyOrder(
                        tuple(DEFAULT_TENANT, StatusEnum.DISABLED),
                        tuple(TENANT_A, StatusEnum.ENABLED)));
  }

  /** The (physical tenant, status) of every exporter in the cluster. */
  private List<org.assertj.core.groups.Tuple> statuses() {
    return actuator.getExporters().stream()
        .map(status -> tuple(status.getPhysicalTenant(), status.getStatus()))
        .toList();
  }
}
