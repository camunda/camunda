/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.data.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.data.primarystorage.PrimaryStorageBackup.BackupStoreType;
import io.camunda.management.backups.BackupInfo;
import io.camunda.management.backups.StateCode;
import io.camunda.management.backups.TakeBackupRuntimeResponse;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

/**
 * Shared fixture and test cases for restoring when Elasticsearch is the secondary storage. Its
 * backups are not continuous, so a restore takes exactly one backup id, and any other request is
 * rejected.
 *
 * <p>Every test case here is exercised by two concrete subclasses, one per restore execution path:
 * {@link ElasticsearchRestoreIT} (a separate, standalone restore application) and {@link
 * InProcessElasticsearchRestoreIT} (triggered over a running broker's REST endpoint while it is in
 * {@code RECOVERING} mode). Both resolve the request with the same validation, so they must
 * restore, and reject, the same requests.
 */
@ZeebeIntegration
abstract class ElasticsearchRestoreTestBase {

  protected static final int PARTITION_COUNT = 2;
  private static final int SCALED_PARTITION_COUNT = PARTITION_COUNT + 1;
  private static final String PROCESS_ID = "process";
  private static final String JOB_TYPE = "task";

  @TestZeebe(partitionCount = PARTITION_COUNT)
  protected final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withSecondaryStorageType(SecondaryStorageType.elasticsearch)
          .withCreateSchema(true)
          .withUnifiedConfig(this::configureBroker);

  /**
   * The partition count the restore is configured with, which a scale up raises above the backup's
   * partitions.
   */
  protected int configuredPartitionCount = PARTITION_COUNT;

  private long nextBackupId = 1;

  @Test
  void shouldRestoreFromABackup() throws Exception {
    // given
    final List<Long> backedUp;
    final long backupId;
    try (final var client = broker.newClientBuilder().build()) {
      deployTestProcess(client);
      backedUp = createInstances(client);
      backupId = takeAndAwaitBackup();
      createInstances(client);
    }

    // when
    restore(RestoreRequest.backups(backupId));

    // then - every partition ends at the backup, without what was created after it
    assertOnlyInstancesRestored(backedUp);
  }

  @Test
  void shouldRejectRestoreWithoutABackup() throws Exception {
    // given
    try (final var client = broker.newClientBuilder().build()) {
      deployTestProcess(client);
      createInstances(client);
      takeAndAwaitBackup();
    }

    // when / then
    assertRestoreRejected(
        RestoreRequest.latest(), IllegalArgumentException.class, "No backupId specified");
  }

  @Test
  void shouldRejectMultipleBackups() throws Exception {
    // given
    final long firstBackup;
    final long secondBackup;
    try (final var client = broker.newClientBuilder().build()) {
      deployTestProcess(client);
      createInstances(client);
      firstBackup = takeAndAwaitBackup();
      createInstances(client);
      secondBackup = takeAndAwaitBackup();
    }

    // when / then
    assertRestoreRejected(
        RestoreRequest.backups(firstBackup, secondBackup),
        IllegalArgumentException.class,
        "Cannot restore from multiple backups against database type elasticsearch");
  }

  @Test
  void shouldRejectATimeRange() throws Exception {
    // given
    final Instant from = Instant.now();
    try (final var client = broker.newClientBuilder().build()) {
      deployTestProcess(client);
      createInstances(client);
      takeAndAwaitBackup();
    }

    // when / then
    assertRestoreRejected(
        RestoreRequest.timeRange(from, Instant.now()),
        IllegalArgumentException.class,
        "Time range restore (from/to) is not supported for elasticsearch");
  }

  @Test
  void shouldRejectANonExistingBackup() throws Exception {
    // given
    try (final var client = broker.newClientBuilder().build()) {
      deployTestProcess(client);
      createInstances(client);
      takeAndAwaitBackup();
    }

    // when / then
    assertRestoreRejected(
        RestoreRequest.backups(1234L),
        NoSuchElementException.class,
        "No completed backup found with backup id 1234");
  }

  @Test
  void shouldRejectABackupTogetherWithATimeRange() throws Exception {
    // given
    final Instant from = Instant.now();
    final long backupId;
    try (final var client = broker.newClientBuilder().build()) {
      deployTestProcess(client);
      createInstances(client);
      backupId = takeAndAwaitBackup();
    }

    // when / then - the restore app rejects it while parsing its arguments, in its own words
    assertRestoreRejected(
        new RestoreRequest(List.of(backupId), from, null), IllegalArgumentException.class, "both");
  }

