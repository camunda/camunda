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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.protocol.rest.ClusterRestoreRequest;
import io.camunda.client.protocol.rest.RestoreRequest;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.Data;
import io.camunda.configuration.PrimaryStorageBackup;
import io.camunda.configuration.PrimaryStorageBackup.BackupStoreType;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Acceptance test for restoring in-place over the cluster-admin API ({@code POST
 * cluster/v2/restore}), as opposed to {@link InProcessRestoreAcceptance} which restores over the
 * per-physical-tenant API ({@code POST v2/restore}).
 *
 * <p>Runs on a three-broker cluster serving three physical tenants — {@code default}, {@code
 * tenantb} and {@code tenantc} — each its own partition group of three partitions replicated across
 * every broker, and each with its own primary storage backup store. That shape is deliberate: it is
 * the smallest cluster in which all three parallel axes of a restore are non-trivial at once, so a
 * cluster-wide restore plans 3 tenants × 3 brokers × 3 partitions of partition work rather than
 * collapsing one of them to a single element. No secondary storage is needed: backup/restore acts
 * on primary storage only, independently of it (see {@code ClusterRecoveryServicesTest} in the
 * {@code service} module for coverage of the per-tenant secondary-storage environment that {@code
 * overrides} coexists with).
 *
 * <p>Covers the three shapes {@link
 * io.camunda.zeebe.dynamic.config.api.ClusterRestoreRequestTransformer} supports: a request naming
 * one physical tenant, forcing the restore onto just that tenant while the rest of the cluster is
 * left alone; a request naming every known physical tenant of the cluster — the shape {@code
 * ClusterRecoveryServices} builds when the request omits {@code physicalTenantId} — restoring them
 * all from the same backup in one change; and that same cluster-wide shape with one tenant's backup
 * selection overridden to a different, additional backup, proving the override reaches exactly the
 * tenant it names and nowhere else.
 *
 * <h4>What the parallelism assertions here do and do not establish</h4>
 *
 * <p>Each scenario asserts that the accepted plan covers every broker and every partition of every
 * tenant it targets, in a single configuration change — see {@link #assertPlanCovers(HttpResponse,
 * Set)}. That is what proves the restore is planned across all three axes rather than, say, per
 * broker in sequence.
 *
 * <p>It does not prove the operations then <em>overlap</em> in time. The cluster-admin API carries
 * no per-operation timestamps, so overlap cannot be asserted through it. Overlap is covered where
 * it is observable: {@code RestoreRequestTransformerTest} pins the dependency edges that permit it,
 * and {@code ClusterConfigurationManagerImplTest} pins that a broker starts every runnable
 * operation rather than one per round.
 */
@Timeout(600)
@ZeebeIntegration
final class ClusterAdminRestoreAcceptanceIT {

  private static final String DEFAULT_TENANT = PhysicalTenantsITHelper.DEFAULT_TENANT_ID;
  private static final String TENANT_B = "tenantb";
  private static final String TENANT_C = "tenantc";
  private static final Set<String> ALL_TENANTS = Set.of(DEFAULT_TENANT, TENANT_B, TENANT_C);

  private static final int BROKERS_COUNT = 3;
  private static final int PARTITIONS_COUNT = 3;

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
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(BROKERS_COUNT)
          .withPartitionsCount(PARTITIONS_COUNT)
          .withReplicationFactor(BROKERS_COUNT)
          .withEmbeddedGateway(true)
          .withBrokerConfig(
              broker ->
                  configureBackupStores(TENANTS.configure(broker.withUnauthenticatedAccess())))
          .build();

