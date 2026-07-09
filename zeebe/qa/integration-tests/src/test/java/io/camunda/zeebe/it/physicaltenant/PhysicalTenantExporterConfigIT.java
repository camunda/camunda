/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Verifies that per-physical-tenant exporter configuration is applied at the exporter level.
 *
 * <p>Supported today: an exporter declared in the <b>root</b> configuration can be redeclared per
 * physical tenant under {@code camunda.physical-tenants.<tenantId>.data.exporters.<id>.*} with
 * different properties. The per-tenant declaration must be complete (class-name and all args):
 * partial overrides do not inherit the root entry's remaining fields (Spring's MapBinder replaces
 * the whole map entry; a merge strategy is discussed in a separate issue). Each tenant's partition
 * group then runs the exporter with that tenant's configuration.
 *
 * <p>Not yet supported: declaring an exporter <b>only</b> for a physical tenant (absent from the
 * root config). The initial cluster configuration — which decides which exporter ids are enabled on
 * a partition — is still generated from the root {@code BrokerCfg} only, so a tenant-only exporter
 * is never enabled. See the disabled test below; this will be addressed once the
 * cluster-configuration layer supports per-physical-tenant exporters.
 */
@Timeout(120)
@ZeebeIntegration
final class PhysicalTenantExporterConfigIT {

  private static final String TENANT_A = "tenanta";
  private static final String LOCATION_EXPORTER_ID = "location-exporter";
  private static final String TARGET_ARG = "target";
  private static final String ROOT_TARGET = "root-location";
  private static final String TENANT_A_TARGET = "tenanta-location";

  private static final String TENANT_A_ONLY_EXPORTER_ID = "tenanta-exporter";

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  // The exporter is declared in the ROOT config (so it is enabled on every partition group), and
  // tenantA redeclares it fully with a different "target" arg. The tenantA-only exporter
  // exercises the not-yet-supported case and is used by the disabled test only.
  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS
          .configure(new TestStandaloneBroker().withUnauthenticatedAccess())
          .withExporter(
              LOCATION_EXPORTER_ID,
              cfg -> {
                cfg.setClassName(LocationRecordingExporter.class.getName());
                cfg.setArgs(Map.of(TARGET_ARG, ROOT_TARGET));
              })
          .withPtConfig(
              TENANT_A,
              camunda -> {
                final var exporter = new io.camunda.configuration.Exporter();
                exporter.setClassName(LocationRecordingExporter.class.getName());
                exporter.setArgs(Map.of(TARGET_ARG, TENANT_A_TARGET));
                camunda.getData().getExporters().put(LOCATION_EXPORTER_ID, exporter);

                final var tenantOnlyExporter = new io.camunda.configuration.Exporter();
                tenantOnlyExporter.setClassName(TenantAExporter.class.getName());
                camunda.getData().getExporters().put(TENANT_A_ONLY_EXPORTER_ID, tenantOnlyExporter);
              });

  @AutoClose private CamundaClient tenantAClient;
  @AutoClose private CamundaClient defaultClient;

  @BeforeEach
  void setUp() {
    LocationRecordingExporter.reset();
    TenantAExporter.reset();
    tenantAClient = TENANTS.newClientBuilder(broker, TENANT_A).build();
    defaultClient =
        TENANTS.newClientBuilder(broker, PhysicalTenantsITHelper.DEFAULT_TENANT_ID).build();
  }

