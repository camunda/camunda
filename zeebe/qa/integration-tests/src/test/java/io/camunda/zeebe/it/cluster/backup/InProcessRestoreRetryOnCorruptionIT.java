/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.PrimaryStorageBackup.BackupStoreType;
import io.camunda.management.backups.BackupInfo;
import io.camunda.management.backups.PartitionBackupInfo;
import io.camunda.management.backups.StateCode;
import io.camunda.management.backups.TakeBackupRuntimeResponse;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.management.cluster.BrokerState;
import io.camunda.zeebe.management.cluster.Operation.OperationEnum;
import io.camunda.zeebe.management.cluster.PartitionState;
import io.camunda.zeebe.management.cluster.PartitionStateCode;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that in-process restore on a 2-partition broker restores partition 1 successfully, then,
 * when partition 2's backup snapshot is corrupted, fails partition 2's restore step without ever
 * completing the cluster change - and, once the corruption is fixed, the SAME already-pending
 * change completes on its own, proving the existing cluster-change retry framework picked the
 * failed {@code PartitionRestoreOperation} step back up automatically (the test never re-triggers
 * restore over REST after the initial request).
 */
@ZeebeIntegration
final class InProcessRestoreRetryOnCorruptionIT {

  private static final int PARTITIONS_COUNT = 2;
  private static final long BACKUP_ID = 42;
  private static final String JOB_TYPE = "restore-retry-on-corruption-job";
  private static final String PROCESS_ID = "restore-retry-on-corruption-process";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private static @TempDir Path backupDir;

  @TestZeebe(partitionCount = PARTITIONS_COUNT)
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withUnifiedConfig(
              cfg -> {
                cfg.getCluster().setPartitionCount(PARTITIONS_COUNT);
                configureBackupStore(cfg);
              });

  @Test
  void shouldRestorePartitionOneThenRetryPartitionTwoAfterCorruptionIsFixed() throws Exception {
    try (final var client = broker.newClientBuilder().build()) {
      // given - every partition has at least one process instance with a pending job
      deployProcessAndCreateInstances(client);

      // and - a completed backup for both partitions, taken after a snapshot
      PartitionsActuator.of(broker).takeSnapshot();
      Awaitility.await("snapshot is taken on every partition")
          .atMost(Duration.ofSeconds(60))
          .untilAsserted(
              () ->
                  assertThat(PartitionsActuator.of(broker).query().values())
                      .allSatisfy(status -> assertThat(status.snapshotId()).isNotNull()));
      takeBackup();

      // and - partition 2's backup snapshot is corrupted on disk, partition 1's is left intact
      final var originalSnapshotFiles = corruptPartitionSnapshot(2);

      // when - the broker is put into RECOVERING mode and a restore is triggered
      final var clusterActuator = ClusterActuator.of(broker);
      final var toRecovering = clusterActuator.updateMode("RECOVERING", false);
      Awaitility.await("broker transitions to RECOVERING")
          .timeout(Duration.ofSeconds(60))
          .untilAsserted(
              () ->
                  ClusterActuatorAssert.assertThat(clusterActuator)
                      .hasCompletedChanges(toRecovering.getChangeId())
                      .doesNotHavePendingChanges());
      final var changeId = triggerRestore(client, BACKUP_ID);

      // then - partition 1 restores fine and the change plan advances past its restore step,
      // while partition 2's restore step keeps failing and the change never completes
      Awaitility.await("partition 2's restore step is the one stuck retrying")
          .timeout(Duration.ofSeconds(30))
          .untilAsserted(() -> assertPartitionRestoreIsPending(clusterActuator, 2));

      // and - the change plan stays stuck there for multiple retry attempts (the framework
      // retries with a ~10s backoff), not just the first attempt
      Awaitility.await("the corrupted restore keeps failing across multiple retries")
          .during(Duration.ofSeconds(25))
          .atMost(Duration.ofSeconds(35))
          .until(
              () -> {
                assertPartitionRestoreIsPending(clusterActuator, 2);
                return true;
              });

      // when - the corruption is fixed by restoring the original snapshot bytes, without ever
      // re-triggering restore over REST again
      restoreOriginalSnapshotFiles(originalSnapshotFiles);

      // then - the SAME change, already pending since the first REST call, completes on its own
      Awaitility.await("restore change plan completes once the retry succeeds")
          .timeout(Duration.ofMinutes(2))
          .untilAsserted(
              () ->
                  ClusterActuatorAssert.assertThat(clusterActuator)
                      .hasCompletedChanges(changeId)
                      .doesNotHavePendingChanges());

      // and - every partition is ACTIVE again
      Awaitility.await("every partition reports ACTIVE again")
          .timeout(Duration.ofSeconds(60))
          .untilAsserted(
              () -> {
                final var topology = clusterActuator.getTopology();
                assertThat(topology.getBrokers())
                    .flatExtracting(BrokerState::getPartitions)
                    .extracting(PartitionState::getState)
                    .allMatch(state -> state == PartitionStateCode.ACTIVE);
              });

      // and - jobs from every partition are activated again, proving both partitions' data
      // (including partition 2's, restored only after the retry succeeded) is intact
      assertJobsActivatedFromEveryPartition(client);
    }
  }