  @Test
  void shouldRestoreOnlyTheForcedPhysicalTenant() {
    try (final var defaultClient = newClient(DEFAULT_TENANT);
        final var tenantBClient = newClient(TENANT_B)) {
      final var processId = "forced-restore-process";
      final var jobType = "forced-restore-job";
      final var probeProcessId = "forced-restore-probe";
      final long backupId = 41;

      // given — the default tenant keeps processing throughout; only tenant-b gets a backup
      deployProbeProcess(defaultClient, probeProcessId);
      deployProbeProcess(tenantBClient, probeProcessId);
      createInstancesOnEveryPartition(defaultClient, processId, jobType);
      createInstancesOnEveryPartition(tenantBClient, processId, jobType);
      takeSnapshot(TENANT_B);
      takeBackup(TENANT_B, backupId);

      // when — only tenant-b is put into RECOVERING, scoped by physicalTenantId
      InProcessRestoreTestUtil.changeClusterMode(defaultClient, TENANT_B, "RECOVERING", false);
      awaitCommandsRejected(tenantBClient, probeProcessId);

      // and — the default tenant is left untouched by the scoped mode change
      assertThat(createInstance(defaultClient, probeProcessId)).isPositive();

      // and — the restore is forced onto tenant-b alone
      final var response =
          awaitRestoreAccepted(
              () ->
                  InProcessRestoreTestUtil.sendClusterRestoreRequest(
                      defaultClient, TENANT_B, restoreBody(backupId)));

      // then — the plan targets tenant-b alone, but within it every broker and every partition, so
      // the scoping narrows the tenant axis without serialising the other two
      assertPlanCovers(response, Set.of(TENANT_B));

      // and — tenant-b processes commands again once its restore completes, and the baseline jobs
      // of
      // every partition are still there, proving partition data (not just topology/mode) came back
      awaitCommandsAccepted(tenantBClient, probeProcessId);
      completeJobsFromEveryPartition(tenantBClient, jobType);

      // and — the default tenant was never affected by the other tenant's restore
      assertThat(createInstance(defaultClient, probeProcessId)).isPositive();
    }
  }

  @Test
  void shouldRestoreEveryPhysicalTenantOfTheClusterWhenNoneIsNamed() {
    try (final var defaultClient = newClient(DEFAULT_TENANT);
        final var tenantBClient = newClient(TENANT_B);
        final var tenantCClient = newClient(TENANT_C)) {
      final var processId = "cluster-wide-restore-process";
      final var jobType = "cluster-wide-restore-job";
      final var probeProcessId = "cluster-wide-restore-probe";
      final long backupId = 42;

      // given — every physical tenant has a pending job on every partition, backed up from the same
      // selection
      for (final var client : List.of(defaultClient, tenantBClient, tenantCClient)) {
        deployProbeProcess(client, probeProcessId);
        createInstancesOnEveryPartition(client, processId, jobType);
      }
      ALL_TENANTS.forEach(this::takeSnapshot);
      ALL_TENANTS.forEach(tenant -> takeBackup(tenant, backupId));

      // when — the whole cluster is put into RECOVERING, no physicalTenantId naming every tenant
      InProcessRestoreTestUtil.changeClusterMode(defaultClient, null, "RECOVERING", false);
      awaitCommandsRejected(defaultClient, probeProcessId);
      awaitCommandsRejected(tenantBClient, probeProcessId);
      awaitCommandsRejected(tenantCClient, probeProcessId);

      // and — a cluster-wide restore is triggered, no overrides: every tenant shares the selection
      final var response =
          awaitRestoreAccepted(
              () ->
                  InProcessRestoreTestUtil.sendClusterRestoreRequest(
                      defaultClient, null, restoreBody(backupId)));

      // then — one change plans the restore of all three tenants, each across every broker and
      // every
      // partition: 3 × 3 × 3 of partition work in a single change
      assertPlanCovers(response, ALL_TENANTS);

      // and — every tenant processes commands again once its restore completes
      awaitCommandsAccepted(defaultClient, probeProcessId);
      awaitCommandsAccepted(tenantBClient, probeProcessId);
      awaitCommandsAccepted(tenantCClient, probeProcessId);

      // and — every partition of every tenant has its baseline job back, proving partition data
      // (not
      // just topology/mode) was restored on all nine partitions
      completeJobsFromEveryPartition(defaultClient, jobType);
      completeJobsFromEveryPartition(tenantBClient, jobType);
      completeJobsFromEveryPartition(tenantCClient, jobType);
    }
  }

