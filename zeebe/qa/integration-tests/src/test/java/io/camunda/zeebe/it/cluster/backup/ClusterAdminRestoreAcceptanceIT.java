/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.Data;
import io.camunda.configuration.PrimaryStorageBackup;
import io.camunda.configuration.PrimaryStorageBackup.BackupStoreType;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.awaitility.core.ThrowingRunnable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Acceptance test for restoring in-place over the cluster-admin API ({@code POST
 * cluster/v2/restore}), as opposed to {@link InProcessRestoreAcceptance} which restores over the
 * per-physical-tenant API ({@code POST v2/restore}).
 *
 * <p>One broker serves three physical tenants — {@code default}, {@code tenantb} and {@code
 * tenantc} — each its own partition group with its own primary storage backup store. No secondary
 * storage is needed: backup/restore acts on primary storage only, independently of it (see {@code
 * ClusterRecoveryServicesTest} in the {@code service} module for coverage of the per-tenant
 * secondary-storage environment that {@code overrides} coexists with).
 *
 * <p>Covers the three shapes {@link
 * io.camunda.zeebe.dynamic.config.api.ClusterRestoreRequestTransformer} supports: a request naming
 * one physical tenant, forcing the restore onto just that tenant while the rest of the cluster is
 * left alone; a request naming every known physical tenant of the cluster — the shape {@code
 * ClusterRecoveryServices} builds when the request omits {@code physicalTenantId} — restoring them
 * all from the same backup in one change; and that same cluster-wide shape with one tenant's backup
 * selection overridden to a different, additional backup, proving the override reaches exactly the
 * tenant it names and nowhere else.
 */
@Timeout(240)
@ZeebeIntegration
final class ClusterAdminRestoreAcceptanceIT {

  private static final String DEFAULT_TENANT = PhysicalTenantsITHelper.DEFAULT_TENANT_ID;
  private static final String TENANT_B = "tenantb";
  private static final String TENANT_C = "tenantc";

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @TempDir private static Path defaultBackupDir;
  @TempDir private static Path tenantBBackupDir;
  @TempDir private static Path tenantCBackupDir;

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(DEFAULT_TENANT, Storage.none())
          .withTenant(TENANT_B, Storage.none())
          .withTenant(TENANT_C, Storage.none())
          .build();

