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
import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.client.protocol.rest.ClusterRestoreRequest;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.Data;
import io.camunda.configuration.PrimaryStorageBackup;
import io.camunda.configuration.PrimaryStorageBackup.BackupStoreType;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreImpl;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Proves that a physical tenant can always be moved into recovery mode and restored, whatever state
 * one broker's copy of one of its partitions is in - including states that stop the partition from
 * starting at all.
 *
 * <p>That is the invariant the recovery API rests on. A mode change is the operator's only way in
 * to a restore, so it has to be triggerable regardless of the health of the brokers' partitions: if
 * a partition that fails to bootstrap could block the transition, the one operation that repairs
 * such a partition would be unreachable exactly when it is needed. Only Spring Boot and the
 * broker's own required services are assumed to be up.
 *
 * <p>Each case breaks {@link #BROKEN_PARTITION_ID} on broker {@link #BROKEN_BROKER_ID} in a
 * different way (see {@link Break}), leaving its peers' copies and the backup store untouched so
 * the cluster keeps quorum and stays available, and then asserts the same four things:
 *
 * <ol>
 *   <li>the broker is up and still serving the other partition it holds;
 *   <li>the tenant enters {@code RECOVERING}, rather than the change plan stalling on the broken
 *       partition;
 *   <li>the accepted restore plan wipes and reloads the broken copy, not only the healthy ones;
 *   <li>after the restore the tenant processes commands again, every partition's backed-up work is
 *       back, and the broken copy is healthy on its own broker.
 * </ol>
 *
 * <h4>Why the plan assertion matters</h4>
 *
 * <p>{@code PartitionModeHandler#awaitModeApplied} confirms only the partitions that came up
 * healthy, so a broken one keeps its prior {@code PartitionState} while its broker's mode flips to
 * {@code RECOVERING}. The restore plan is built from the partitions a broker holds and gated on
 * that broker's mode ({@code RestoreAppliers#requireRecoveringMember}), not on per-partition state,
 * which is what lets the broken copy be wiped and reloaded rather than skipped. A restore planned
 * from per-partition state would quietly skip it, still complete, and still pass every other
 * assertion here - because the partition's peers hold the same data.
 *
 * <p>The matrix runs over the per-physical-tenant API; {@link
 * #shouldEnterRecoveryAndRestoreOverClusterAdminApi} covers the cluster-admin API, on the harshest
 * break, since the API surface is independent of how the partition was broken.
 *
 * <p>{@code PartitionModeHandlerRecoveryRoundTripTest} and {@code RecoveryPartitionManagerTest} pin
 * the same confirmation logic against a stubbed partition manager. What they cannot show is that a
 * partition which genuinely fails to start produces that state, that the broker survives it, and
 * that the restore then puts the replica back.
 */
@Timeout(1800)
@ZeebeIntegration
final class RestoreWithBrokenPartitionIT {

  private static final String DEFAULT_TENANT = PhysicalTenantsITHelper.DEFAULT_TENANT_ID;
  private static final String TENANT_B = "tenantb";

  private static final int BROKERS_COUNT = 3;
  private static final int PARTITIONS_COUNT = 2;

  /**
   * The broker whose copy of {@link #BROKEN_PARTITION_ID} is broken. Broker 0 is deliberately not
   * the one: it serves the gateway every client in this test talks to, so breaking it would test
   * client failover instead of restore.
   */
  private static final int BROKEN_BROKER_ID = 1;

  private static final int BROKEN_PARTITION_ID = 2;

  /**
   * The other partition the broken broker holds. Asserting on it alongside the broken one is what
   * distinguishes a broker serving one partition of a tenant from a broker serving none.
   */
  private static final int HEALTHY_PARTITION_ID = 1;

  /**
   * Any value outside the versions {@code SegmentDescriptorSerializer} supports; only version 2 is
   * readable today, and 0 has never been a valid one.
   */
  private static final byte UNSUPPORTED_SEGMENT_DESCRIPTOR_VERSION = 0;

  private static final byte[] CORRUPTION =
      "<--corrupted-by-RestoreWithBrokenPartitionIT-->".getBytes();

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @TempDir private static Path defaultBackupDir;
  @TempDir private static Path tenantBBackupDir;

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(DEFAULT_TENANT, Storage.none())
          .withTenant(TENANT_B, Storage.none())
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

  /**
   * The ways one broker's copy of a partition is broken before the recovery transition. Every one
   * of them has to leave the transition triggerable; whether a given one also stops the partition
   * from starting is not asserted, because that is the product's business and it differs per mode -
   * what has to hold either way is the invariant.
   */
  private enum Break {
    /** The snapshot is gone, so the replica has only its log to rebuild state from. */
    MISSING_SNAPSHOT,
    /**
     * The snapshot store cannot be opened at all. Fails the partition's first startup step, before
     * it is ever assigned a role.
     */
    UNOPENABLE_SNAPSHOT_STORE,
    /** The raft log cannot be loaded, so the replica cannot recover its own history. */
    CORRUPT_RAFT_LOG,
    /** The snapshot is present but its RocksDB files are damaged. */
    CORRUPT_ROCKSDB_SNAPSHOT,
    /** The partition runs but makes no progress, the way a wedged stream processor leaves it. */
    STUCK_STREAM_PROCESSOR;

    /** Whether the break is on disk and so needs the broker stopped and started around it. */
    boolean isOnDisk() {
      return this != STUCK_STREAM_PROCESSOR;
    }
  }

  @ParameterizedTest
  @EnumSource(Break.class)
  void shouldEnterRecoveryAndRestoreOverPhysicalTenantApi(final Break brokenBy) throws IOException {
    try (final var defaultClient = newClient(DEFAULT_TENANT);
        final var tenantBClient = newClient(TENANT_B)) {
      final var processId = "pt-broken-restore-process";
      final var jobType = "pt-broken-restore-job";
      final var probeProcessId = "pt-broken-restore-probe";
      final long backupId = 61;

      // given - tenant-b has a pending job on every partition, captured in a completed backup
      deployProbeProcess(defaultClient, probeProcessId);
      deployProbeProcess(tenantBClient, probeProcessId);
      createInstancesOnEveryPartition(tenantBClient, processId, jobType);
      InProcessRestoreTestUtil.takeSnapshotOnEveryBroker(cluster, TENANT_B);
      InProcessRestoreTestUtil.takeBackup(cluster, TENANT_B, backupId);

      // and - one broker's copy of one of tenant-b's partitions is broken, while the broker itself
      // stays up and keeps serving the other partition it holds
      breakPartition(brokenBy, TENANT_B);
      assertBrokenBrokerIsStillServing(tenantBClient, TENANT_B);

      // when - tenant-b alone is moved into recovery over its own physical-tenant API
      awaitModeChangeAccepted(
          "RECOVERING",
          () -> InProcessRestoreTestUtil.changeMode(tenantBClient, TENANT_B, "RECOVERING", false));

      // then - the transition takes effect rather than stalling on the broken partition
      awaitCommandsRejected(tenantBClient, probeProcessId);

      // and - the restore is accepted, and covers the broken copy as well as the healthy ones
      final var response =
          awaitRestoreAccepted(
              () ->
                  InProcessRestoreTestUtil.sendRestoreRequest(
                      tenantBClient, TENANT_B, Map.of("backupIds", List.of(backupId))));
      assertPlanCoversBrokenPartition(response, TENANT_B);

      // and - tenant-b processes commands again once the restore completes, with the pending job of
      // every partition back, and the broken copy healthy on its own broker
      awaitCommandsAccepted(tenantBClient, probeProcessId);
      completeJobsFromEveryPartition(tenantBClient, jobType);
      awaitPartitionRestored(tenantBClient, TENANT_B);

      // and - the default tenant was never affected by the other tenant's broken partition
      assertThat(createInstance(defaultClient, probeProcessId)).isPositive();
    }
  }

  /**
   * The cluster-admin API on the break that stops the partition from starting outright. Which API
   * triggers the transition is independent of how the partition was broken, so this covers the
   * second surface without running the whole matrix over it again.
   */
  @Test
  void shouldEnterRecoveryAndRestoreOverClusterAdminApi() throws IOException {
    try (final var defaultClient = newClient(DEFAULT_TENANT);
        final var tenantBClient = newClient(TENANT_B)) {
      final var processId = "cluster-broken-restore-process";
      final var jobType = "cluster-broken-restore-job";
      final var probeProcessId = "cluster-broken-restore-probe";
      final long backupId = 62;

      // given - both physical tenants have a pending job on every partition, backed up under the
      // same id so one cluster-wide restore can name it for all of them
      for (final var client : List.of(defaultClient, tenantBClient)) {
        deployProbeProcess(client, probeProcessId);
        createInstancesOnEveryPartition(client, processId, jobType);
      }
      for (final var tenant : List.of(DEFAULT_TENANT, TENANT_B)) {
        InProcessRestoreTestUtil.takeSnapshotOnEveryBroker(cluster, tenant);
        InProcessRestoreTestUtil.takeBackup(cluster, tenant, backupId);
      }

      // and - one broker's copy of one of the default tenant's partitions cannot start
      breakPartition(Break.CORRUPT_RAFT_LOG, DEFAULT_TENANT);
      assertBrokenBrokerIsStillServing(defaultClient, DEFAULT_TENANT);

      // when - the whole cluster is moved into recovery over the cluster-admin API
      awaitModeChangeAccepted(
          "RECOVERING",
          () ->
              InProcessRestoreTestUtil.changeClusterMode(defaultClient, null, "RECOVERING", false));

      // then - every tenant transitions, the one holding the broken partition included
      awaitCommandsRejected(defaultClient, probeProcessId);
      awaitCommandsRejected(tenantBClient, probeProcessId);

      // and - the cluster-wide restore is accepted and covers the broken copy
      final var response =
          awaitRestoreAccepted(
              () ->
                  InProcessRestoreTestUtil.sendClusterRestoreRequest(
                      defaultClient,
                      null,
                      new ClusterRestoreRequest().backupIds(List.of(backupId))));
      assertPlanCoversBrokenPartition(response, DEFAULT_TENANT);

      // and - both tenants process commands again, with every partition's work back
      awaitCommandsAccepted(defaultClient, probeProcessId);
      awaitCommandsAccepted(tenantBClient, probeProcessId);
      completeJobsFromEveryPartition(defaultClient, jobType);
      completeJobsFromEveryPartition(tenantBClient, jobType);
      awaitPartitionRestored(defaultClient, DEFAULT_TENANT);
    }
  }

  /**
   * Breaks {@link #BROKEN_BROKER_ID}'s copy of {@link #BROKEN_PARTITION_ID} for the given physical
   * tenant. On-disk breaks are made while the broker is down and survive any number of restarts -
   * only wiping and reloading the partition directory, which is what a restore does, undoes them.
   * The broker comes back up regardless of what the break does to the partition, since broker
   * startup does not await partition bootstrap ({@code PartitionManagerStep}).
   */
  private void breakPartition(final Break brokenBy, final String physicalTenantId)
      throws IOException {
    final var broker = brokenByBroker();
    if (!brokenBy.isOnDisk()) {
      applyRuntimeBreak(brokenBy, physicalTenantId);
      return;
    }

    broker.stop();
    final var partitionDirectory =
        InProcessRestoreTestUtil.partitionDirectory(
            broker.getWorkingDirectory(), physicalTenantId, BROKEN_PARTITION_ID);
    assertThat(partitionDirectory)
        .describedAs("partition %d of tenant '%s'", BROKEN_PARTITION_ID, physicalTenantId)
        .isDirectory();
    applyDiskBreak(brokenBy, partitionDirectory);
    broker.start();
  }

  private void applyDiskBreak(final Break brokenBy, final Path partitionDirectory)
      throws IOException {
    final var snapshots =
        partitionDirectory.resolve(FileBasedSnapshotStoreImpl.SNAPSHOTS_DIRECTORY);
    switch (brokenBy) {
      case MISSING_SNAPSHOT -> {
        assertThat(snapshots).isDirectory();
        FileUtil.deleteFolderContents(snapshots);
      }
      // A regular file where the directory belongs: the store fails to open it at all, rather than
      // reading it and finding nothing.
      case UNOPENABLE_SNAPSHOT_STORE -> {
        assertThat(snapshots).isDirectory();
        FileUtil.deleteFolder(snapshots);
        Files.createFile(snapshots);
      }
      // The descriptor version is the first byte of a segment file and the first thing read from
      // it, so an unsupported value fails the load before any of the segment's layout matters
      // ({@code SegmentDescriptorSerializerSbe#readFrom}). The journal only discards a corrupted
      // segment it can prove was never flushed, and the snapshot taken above flushed, so this is
      // a corruption it has to report rather than clean up
      // ({@code SegmentsManager#handleSegmentCorruption}).
      case CORRUPT_RAFT_LOG -> {
        final var segments = filesMatching(partitionDirectory, ".log");
        assertThat(segments).describedAs("raft log segments to corrupt").isNotEmpty();
        for (final var segment : segments) {
          overwriteFirstByte(segment);
        }
      }
      // Garbage over the RocksDB files of the persisted snapshot, leaving the snapshot's recorded
      // checksums describing content that is no longer there.
      case CORRUPT_ROCKSDB_SNAPSHOT -> {
        final var sstFiles = new ArrayList<Path>();
        try (final var snapshotDirs = Files.list(snapshots)) {
          for (final var snapshotDir : snapshotDirs.filter(Files::isDirectory).toList()) {
            sstFiles.addAll(filesMatching(snapshotDir, ".sst"));
          }
        }
        assertThat(sstFiles).describedAs("snapshot RocksDB files to corrupt").isNotEmpty();
        for (final var sstFile : sstFiles) {
          Files.write(sstFile, CORRUPTION, StandardOpenOption.TRUNCATE_EXISTING);
        }
      }
      default -> throw new IllegalArgumentException("Not an on-disk break: " + brokenBy);
    }
  }

  /**
   * Wedges the partition without touching its data: processing is paused on the broken broker, so
   * its partitions stay up and healthy but stop making progress. Asserted rather than assumed,
   * since a paused processor is the whole precondition of this case.
   */
  private void applyRuntimeBreak(final Break brokenBy, final String physicalTenantId) {
    if (brokenBy != Break.STUCK_STREAM_PROCESSOR) {
      throw new IllegalArgumentException("Not a runtime break: " + brokenBy);
    }
    final var partitions = PartitionsActuator.of(brokenByBroker());
    partitions.pauseProcessing(physicalTenantId);
    Awaitility.await("processing is paused on broker " + BROKEN_BROKER_ID)
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(partitions.query(physicalTenantId))
                    .describedAs("stream processor phase per partition")
                    .allSatisfy(
                        (partitionId, status) ->
                            assertThat(status.streamProcessorPhase()).isEqualTo("PAUSED")));
  }

  private static List<Path> filesMatching(final Path directory, final String suffix)
      throws IOException {
    try (final var files = Files.list(directory)) {
      return files.filter(file -> file.toString().endsWith(suffix)).sorted().toList();
    }
  }

  /**
   * Patched in place rather than read-modify-written: a raft log segment is pre-allocated to the
   * configured segment size, so rewriting it whole would pull all of that through the heap.
   */
  private static void overwriteFirstByte(final Path file) throws IOException {
    try (final var channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
      channel.write(ByteBuffer.wrap(new byte[] {UNSUPPORTED_SEGMENT_DESCRIPTOR_VERSION}), 0);
    }
  }

  /**
   * Asserts the broker carrying the broken partition is up and still serving the other partition it
   * holds of that tenant. That is the precondition every case shares: whatever the break did to one
   * partition, the broker itself is a live member the mode change has to be able to move.
   *
   * <p>Deliberately does not assert anything about the broken partition. Whether a given break
   * stops it from starting, or merely leaves it unable to progress, differs per break and is the
   * product's business; what every case then asserts is the invariant, not the classification.
   */
  private static void assertBrokenBrokerIsStillServing(
      final CamundaClient client, final String physicalTenantId) {
    Awaitility.await(
            "broker %d is up and serving partition %d"
                .formatted(BROKEN_BROKER_ID, HEALTHY_PARTITION_ID))
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                assertThat(partitionsOfBrokenBroker(client, physicalTenantId))
                    .containsEntry(HEALTHY_PARTITION_ID, "healthy"));
  }

  /**
   * Asserts the previously broken partition is back on its own broker and healthy there. Restoring
   * the other brokers' copies alone would satisfy every other assertion in this test, since they
   * hold the same data; only this one is about the replica that was broken.
   */
  private void awaitPartitionRestored(final CamundaClient client, final String physicalTenantId) {
    Awaitility.await("the restored partition is healthy again on broker " + BROKEN_BROKER_ID)
        .atMost(Duration.ofMinutes(2))
        .untilAsserted(
            () ->
                assertThat(partitionsOfBrokenBroker(client, physicalTenantId))
                    .containsEntry(BROKEN_PARTITION_ID, "healthy")
                    .containsEntry(HEALTHY_PARTITION_ID, "healthy"));

    // and - the broker's own partitions actuator serves them too. The recovery round trip replaced
    // the partition manager twice, so this holds only while the admin service resolves the current
    // manager per request rather than answering from the one captured at broker startup.
    final var partitions = PartitionsActuator.of(brokenByBroker());
    Awaitility.await("the partitions actuator answers from the post-restore partition manager")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(partitions.query(physicalTenantId))
                    .containsKeys(HEALTHY_PARTITION_ID, BROKEN_PARTITION_ID));
  }

  /**
   * The health the broken broker reports for each partition it holds of the given tenant, keyed by
   * partition id. Read from the tenant's own topology: it is fed by what each broker gossips about
   * itself, so it observes the broken broker without depending on that broker's actuator plumbing.
   * The actuator view is asserted separately in {@link #awaitPartitionRestored}, where it guards
   * the admin service resolving the current partition manager rather than the boot-time one.
   */
  private static Map<Integer, String> partitionsOfBrokenBroker(
      final CamundaClient client, final String physicalTenantId) {
    final var response = InProcessRestoreTestUtil.sendTopologyRequest(client, physicalTenantId);
    assertThat(response.statusCode())
        .describedAs("topology of tenant '%s': %s", physicalTenantId, response.body())
        .isEqualTo(200);

    final var partitions = new HashMap<Integer, String>();
    readJson(response.body())
        .path("brokers")
        .forEach(
            broker -> {
              if (broker.path("nodeId").asInt() != BROKEN_BROKER_ID) {
                return;
              }
              broker
                  .path("partitions")
                  .forEach(
                      partition ->
                          partitions.put(
                              partition.path("partitionId").asInt(),
                              partition.path("health").asText()));
            });
    return partitions;
  }

  /**
   * Asserts the accepted plan wipes and reloads the broken copy of the partition, not only the
   * healthy copies. This is the assertion that rejects a restore which quietly skips a partition
   * that never confirmed its recovery transition - such a restore would still complete, and every
   * other assertion here would still pass, because the partition's peers hold the same data.
   */
  private static void assertPlanCoversBrokenPartition(
      final HttpResponse<String> response, final String physicalTenantId) {
    final var group =
        groupOf(
            readJson(response.body()).path("plannedChanges"), physicalTenantId, response.body());
    final var brokenCopy = String.valueOf(BROKEN_BROKER_ID) + "/p" + BROKEN_PARTITION_ID;
    assertThat(partitionWork(group, "PartitionPreRestoreOperation"))
        .describedAs("pre-restored copies of tenant '%s': %s", physicalTenantId, response.body())
        .contains(brokenCopy);
    assertThat(partitionWork(group, "PartitionRestoreOperation"))
        .describedAs("restored copies of tenant '%s': %s", physicalTenantId, response.body())
        .contains(brokenCopy);
  }

  private static JsonNode groupOf(
      final JsonNode plannedChanges, final String physicalTenantId, final String body) {
    for (final var group : plannedChanges) {
      if (physicalTenantId.equals(group.path("physicalTenantId").asText())) {
        return group;
      }
    }
    throw new AssertionError(
        "no planned changes for physical tenant '%s': %s".formatted(physicalTenantId, body));
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

  private TestStandaloneBroker brokenByBroker() {
    return cluster.brokers().get(MemberId.from(String.valueOf(BROKEN_BROKER_ID)));
  }

  /**
   * Builds a client against broker 0's embedded gateway rather than any available one, so that
   * stopping {@link #BROKEN_BROKER_ID} does not take the client's own gateway down with it.
   */
  private CamundaClient newClient(final String tenantId) {
    return TENANTS.newClientBuilder(cluster.brokers().get(MemberId.from("0")), tenantId).build();
  }

  /**
   * Deploys the process the readiness probes create instances of: it carries no service task, so a
   * probe instance leaves no job behind, and the job assertions cannot pass off work a probe
   * created as work the backup captured. Deploy it before the backup is taken, so the restored
   * state still knows it.
   */
  private static void deployProbeProcess(final CamundaClient client, final String processId) {
    final var process = Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();
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
   * Leaves one pending job of the given type on every partition of the client's tenant, so the
   * post-restore assertions can tell a restore that brought back one partition from one that
   * brought back all of them.
   */
  private static void createInstancesOnEveryPartition(
      final CamundaClient client, final String processId, final String jobType) {
    InProcessRestoreTestUtil.deployAndCreateInstancesOnEveryPartition(
        client, processId, jobType, PARTITIONS_COUNT);
  }

  private static void completeJobsFromEveryPartition(
      final CamundaClient client, final String jobType) {
    InProcessRestoreTestUtil.activateAndCompleteJobsFromEveryPartition(
        client, jobType, PARTITIONS_COUNT);
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

  /**
   * Sends the given mode change until the cluster accepts it: a change is rejected with {@code 409}
   * while another one is still pending. Acceptance is all there is to wait for here - a change
   * scoped to a non-default physical tenant is not observable through the topology API, which
   * projects the default partition group only (see {@code ClusterAdminPhysicalTenantModeChangeIT}).
   */
  private static void awaitModeChangeAccepted(final String mode, final Runnable modeChange) {
    Awaitility.await("the cluster accepts the mode change to " + mode)
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(modeChange::run);
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
   * yet even though the affected tenant's commands are already rejected - rejection can start
   * before the configuration change that caused it is marked complete - so a transient 409 is
   * retried rather than failing outright. Safe to retry: nothing has started until the trigger is
   * actually accepted.
   */
  private static HttpResponse<String> awaitRestoreAccepted(
      final Supplier<HttpResponse<String>> trigger) {
    final var accepted = new ArrayList<HttpResponse<String>>();
    Awaitility.await("the restore is accepted once the prior change clears")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              final var response = trigger.get();
              assertThat(response.statusCode())
                  .describedAs("restore REST response: %s", response.body())
                  .isEqualTo(202);
              accepted.add(response);
            });
    return accepted.getLast();
  }

  private static JsonNode readJson(final String body) {
    try {
      return OBJECT_MAPPER.readTree(body);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to parse REST response: " + body, e);
    }
  }

  private static TestStandaloneBroker configureBackupStores(final TestStandaloneBroker broker) {
    return broker
        .withDataConfig(RestoreWithBrokenPartitionIT::configureFilesystemBackup)
        .withPtConfig(
            TENANT_B, camunda -> configureFilesystemBackupForTenant(camunda, tenantBBackupDir));
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