  @Test
  void shouldRestoreTheOverriddenTenantFromItsOwnBackupWhileOthersShareTheDefault() {
    try (final var defaultClient = newClient(DEFAULT_TENANT);
        final var tenantBClient = newClient(TENANT_B);
        final var tenantCClient = newClient(TENANT_C)) {
      final var baselineProcessId = "override-it-baseline-process";
      final var baselineJobType = "override-it-baseline-job";
      final var overrideProcessId = "override-it-override-process";
      final var overrideJobType = "override-it-override-job";
      final var probeProcessId = "override-it-probe";
      final long baselineBackupId = 100;
      final long overrideBackupId = 200;

      // given — every physical tenant has a pending baseline job on every partition
      for (final var client : List.of(defaultClient, tenantBClient, tenantCClient)) {
        deployProbeProcess(client, probeProcessId);
        createInstancesOnEveryPartition(client, baselineProcessId, baselineJobType);
      }

      // and — a completed baseline backup on all three tenants
      ALL_TENANTS.forEach(this::takeSnapshot);
      ALL_TENANTS.forEach(tenant -> takeBackup(tenant, baselineBackupId));

      // and — tenant-c alone completes its baseline jobs and moves on to different work, then takes
      // an additional backup capturing that later state. Every baseline job has to be drained, not
      // just one per partition: any left pending would be captured by the override backup below and
      // come back with it, breaking the "the baseline jobs never reappear" assertion at the end.
      completeEveryJob(tenantCClient, baselineJobType);
      createInstancesOnEveryPartition(tenantCClient, overrideProcessId, overrideJobType);
      takeSnapshot(TENANT_C);
      takeBackup(TENANT_C, overrideBackupId);

      // when — the whole cluster is put into RECOVERING over the cluster-admin endpoint ...
      InProcessRestoreTestUtil.changeClusterMode(defaultClient, null, "RECOVERING", false);
      awaitCommandsRejected(defaultClient, probeProcessId);
      awaitCommandsRejected(tenantBClient, probeProcessId);
      awaitCommandsRejected(tenantCClient, probeProcessId);

      // ... and a cluster-wide restore is triggered: tenant-c overridden to its own, additional
      // backup, default and tenant-b left on the top-level (baseline) selection
      final var response =
          awaitRestoreAccepted(
              () ->
                  InProcessRestoreTestUtil.sendClusterRestoreRequest(
                      defaultClient,
                      null,
                      restoreBody(baselineBackupId, Map.of(TENANT_C, overrideBackupId))));

      // then — the override changes which backup a tenant reads, not the shape of the plan: all
      // three tenants are still planned across every broker and every partition
      assertPlanCovers(response, ALL_TENANTS);

      // and — every tenant processes commands again once its restore completes
      awaitCommandsAccepted(defaultClient, probeProcessId);
      awaitCommandsAccepted(tenantBClient, probeProcessId);
      awaitCommandsAccepted(tenantCClient, probeProcessId);

      // and — default and tenant-b restored from the baseline backup: their baseline jobs are still
      // there to complete, on every partition
      completeJobsFromEveryPartition(defaultClient, baselineJobType);
      completeJobsFromEveryPartition(tenantBClient, baselineJobType);

      // and — tenant-c restored from its own overridden backup, not the baseline: the baseline jobs
      // it had already completed before that backup was taken never reappear, but the jobs the
      // override backup captured do
      assertThat(activatedJobTypes(tenantCClient, baselineJobType)).isEmpty();
      completeJobsFromEveryPartition(tenantCClient, overrideJobType);
    }
  }