  @Test
  void shouldRestoreTheBackupsPartitionsAfterAScaleUp() throws Exception {
    // given - a backup of the 2 partitions
    final List<Long> backedUp;
    final long backupId;
    try (final var client = broker.newClientBuilder().build()) {
      deployTestProcess(client);
      backedUp = createInstances(client);
      backupId = takeAndAwaitBackup();
    }

    // and - the cluster is scaled up to 3 partitions, with a later backup holding all 3
    InProcessRestoreTestUtil.scaleUpPartitions(
        ClusterActuator.of(broker), SCALED_PARTITION_COUNT, 1);
    try (final var client = broker.newClientBuilder().build()) {
      InProcessRestoreTestUtil.createInstancesOnEveryPartition(
          client, PROCESS_ID, SCALED_PARTITION_COUNT);
    }
    takeAndAwaitBackup();

    // when
    configuredPartitionCount = SCALED_PARTITION_COUNT;
    restore(RestoreRequest.backups(backupId));

    // then - only the backup's partitions are restored
    assertOnlyInstancesRestored(backedUp);
    InProcessRestoreTestUtil.assertOnlyRestoredPartitionDirectories(
        broker.getWorkingDirectory(),
        DEFAULT_PHYSICAL_TENANT_ID,
        PARTITION_COUNT,
        SCALED_PARTITION_COUNT);

    // and - the group routes over them again. After the standalone restore the broker takes the
    // routing state from the restored data once it is up, so it settles shortly after.
    final var restoredClusterActuator = ClusterActuator.of(broker);
    await("the group routes over the backup's partitions")
        .timeout(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                InProcessRestoreTestUtil.assertRoutesOverPartitions(
                    restoredClusterActuator, PARTITION_COUNT));
    InProcessRestoreTestUtil.assertNewInstancesLandOnPartitions(
        broker, PROCESS_ID, PARTITION_COUNT);
  }

  /**
   * Restores as requested and leaves the broker running and ready for verification (e.g. by
   * restarting it, for the standalone path, or by completing an in-process restore change plan and
   * waiting for the broker to return to {@code ACTIVE}).
   */
  abstract void restore(RestoreRequest request) throws Exception;

  /**
   * Asserts that the request is rejected before anything is restored, by the validation both paths
   * share, with the given exception.
   */
  abstract void assertRestoreRejected(
      RestoreRequest request, Class<? extends RuntimeException> expectedType, String message)
      throws Exception;

  /**
   * The backup store's base directory. Declared as a {@code static @TempDir} field on each concrete
   * subclass so JUnit injects it before that subclass's {@code broker} field initializer runs
   * {@link #configureBroker}.
   */
  protected abstract Path backupDir();

  /**
   * The URL of the Elasticsearch container. Each concrete subclass declares its own container, as
   * one shared static container would be stopped after the first subclass and not start again.
   */
  protected abstract String elasticsearchUrl();

  protected void configureStorages(final Camunda cfg) {
    cfg.getData().getSecondaryStorage().getElasticsearch().setUrl(elasticsearchUrl());
    final var backup = cfg.getData().getPrimaryStorage().getBackup();
    backup.setStore(BackupStoreType.FILESYSTEM);
    backup.getFilesystem().setBasePath(backupDir().toAbsolutePath().toString());
  }

  private void assertOnlyInstancesRestored(final List<Long> expectedInstances) {
    try (final var client = broker.newClientBuilder().build()) {
      final var activatedInstances =
          InProcessRestoreTestUtil.awaitJobsOfInstancesActivatable(
              client, JOB_TYPE, expectedInstances);
      assertThat(activatedInstances)
          .describedAs("only the instances up to the restore point survive the restore")
          .containsExactlyInAnyOrderElementsOf(expectedInstances);
    }
  }

  private List<Long> createInstances(final CamundaClient client) {
    return InProcessRestoreTestUtil.createInstancesOnEveryPartition(
        client, PROCESS_ID, PARTITION_COUNT);
  }

  /** Takes a snapshot and then a backup with the next id, and waits for the backup to complete. */
  private long takeAndAwaitBackup() {
    final var partitions = PartitionsActuator.of(broker);
    partitions.takeSnapshot();
    await("a snapshot is taken")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                assertThat(partitions.query().values())
                    .allSatisfy(status -> assertThat(status.snapshotId()).isNotNull()));

    final long backupId = nextBackupId++;
    final var backupActuator = BackupActuator.of(broker);
    assertThat(backupActuator.take(backupId)).isInstanceOf(TakeBackupRuntimeResponse.class);
    await("backup %d completes".formatted(backupId))
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(backupActuator.status(backupId))
                    .extracting(BackupInfo::getBackupId, BackupInfo::getState)
                    .containsExactly(backupId, StateCode.COMPLETED));
    return backupId;
  }

  private static void deployTestProcess(final CamundaClient client) {
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                .endEvent()
                .done(),
            "process.bpmn")
        .send()
        .join();
  }

  private void configureBroker(final Camunda cfg) {
    cfg.getCluster().setPartitionCount(PARTITION_COUNT);
    configureStorages(cfg);
  }

  /**
   * What a restore is asked for: either the backups to restore from, or a time range whose bounds
   * may each be left out.
   */
  protected record RestoreRequest(List<Long> backupIds, Instant from, Instant to) {

    static RestoreRequest latest() {
      return new RestoreRequest(List.of(), null, null);
    }

    static RestoreRequest timeRange(final Instant from, final Instant to) {
      return new RestoreRequest(List.of(), from, to);
    }

    static RestoreRequest backups(final Long... backupIds) {
      return new RestoreRequest(List.of(backupIds), null, null);
    }
  }
}