  @TestZeebe
  private final TestStandaloneBroker broker =
      configureBackupStores(
          TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess()));

  @Test
  void shouldRestoreOnlyTheForcedPhysicalTenant() {
    try (final var defaultClient = TENANTS.newClientBuilder(broker, DEFAULT_TENANT).build();
        final var tenantBClient = TENANTS.newClientBuilder(broker, TENANT_B).build()) {
      final var processId = "forced-restore-process";
      final var jobType = "forced-restore-job";
      final var probeProcessId = "forced-restore-probe";

      // given — the default tenant keeps processing throughout; only tenant-b gets a backup
      deployProbeProcess(defaultClient, probeProcessId);
      deployProbeProcess(tenantBClient, probeProcessId);
      deployAndCreateInstance(defaultClient, processId, jobType);
      deployAndCreateInstance(tenantBClient, processId, jobType);
      takeSnapshot(TENANT_B);
      takeBackup(TENANT_B, 41);

      // when — only tenant-b is put into RECOVERING, scoped by physicalTenantId
      InProcessRestoreTestUtil.changeClusterMode(defaultClient, TENANT_B, "RECOVERING", false);
      awaitCommandsRejected(tenantBClient, probeProcessId);

      // and — the default tenant is left untouched by the scoped mode change
      assertThat(createInstance(defaultClient, probeProcessId)).isPositive();

      // and — the restore is forced onto tenant-b alone. The mode change above may not have fully
      // settled yet even though tenant-b's commands are already rejected (rejection can start
      // before the configuration change that caused it is marked complete), so a transient 409
      // while it clears is retried rather than failing outright.
      awaitRestoreTriggered(
          () -> InProcessRestoreTestUtil.triggerClusterRestore(defaultClient, TENANT_B, 41));

      // then — tenant-b processes commands again once its restore completes, and its baseline job
      // is still there to complete, proving partition data (not just topology/mode) was restored
      awaitCommandsAccepted(tenantBClient, probeProcessId);
      activateAndComplete(tenantBClient, jobType);

      // and — the default tenant was never affected by the other tenant's restore
      assertThat(createInstance(defaultClient, probeProcessId)).isPositive();
    }
  }

  @Test
  void shouldRestoreEveryPhysicalTenantOfTheClusterWhenNoneIsNamed() {
    try (final var defaultClient = TENANTS.newClientBuilder(broker, DEFAULT_TENANT).build();
        final var tenantBClient = TENANTS.newClientBuilder(broker, TENANT_B).build();
        final var tenantCClient = TENANTS.newClientBuilder(broker, TENANT_C).build()) {
      final var processId = "cluster-wide-restore-process";
      final var jobType = "cluster-wide-restore-job";
      final var probeProcessId = "cluster-wide-restore-probe";
      final var backupId = 42;

      // given — every physical tenant has a pending job, backed up from the same selection
      deployProbeProcess(defaultClient, probeProcessId);
      deployProbeProcess(tenantBClient, probeProcessId);
      deployProbeProcess(tenantCClient, probeProcessId);
      deployAndCreateInstance(defaultClient, processId, jobType);
      deployAndCreateInstance(tenantBClient, processId, jobType);
      deployAndCreateInstance(tenantCClient, processId, jobType);
      takeSnapshot(DEFAULT_TENANT);
      takeSnapshot(TENANT_B);
      takeSnapshot(TENANT_C);
      takeBackup(DEFAULT_TENANT, backupId);
      takeBackup(TENANT_B, backupId);
      takeBackup(TENANT_C, backupId);

      // when — the whole cluster is put into RECOVERING, no physicalTenantId naming every tenant
      InProcessRestoreTestUtil.changeClusterMode(defaultClient, null, "RECOVERING", false);
      awaitCommandsRejected(defaultClient, probeProcessId);
      awaitCommandsRejected(tenantBClient, probeProcessId);
      awaitCommandsRejected(tenantCClient, probeProcessId);

      // and — a cluster-wide restore is triggered, no overrides: every tenant shares the selection
      awaitRestoreTriggered(
          () -> InProcessRestoreTestUtil.triggerClusterRestore(defaultClient, backupId, Map.of()));

      // then — every tenant processes commands again once its restore completes
      awaitCommandsAccepted(defaultClient, probeProcessId);
      awaitCommandsAccepted(tenantBClient, probeProcessId);
      awaitCommandsAccepted(tenantCClient, probeProcessId);

      // and — every tenant's baseline job is still there to complete, proving partition data (not
      // just topology/mode) was restored on all three
      activateAndComplete(defaultClient, jobType);
      activateAndComplete(tenantBClient, jobType);
      activateAndComplete(tenantCClient, jobType);
    }
  }

  @Test
  void shouldRestoreTheOverriddenTenantFromItsOwnBackupWhileOthersShareTheDefault() {
    try (final var defaultClient = TENANTS.newClientBuilder(broker, DEFAULT_TENANT).build();
        final var tenantBClient = TENANTS.newClientBuilder(broker, TENANT_B).build();
        final var tenantCClient = TENANTS.newClientBuilder(broker, TENANT_C).build()) {
      final var baselineProcessId = "override-it-baseline-process";
      final var baselineJobType = "override-it-baseline-job";
      final var overrideProcessId = "override-it-override-process";
      final var overrideJobType = "override-it-override-job";
      final var probeProcessId = "override-it-probe";
      final long baselineBackupId = 100;
      final long overrideBackupId = 200;

      // given — every physical tenant has a pending job from the baseline process
      deployProbeProcess(defaultClient, probeProcessId);
      deployProbeProcess(tenantBClient, probeProcessId);
      deployProbeProcess(tenantCClient, probeProcessId);
      deployAndCreateInstance(defaultClient, baselineProcessId, baselineJobType);
      deployAndCreateInstance(tenantBClient, baselineProcessId, baselineJobType);
      deployAndCreateInstance(tenantCClient, baselineProcessId, baselineJobType);

      // and — a completed baseline backup on all three tenants
      takeSnapshot(DEFAULT_TENANT);
      takeSnapshot(TENANT_B);
      takeSnapshot(TENANT_C);
      takeBackup(DEFAULT_TENANT, baselineBackupId);
      takeBackup(TENANT_B, baselineBackupId);
      takeBackup(TENANT_C, baselineBackupId);

      // and — tenant-c alone completes its baseline job and moves on to different work, then takes
      // an additional backup capturing that later state
      activateAndComplete(tenantCClient, baselineJobType);
      deployAndCreateInstance(tenantCClient, overrideProcessId, overrideJobType);
      takeSnapshot(TENANT_C);
      takeBackup(TENANT_C, overrideBackupId);

      // when — the whole cluster is put into RECOVERING over the cluster-admin endpoint ...
      InProcessRestoreTestUtil.changeClusterMode(defaultClient, null, "RECOVERING", false);
      awaitCommandsRejected(defaultClient, probeProcessId);
      awaitCommandsRejected(tenantBClient, probeProcessId);
      awaitCommandsRejected(tenantCClient, probeProcessId);

      // ... and a cluster-wide restore is triggered: tenant-c overridden to its own, additional
      // backup, default and tenant-b left on the top-level (baseline) selection
      awaitRestoreTriggered(
          () ->
              InProcessRestoreTestUtil.triggerClusterRestore(
                  defaultClient, baselineBackupId, Map.of(TENANT_C, overrideBackupId)));

      // then — every tenant processes commands again once its restore completes
      awaitCommandsAccepted(defaultClient, probeProcessId);
      awaitCommandsAccepted(tenantBClient, probeProcessId);
      awaitCommandsAccepted(tenantCClient, probeProcessId);

      // and — default and tenant-b restored from the baseline backup: the baseline job is still
      // there to complete
      activateAndComplete(defaultClient, baselineJobType);
      activateAndComplete(tenantBClient, baselineJobType);

      // and — tenant-c restored from its own overridden backup, not the baseline: the baseline job
      // it had already completed before that backup was taken never reappears, but the job the
      // override backup captured does
      assertThat(activatedJobTypes(tenantCClient, baselineJobType)).isEmpty();
      activateAndComplete(tenantCClient, overrideJobType);
    }
  }

  /**
   * Deploys the process the readiness probes create instances of: it carries no service task, so a
   * probe instance leaves no job behind. The processes whose pending jobs later prove that
   * backed-up partition data was restored must not be the ones probed for readiness — a probe
   * instance of such a process would mint a fresh job of the asserted type, letting the job
   * assertions pass off work the probe itself created rather than work the backup captured.
   *
   * <p>Deploy it before the backup is taken, so the restored state still knows it.
   */
  private static void deployProbeProcess(final CamundaClient client, final String processId) {
    deploy(
        client, processId, Bpmn.createExecutableProcess(processId).startEvent().endEvent().done());
  }

  /**
   * Deploys a single-service-task process and creates one instance, leaving one job of the given
   * type pending.
   */
  private static void deployAndCreateInstance(
      final CamundaClient client, final String processId, final String jobType) {
    deploy(
        client,
        processId,
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(jobType))
            .endEvent()
            .done());
    Awaitility.await("instance of " + processId + " is created")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(() -> assertThat(createInstance(client, processId)).isPositive());
  }

  /** Deploys a process, idempotent under retry so a transient rejection is simply retried. */
  private static void deploy(
      final CamundaClient client, final String processId, final BpmnModelInstance process) {
    Awaitility.await("process " + processId + " is deployed")
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
  }

  /** Activates at least one job of the given type and completes every job it finds. */
  private static void activateAndComplete(final CamundaClient client, final String jobType) {
    Awaitility.await("a job of type " + jobType + " is activated and completed")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var jobs =
                  client
                      .newActivateJobsCommand()
                      .jobType(jobType)
                      .maxJobsToActivate(1)
                      .send()
                      .join()
                      .getJobs();
              assertThat(jobs).isNotEmpty();
              jobs.forEach(job -> client.newCompleteCommand(job.getKey()).send().join());
            });
  }

  /** Activates up to one job of the given type, for a negative "it isn't there" assertion. */
  private static List<String> activatedJobTypes(final CamundaClient client, final String jobType) {
    return client
        .newActivateJobsCommand()
        .jobType(jobType)
        .maxJobsToActivate(1)
        .send()
        .join()
        .getJobs()
        .stream()
        .map(job -> job.getType())
        .toList();
  }

  private static long createInstance(final CamundaClient client, final String processId) {
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .send()
        .join()
        .getProcessInstanceKey();
  }

  private static void awaitCommandsRejected(final CamundaClient client, final String processId) {
    Awaitility.await("recovering tenant stops accepting commands")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                assertThatThrownBy(() -> createInstance(client, processId))
                    .isInstanceOf(Exception.class));
  }

  private static void awaitCommandsAccepted(final CamundaClient client, final String processId) {
    Awaitility.await("restored tenant accepts commands again")
        .atMost(Duration.ofMinutes(2))
        .ignoreExceptions()
        .untilAsserted(() -> assertThat(createInstance(client, processId)).isPositive());
  }

  /**
   * Retries the given restore trigger until it is accepted. The mode change preceding it may not
   * have fully settled yet even though the affected tenant's commands are already rejected —
   * rejection can start before the configuration change that caused it is marked complete — so a
   * transient 409 (another configuration change still in progress) is retried rather than failing
   * outright. Safe to retry: nothing has started until the trigger is actually accepted.
   */
  private static void awaitRestoreTriggered(final ThrowingRunnable trigger) {
    Awaitility.await("cluster-admin restore is accepted once the prior change clears")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(trigger);
  }

  /**
   * Takes a snapshot on every partition of the tenant and waits for it to be persisted.
   * Snapshotting is asynchronous, and a tenant may already carry a snapshot from an earlier call
   * here, so the wait is for each partition's snapshot ID to <em>change</em>: merely waiting for a
   * non-null ID would return immediately on the second call and let the backup be taken before the
   * work it is meant to capture is snapshotted.
   */
  private void takeSnapshot(final String tenantId) {
    final var partitions = PartitionsActuator.of(broker);
    final var previousSnapshotIds = new HashMap<Integer, String>();
    partitions
        .query(tenantId)
        .forEach((id, status) -> previousSnapshotIds.put(id, status.snapshotId()));
    partitions.takeSnapshot(tenantId);
    Awaitility.await("a new snapshot is taken for tenant " + tenantId)
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                assertThat(partitions.query(tenantId))
                    .allSatisfy(
                        (partitionId, status) ->
                            assertThat(status.snapshotId())
                                .isNotNull()
                                .isNotEqualTo(previousSnapshotIds.get(partitionId))));
  }

  private void takeBackup(final String tenantId, final long backupId) {
    final var uri = backupsUri(tenantId);
    final var body = "{\"backupId\": " + backupId + "}";
    final var request =
        HttpRequest.newBuilder(uri)
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(body))
            .build();
    assertThat(send(request).statusCode())
        .describedAs("take backup %d for tenant %s", backupId, tenantId)
        .isEqualTo(202);

    Awaitility.await("backup %d for tenant %s completes".formatted(backupId, tenantId))
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // 404 NOT_FOUND until the backup is registered
        .untilAsserted(
            () -> {
              final var status =
                  send(HttpRequest.newBuilder(URI.create(uri + "/" + backupId)).GET().build());
              assertThat(status.statusCode()).isEqualTo(200);
              assertThat(OBJECT_MAPPER.readTree(status.body()).path("state").asText())
                  .isEqualTo("COMPLETED");
            });
  }

  private URI backupsUri(final String tenantId) {
    final var base = broker.restAddress().toString().replaceAll("/+$", "");
    return URI.create(
        DEFAULT_TENANT.equals(tenantId)
            ? base + "/v2/backups/runtime"
            : base + "/physical-tenants/" + tenantId + "/v2/backups/runtime");
  }

  private static HttpResponse<String> send(final HttpRequest request) {
    try {
      return HTTP.send(request, BodyHandlers.ofString());
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }

  private static TestStandaloneBroker configureBackupStores(final TestStandaloneBroker broker) {
    return broker
        .withDataConfig(ClusterAdminRestoreAcceptanceIT::configureFilesystemBackup)
        .withPtConfig(
            TENANT_B, camunda -> configureFilesystemBackupForTenant(camunda, tenantBBackupDir))
        .withPtConfig(
            TENANT_C, camunda -> configureFilesystemBackupForTenant(camunda, tenantCBackupDir));
  }

  private static void configureFilesystemBackup(final Data data) {
    final var backup = data.getPrimaryStorage().getBackup();
    backup.setStore(BackupStoreType.FILESYSTEM);
    backup.getFilesystem().setBasePath(defaultBackupDir.toAbsolutePath().toString());
  }

  private static void configureFilesystemBackupForTenant(
      final Camunda camunda, final Path backupDir) {
    final PrimaryStorageBackup backup = camunda.getData().getPrimaryStorage().getBackup();
    backup.setStore(BackupStoreType.FILESYSTEM);
    backup.getFilesystem().setBasePath(backupDir.toAbsolutePath().toString());
  }
}