  /**
   * Asserts the accepted plan covers every parallel axis the restore has: one planned group per
   * expected physical tenant, and within each group the pre-restore and restore of every partition
   * on every broker, plus a mode change and an await per broker.
   *
   * <p>This is coverage, not overlap — see the class javadoc. A plan that covered only one broker,
   * or only one partition per broker, or only one tenant of a cluster-wide request, would still
   * complete successfully and pass every other assertion in this test; only this one rejects it.
   */
  private static void assertPlanCovers(
      final HttpResponse<String> response, final Set<String> expectedTenants) {
    final JsonNode plannedChanges = readJson(response.body()).path("plannedChanges");
    final var plannedTenants = new ArrayList<String>();
    plannedChanges.forEach(group -> plannedTenants.add(group.path("physicalTenantId").asText()));
    assertThat(plannedTenants)
        .describedAs("physical tenants planned by the restore: %s", response.body())
        .containsExactlyInAnyOrderElementsOf(expectedTenants);

    final var expectedPartitionWork =
        IntStream.rangeClosed(1, BROKERS_COUNT)
            .boxed()
            .flatMap(
                broker ->
                    IntStream.rangeClosed(1, PARTITIONS_COUNT)
                        .mapToObj(partition -> brokerId(broker) + "/p" + partition))
            .toList();
    final var expectedBrokers =
        IntStream.rangeClosed(1, BROKERS_COUNT)
            .mapToObj(ClusterAdminRestoreAcceptanceIT::brokerId)
            .toList();

    plannedChanges.forEach(
        group -> {
          final var tenant = group.path("physicalTenantId").asText();
          assertThat(partitionWork(group, "PartitionPreRestoreOperation"))
              .describedAs("pre-restored partitions per broker for tenant '%s'", tenant)
              .containsExactlyInAnyOrderElementsOf(expectedPartitionWork);
          assertThat(partitionWork(group, "PartitionRestoreOperation"))
              .describedAs("restored partitions per broker for tenant '%s'", tenant)
              .containsExactlyInAnyOrderElementsOf(expectedPartitionWork);
          assertThat(brokerWork(group, "ModeChangeOperation"))
              .describedAs("mode changes per broker for tenant '%s'", tenant)
              .containsExactlyInAnyOrderElementsOf(expectedBrokers);
          assertThat(brokerWork(group, "AwaitModeChangeOperation"))
              .describedAs("awaited mode changes per broker for tenant '%s'", tenant)
              .containsExactlyInAnyOrderElementsOf(expectedBrokers);
        });
  }

  /** The {@code <broker>/p<partition>} pairs a group plans for the given operation type. */
  private static List<String> partitionWork(final JsonNode group, final String operationType) {
    final var work = new ArrayList<String>();
    group
        .path("operations")
        .forEach(
            operation -> {
              if (operationType.equals(operation.path("operation").asText())) {
                work.add(
                    operation.path("brokerId").asText()
                        + "/p"
                        + operation.path("partitionId").asInt());
              }
            });
    return work;
  }

  /** The brokers a group plans the given broker-scoped operation type for. */
  private static List<String> brokerWork(final JsonNode group, final String operationType) {
    final var work = new ArrayList<String>();
    group
        .path("operations")
        .forEach(
            operation -> {
              if (operationType.equals(operation.path("operation").asText())) {
                work.add(operation.path("brokerId").asText());
              }
            });
    return work;
  }

  /**
   * Broker ids are zero-based; the loops above are one-based for readability alongside partitions.
   */
  private static String brokerId(final int oneBasedIndex) {
    return String.valueOf(oneBasedIndex - 1);
  }

  private CamundaClient newClient(final String tenantId) {
    return TENANTS.newClientBuilder(cluster.availableGateway(), tenantId).build();
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
   * Leaves one pending job of the given type on every partition of the client's tenant, so the
   * post-restore assertions can tell a restore that brought back one partition from one that
   * brought back all of them.
   */
  private static void createInstancesOnEveryPartition(
      final CamundaClient client, final String processId, final String jobType) {
    InProcessRestoreTestUtil.deployAndCreateInstancesOnEveryPartition(
        client, processId, jobType, PARTITIONS_COUNT);
  }

  /** Completes the pending jobs, asserting one came from every partition. */
  private static void completeJobsFromEveryPartition(
      final CamundaClient client, final String jobType) {
    InProcessRestoreTestUtil.activateAndCompleteJobsFromEveryPartition(
        client, jobType, PARTITIONS_COUNT);
  }

  /** Completes every pending job of the type, leaving none behind for a later backup to capture. */
  private static void completeEveryJob(final CamundaClient client, final String jobType) {
    InProcessRestoreTestUtil.completeEveryJob(client, jobType, PARTITIONS_COUNT);
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

  /**
   * Activates up to a partition's worth of jobs of the given type, for a negative "it isn't there"
   * assertion. Sized above the partition count so a single poll can see any partition's job.
   */
  private static List<String> activatedJobTypes(final CamundaClient client, final String jobType) {
    return client
        .newActivateJobsCommand()
        .jobType(jobType)
        .maxJobsToActivate(2 * PARTITIONS_COUNT)
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
        .atMost(Duration.ofMinutes(3))
        .ignoreExceptions()
        .untilAsserted(() -> assertThat(createInstance(client, processId)).isPositive());
  }

  /**
   * Retries the given restore trigger until it is accepted, and returns the accepting response so
   * the plan it carries can be asserted. The mode change preceding it may not have fully settled
   * yet even though the affected tenant's commands are already rejected — rejection can start
   * before the configuration change that caused it is marked complete — so a transient 409 (another
   * configuration change still in progress) is retried rather than failing outright. Safe to retry:
   * nothing has started until the trigger is actually accepted.
   */
  private static HttpResponse<String> awaitRestoreAccepted(
      final Supplier<HttpResponse<String>> trigger) {
    final var accepted = new ArrayList<HttpResponse<String>>();
    Awaitility.await("cluster-admin restore is accepted once the prior change clears")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              final var response = trigger.get();
              assertThat(response.statusCode())
                  .describedAs("cluster restore REST response: %s", response.body())
                  .isEqualTo(202);
              accepted.add(response);
            });
    return accepted.getLast();
  }

