/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker.RECORDING_EXPORTER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import feign.FeignException;
import io.camunda.zeebe.management.cluster.ExporterStatus.StatusEnum;
import io.camunda.zeebe.qa.util.actuator.ExportersActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that deleting an exporter through the {@code exporters} actuator is scoped by the {@code
 * physicalTenant} query parameter: the exporter is removed from the named tenant's partition group
 * only, and omitting the parameter removes it from every tenant.
 *
 * <p>Delete needs its own fixture, which is why it is not part of {@link
 * PhysicalTenantExportersActuatorIT}: an exporter can only be deleted once it is gone from the
 * application configuration ({@code CONFIG_NOT_FOUND}), which takes a restart with a changed
 * configuration and therefore a working directory that survives it. Removing the exporter from the
 * <em>root</em> configuration puts every tenant into that state at once — narrowing a root-declared
 * exporter out of a single tenant's configuration is not supported yet (<a
 * href="https://github.com/camunda/camunda/issues/56652">#56652</a>) — which is all this needs:
 * what is under test is that the delete <em>operation</em> is scoped, not how the precondition was
 * reached.
 */
@Timeout(2 * 60)
@ZeebeIntegration
final class PhysicalTenantExporterDeleteIT {

  private static final String TENANT_A = "tenanta";
  private static final String DEFAULT_TENANT = PhysicalTenantsITHelper.DEFAULT_TENANT_ID;

  // both tenants run broker-only (no secondary storage); declaring tenant A starts a second,
  // fully isolated partition group for it
  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(DEFAULT_TENANT, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  // started by the test rather than the extension: the working directory has to be set from the
  // injected @TempDir first, so the persisted cluster configuration survives the restart below.
  // Without it the restarted broker would generate its configuration afresh and the exporter would
  // simply be absent instead of CONFIG_NOT_FOUND.
  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private final TestStandaloneBroker broker =
      TENANTS.configure(
          new TestStandaloneBroker().withUnauthenticatedAccess().withRecordingExporter(true));

  @TempDir private Path workingDirectory;

  private ExportersActuator actuator;

  @BeforeEach
  void beforeEach() {
    broker.withWorkingDirectory(workingDirectory).start();
    actuator = ExportersActuator.of(broker);

    // a tenant's partition group may need a moment to elect a leader after startup, and the
    // exporter state is only reported once its partition is up
    await("every physical tenant reports its exporter as enabled")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(statuses())
                    .containsExactlyInAnyOrder(
                        tuple(DEFAULT_TENANT, StatusEnum.ENABLED),
                        tuple(TENANT_A, StatusEnum.ENABLED)));
  }

  @Test
  void shouldDeleteExporterFromTargetedPhysicalTenantOnly() {
    // given - the exporter gone from the configuration, so both tenants allow deleting it
    restartWithoutExporter();

    // when
    actuator.deleteExporter(RECORDING_EXPORTER_ID, TENANT_A);

    // then - tenant A no longer has the exporter at all, while the default tenant still reports it
    await("only tenant A's exporter is deleted")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                assertThat(statuses())
                    .containsExactly(tuple(DEFAULT_TENANT, StatusEnum.CONFIG_NOT_FOUND)));
  }

  @Test
  void shouldDeleteExporterFromEveryPhysicalTenantWhenNoneIsGiven() {
    // given - the exporter gone from the configuration, so both tenants allow deleting it
    restartWithoutExporter();

    // when
    actuator.deleteExporter(RECORDING_EXPORTER_ID);

    // then - the whole-cluster meaning of a delete is kept
    await("every physical tenant's exporter is deleted")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(() -> assertThat(actuator.getExporters()).isEmpty());
  }

  @Test
  void shouldRejectDeletingFromAPhysicalTenantThatStillConfiguresTheExporter() {
    // given - no restart, so the exporter is still configured and enabled in both tenants

    // when - then
    assertThatThrownBy(() -> actuator.deleteExporter(RECORDING_EXPORTER_ID, TENANT_A))
        .isInstanceOf(FeignException.class)
        .extracting(error -> ((FeignException) error).status())
        .isEqualTo(400);

    // and - the rejected request left both tenants untouched
    assertThat(statuses())
        .containsExactlyInAnyOrder(
            tuple(DEFAULT_TENANT, StatusEnum.ENABLED), tuple(TENANT_A, StatusEnum.ENABLED));
  }

  /**
   * Restarts the broker with the exporter removed from the root configuration, and waits until
   * every tenant reports it as {@code CONFIG_NOT_FOUND} - the only state a delete is accepted in.
   */
  private void restartWithoutExporter() {
    broker.stop();
    broker.withRecordingExporter(false);
    // tenant A's exporters-assigned[0]=recordingExporter (set by TENANTS.configure() while the
    // exporter was still present) would otherwise go stale and trip the unknown-exporter-id check
    // now that the exporter is gone from the root catalog
    TENANTS.refreshExportersAssigned(broker);
    broker.start();

    await("every physical tenant reports the exporter as CONFIG_NOT_FOUND")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(statuses())
                    .containsExactlyInAnyOrder(
                        tuple(DEFAULT_TENANT, StatusEnum.CONFIG_NOT_FOUND),
                        tuple(TENANT_A, StatusEnum.CONFIG_NOT_FOUND)));
  }

  /** The (physical tenant, status) of every exporter in the cluster. */
  private List<Tuple> statuses() {
    return actuator.getExporters().stream()
        .map(status -> tuple(status.getPhysicalTenant(), status.getStatus()))
        .toList();
  }
}