  /**
   * Asserts that a root-declared exporter runs on both tenants' partition groups, and that each
   * partition group sees the exporter args of its own tenant: the default tenant exports to the
   * root-configured target, while tenantA exports to the target redeclared under {@code
   * camunda.physical-tenants.tenanta.data.exporters.*}.
   */
  @Test
  void shouldApplyPerTenantExporterArgsOverride() {
    // given — deploy a process to each tenant so records flow through both partition groups
    deployAndCreateInstance(defaultClient, "default-process");
    deployAndCreateInstance(tenantAClient, "tenanta-process");

    // then — the exporter instance on the default partition group was configured with the root
    // target and received records, and the instance on tenantA's partition group was configured
    // with tenantA's overridden target and received records.
    await("exporter receives records under both the root and the tenantA target")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              assertThat(LocationRecordingExporter.RECORDS_BY_TARGET)
                  .containsKey(ROOT_TARGET)
                  .containsKey(TENANT_A_TARGET);
              assertThat(LocationRecordingExporter.RECORDS_BY_TARGET.get(ROOT_TARGET)).isNotEmpty();
              assertThat(LocationRecordingExporter.RECORDS_BY_TARGET.get(TENANT_A_TARGET))
                  .isNotEmpty();
            });

    // and no exporter instance was configured with anything but the two expected targets
    assertThat(LocationRecordingExporter.RECORDS_BY_TARGET.keySet())
        .containsOnly(ROOT_TARGET, TENANT_A_TARGET);
  }

  /**
   * Asserts that an exporter declared only in {@code tenantA}'s config (absent from the root
   * config) is opened and receives records from {@code tenantA}'s partition group.
   *
   * <p>Disabled: the initial cluster configuration that decides which exporter ids are enabled per
   * partition is still generated from the root {@code BrokerCfg} only
   * (StaticConfigurationGenerator), so a tenant-only exporter is never enabled even though it is
   * present in the tenant's ExporterRepository. To be re-enabled once the cluster-configuration
   * layer supports per-physical-tenant exporters.
   */
  @Disabled(
      "Tenant-only exporters require per-physical-tenant exporter support in the"
          + " cluster-configuration layer (initial DynamicPartitionConfig is generated from the"
          + " root BrokerCfg only); tracked in"
          + " https://github.com/camunda/camunda/issues/56652")
  @Test
  void shouldApplyExporterConfiguredOnlyForPhysicalTenant() {
    // given — records flow through tenantA's partition group
    deployAndCreateInstance(tenantAClient, "tenanta-only-process");

    // then — the exporter configured only for tenantA should be opened on tenantA's partition and
    // should receive exported records
    await("tenant-only exporter is opened and receives records from tenantA's partition")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(TenantAExporter.RECORDS).isNotEmpty());
  }

  private static void deployAndCreateInstance(final CamundaClient client, final String processId) {
    final var process = Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();

    // the tenant's Raft group may need a moment to elect a leader after startup
    await("deployment succeeds for " + processId)
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        client
                            .newDeployResourceCommand()
                            .addProcessModel(process, processId + ".bpmn")
                            .send()
                            .join()
                            .getProcesses())
                    .isNotEmpty());

    client.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();
  }

  /**
   * A minimal {@link Exporter} that records exported record positions keyed by the {@code target}
   * arg it was configured with. Static state so assertions can observe it from outside the exporter
   * lifecycle; call {@link #reset()} in {@code @BeforeEach} to isolate test runs.
   */
  public static final class LocationRecordingExporter implements Exporter {

    static final Map<String, CopyOnWriteArrayList<Long>> RECORDS_BY_TARGET =
        new ConcurrentHashMap<>();

    private String target;
    private Controller controller;

    static void reset() {
      RECORDS_BY_TARGET.clear();
    }

    @Override
    public void configure(final Context context) {
      target = String.valueOf(context.getConfiguration().getArguments().get(TARGET_ARG));
    }

    @Override
    public void open(final Controller controller) {
      this.controller = controller;
    }

    @Override
    public void export(final Record<?> record) {
      RECORDS_BY_TARGET
          .computeIfAbsent(target, ignored -> new CopyOnWriteArrayList<>())
          .add(record.getPosition());
      controller.updateLastExportedRecordPosition(record.getPosition());
    }
  }

  /**
   * A minimal {@link Exporter} used by the disabled tenant-only test. Records whether it was opened
   * and collects exported record positions.
   */
  public static final class TenantAExporter implements Exporter {

    static final AtomicBoolean OPENED = new AtomicBoolean(false);
    static final CopyOnWriteArrayList<Long> RECORDS = new CopyOnWriteArrayList<>();

    private Controller controller;

    static void reset() {
      OPENED.set(false);
      RECORDS.clear();
    }

    @Override
    public void configure(final Context context) {}

    @Override
    public void open(final Controller controller) {
      this.controller = controller;
      OPENED.set(true);
    }

    @Override
    public void export(final Record<?> record) {
      RECORDS.add(record.getPosition());
      controller.updateLastExportedRecordPosition(record.getPosition());
    }
  }
}