  private static ClusterRestoreRequest restoreBody(final long backupId) {
    return restoreBody(backupId, Map.of());
  }

  private static ClusterRestoreRequest restoreBody(
      final long backupId, final Map<String, Long> overrideBackupIdByTenant) {
    final var body = new ClusterRestoreRequest().backupIds(List.of(backupId));
    overrideBackupIdByTenant.forEach(
        (tenantId, overrideBackupId) ->
            body.putOverridesItem(
                tenantId, new RestoreRequest().backupIds(List.of(overrideBackupId))));
    return body;
  }

  /**
   * Takes a snapshot on every partition of the tenant, on every broker, and waits for it to be
   * persisted. A restore reads each broker's own backup of its own replica, so a snapshot taken on
   * one broker alone would leave the others' backups behind the work being captured.
   *
   * <p>Snapshotting is asynchronous, and a tenant may already carry a snapshot from an earlier call
   * here, so the wait is for each partition's snapshot ID to <em>change</em>: merely waiting for a
   * non-null ID would return immediately on the second call and let the backup be taken before the
   * work it is meant to capture is snapshotted.
   */
  private void takeSnapshot(final String tenantId) {
    cluster
        .brokers()
        .values()
        .forEach(
            broker -> {
              final var partitions = PartitionsActuator.of(broker);
              final var previousSnapshotIds = new HashMap<Integer, String>();
              partitions
                  .query(tenantId)
                  .forEach((id, status) -> previousSnapshotIds.put(id, status.snapshotId()));
              partitions.takeSnapshot(tenantId);
              Awaitility.await(
                      "a new snapshot is taken for tenant %s on broker %s"
                          .formatted(tenantId, broker.nodeId()))
                  .atMost(Duration.ofSeconds(60))
                  .untilAsserted(
                      () ->
                          assertThat(partitions.query(tenantId))
                              .allSatisfy(
                                  (partitionId, status) ->
                                      assertThat(status.snapshotId())
                                          .isNotNull()
                                          .isNotEqualTo(previousSnapshotIds.get(partitionId))));
            });
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
        .atMost(Duration.ofSeconds(120))
        .ignoreExceptions() // 404 NOT_FOUND until the backup is registered
        .untilAsserted(
            () -> {
              final var status =
                  send(HttpRequest.newBuilder(URI.create(uri + "/" + backupId)).GET().build());
              assertThat(status.statusCode()).isEqualTo(200);
              assertThat(readJson(status.body()).path("state").asText()).isEqualTo("COMPLETED");
            });
  }

  private URI backupsUri(final String tenantId) {
    final var base = cluster.availableGateway().restAddress().toString().replaceAll("/+$", "");
    return URI.create(
        DEFAULT_TENANT.equals(tenantId)
            ? base + "/v2/backups/runtime"
            : base + "/physical-tenants/" + tenantId + "/v2/backups/runtime");
  }

  private static JsonNode readJson(final String body) {
    try {
      return OBJECT_MAPPER.readTree(body);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to parse REST response: " + body, e);
    }
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