  private void deployProcessAndCreateInstances(final CamundaClient client) {
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();
    final var deploymentKey =
        client
            .newDeployResourceCommand()
            .addProcessModel(process, "process.bpmn")
            .send()
            .join()
            .getKey();
    new ZeebeResourcesHelper(client).waitUntilDeploymentIsDone(deploymentKey);

    final var totalInstances = 2 * PARTITIONS_COUNT;
    final List<Long> processInstanceKeys = new ArrayList<>();
    Awaitility.await("every partition has at least one process instance with a pending job")
        .timeout(Duration.ofSeconds(60))
        // might throw exception when a partition has not yet received deployment distribution
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              while (processInstanceKeys.size() < totalInstances) {
                final var result =
                    client
                        .newCreateInstanceCommand()
                        .bpmnProcessId(PROCESS_ID)
                        .latestVersion()
                        .send()
                        .join();
                processInstanceKeys.add(result.getProcessInstanceKey());
              }

              final var partitionsWithInstance =
                  processInstanceKeys.stream()
                      .map(Protocol::decodePartitionId)
                      .collect(Collectors.toSet());
              assertThat(partitionsWithInstance)
                  .describedAs(
                      "every partition has at least one process instance with a pending job")
                  .containsExactlyInAnyOrderElementsOf(
                      IntStream.rangeClosed(1, PARTITIONS_COUNT).boxed().toList());
            });
  }

  private void takeBackup() {
    final var actuator = BackupActuator.of(broker);
    assertThat(actuator.take(BACKUP_ID)).isInstanceOf(TakeBackupRuntimeResponse.class);
    Awaitility.await("until the backup is completed for every partition")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // 404 NOT_FOUND throws exception
        .untilAsserted(
            () -> {
              final var status = actuator.status(BACKUP_ID);
              assertThat(status.getDetails())
                  .extracting(PartitionBackupInfo::getPartitionId, PartitionBackupInfo::getState)
                  .containsExactlyInAnyOrder(
                      tuple(1, StateCode.COMPLETED), tuple(2, StateCode.COMPLETED));
              assertThat(status)
                  .extracting(BackupInfo::getBackupId, BackupInfo::getState)
                  .containsExactly(BACKUP_ID, StateCode.COMPLETED);
            });
  }

  /**
   * Truncates every {@code .sst} file in the given partition's completed backup snapshot on disk,
   * overwriting it with garbage bytes so a later restore's RocksDB sanity check fails with a
   * checksum/corruption error. Returns the original file contents so they can be restored later.
   */
  private Map<Path, byte[]> corruptPartitionSnapshot(final int partitionId) throws IOException {
    final var snapshotDir =
        backupDir
            .resolve("contents")
            .resolve(String.valueOf(partitionId))
            .resolve(String.valueOf(BACKUP_ID))
            .resolve("0") // broker/node id, default for a single unconfigured broker
            .resolve("snapshot");

    final List<Path> sstFiles;
    try (final var files = Files.list(snapshotDir)) {
      sstFiles = files.filter(p -> p.toString().endsWith(".sst")).sorted().toList();
    }
    assertThat(sstFiles)
        .describedAs(
            "partition %d's backup snapshot directory %s must contain .sst files to corrupt",
            partitionId, snapshotDir)
        .isNotEmpty();

    final var originalContents = new HashMap<Path, byte[]>();
    for (final var sstFile : sstFiles) {
      originalContents.put(sstFile, Files.readAllBytes(sstFile));
      Files.write(
          sstFile,
          "<--corrupted-by-InProcessRestoreRetryOnCorruptionIT-->".getBytes(),
          StandardOpenOption.TRUNCATE_EXISTING);
    }
    return originalContents;
  }

  private void restoreOriginalSnapshotFiles(final Map<Path, byte[]> originalContents)
      throws IOException {
    for (final var entry : originalContents.entrySet()) {
      Files.write(entry.getKey(), entry.getValue(), StandardOpenOption.TRUNCATE_EXISTING);
    }
  }

  private long triggerRestore(final CamundaClient client, final long backupId) throws Exception {
    final var uri =
        URI.create(
            "%sv2/restore?dryRun=false".formatted(client.getConfiguration().getRestAddress()));
    final var body = OBJECT_MAPPER.writeValueAsString(Map.of("backupIds", List.of(backupId)));
    final var request =
        HttpRequest.newBuilder(uri)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    final var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode())
        .describedAs("restore REST response: %s".formatted(response.body()))
        .isEqualTo(202);
    return OBJECT_MAPPER.readTree(response.body()).get("changeId").asLong();
  }

  /**
   * Asserts the cluster change is still pending (not completed, not failed-out) and that its
   * current pending operation for {@code partitionId} is specifically a {@code PARTITION_RESTORE}
   * step - i.e. the restore attempt for this partition is the one stuck retrying, not some other
   * step or partition.
   */
  private void assertPartitionRestoreIsPending(
      final ClusterActuator clusterActuator, final int partitionId) {
    final var pendingChange = clusterActuator.getTopology().getPendingChange();
    assertThat(pendingChange)
        .describedAs("cluster change must still be pending, not completed")
        .isNotNull();
    assertThat(pendingChange.getPending())
        .describedAs("pending operations")
        .anySatisfy(
            op -> {
              assertThat(op.getOperation()).isEqualTo(OperationEnum.PARTITION_RESTORE);
              assertThat(op.getPartitionId()).isEqualTo(partitionId);
            });
  }

  private void assertJobsActivatedFromEveryPartition(final CamundaClient client) {
    final Set<Long> activatedJobKeys = new HashSet<>();
    Awaitility.await("jobs from every partition are activated after restore")
        .timeout(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var jobs =
                  client
                      .newActivateJobsCommand()
                      .jobType(JOB_TYPE)
                      .maxJobsToActivate(2 * PARTITIONS_COUNT)
                      .send()
                      .join();
              jobs.getJobs()
                  .forEach(
                      job -> {
                        activatedJobKeys.add(job.getKey());
                        client.newCompleteCommand(job.getKey()).send().join();
                      });

              final var partitionsWithActivatedJob =
                  activatedJobKeys.stream()
                      .map(Protocol::decodePartitionId)
                      .collect(Collectors.toSet());
              assertThat(partitionsWithActivatedJob)
                  .describedAs(
                      "jobs are activated from every partition, proving both partitions' data"
                          + " was actually restored")
                  .containsExactlyInAnyOrderElementsOf(
                      IntStream.rangeClosed(1, PARTITIONS_COUNT).boxed().toList());
            });
  }

  /** Backup store can only be configured via UnifiedConfiguration */
  private static void configureBackupStore(final Camunda cfg) {
    final var backup = cfg.getData().getPrimaryStorage().getBackup();
    backup.setStore(BackupStoreType.FILESYSTEM);

    final var config = backup.getFilesystem();
    config.setBasePath(backupDir.toAbsolutePath().toString());
    backup.setFilesystem(config);
  }
}
