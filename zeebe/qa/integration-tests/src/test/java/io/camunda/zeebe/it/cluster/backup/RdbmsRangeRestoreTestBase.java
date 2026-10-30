/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride.RDBMS_EXPORTER_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.application.commons.rdbms.RdbmsDataSources;
import io.camunda.client.CamundaClient;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.data.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.data.primarystorage.PrimaryStorageBackup.BackupStoreType;
import io.camunda.configuration.data.primarystorage.backup.Filesystem;
import io.camunda.db.rdbms.sql.ExporterPositionMapper;
import io.camunda.db.rdbms.write.RdbmsMapperBundle;
import io.camunda.management.backups.BackupInfo;
import io.camunda.management.backups.CheckpointType;
import io.camunda.management.backups.PartitionBackupInfo;
import io.camunda.management.backups.PartitionCheckpointState;
import io.camunda.management.backups.StateCode;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequest;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestPartitions;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.actuator.ExportersActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.unit.DataSize;

/**
 * Shared fixture and test cases for restoring from a time range of backups when RDBMS is configured
 * as the secondary storage. This exercises the RDBMS-aware restore path where {@code
 * ExporterPositionMapper} is used by {@code RestorePointResolver} to determine safe restore points
 * per partition.
 *
 * <p>Every test case here is exercised by two concrete subclasses, one per restore execution path:
 * {@link RdbmsRangeRestoreIT} (a separate, standalone restore application) and {@link
 * InProcessRdbmsRangeRestoreIT} (triggered over a running broker's REST endpoint while it is in
 * {@code RECOVERING} mode). The RDBMS-aware resolution of the restore point from a time range is
 * identical either way; only the execution of the resolved restore plan differs, so subclasses only
 * implement the two restore-execution hook methods below.
 *
 * <p>Uses a filesystem backup store (no containers needed) and H2 in-memory database for RDBMS.
 */
@ZeebeIntegration
abstract class RdbmsRangeRestoreTestBase implements ClockSupport {

  protected static final int PARTITION_COUNT = 2;
  private static final int SCALED_PARTITION_COUNT = PARTITION_COUNT + 1;
  private static final String PROCESS_ID = "process";
  private static final String JOB_TYPE = "task";
  private static final Duration MARKER_INTERVAL = Duration.ofHours(1);
  private static final Logger LOG = LoggerFactory.getLogger(RdbmsRangeRestoreTestBase.class);

  // H2 in-memory DB URL. DB_CLOSE_DELAY=-1 keeps the DB alive across a broker's stop/restart
  // cycle within a single test. MODE=PostgreSQL for SQL compatibility with the RDBMS module.
  protected final String h2Url =
      "jdbc:h2:mem:rdbms-restore-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL";

  @TestZeebe(partitionCount = PARTITION_COUNT)
  protected final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withSecondaryStorageType(SecondaryStorageType.rdbms)
          .withUnifiedConfig(this::configureBroker);

  /**
   * The partition count the restore is configured with, which a scale up raises above the backups'
   * partitions.
   */
  protected int configuredPartitionCount = PARTITION_COUNT;

  private BackupActuator backupActuator;
  private ExporterPositionMapper exporterPositionMapper;

  @BeforeEach
  void setUpBase() {
    backupActuator = BackupActuator.of(broker);
    final Map<String, RdbmsMapperBundle> bundles = broker.bean("rdbmsMapperBundles");
    final var defaultBundle = bundles.get(DEFAULT_PHYSICAL_TENANT_ID);
    if (defaultBundle == null) {
      throw new IllegalStateException(
          "Missing default physical tenant '%s' in rdbmsMapperBundles; known tenants: %s"
              .formatted(DEFAULT_PHYSICAL_TENANT_ID, bundles.keySet()));
    }
    exporterPositionMapper = defaultBundle.exporterPositionMapper();
    pinClock(broker);
  }

  @AfterEach
  void tearDownBase() throws SQLException {
    final var dataSources = broker.bean(RdbmsDataSources.class);
    if (dataSources != null) {
      final var ds = dataSources.dataSourceFor(DEFAULT_PHYSICAL_TENANT_ID);
      try (final var connection = ds.getConnection()) {
        connection.createStatement().execute("DROP ALL OBJECTS");
      }
    }
  }

  @Test
  void shouldNotRestoreIfMissingBackupAfterRestorePosition() throws Exception {
    // given - deploy a process and create instances, then take continuous backups.
    final RestoreTarget timeRange;
    try (final var client = broker.newClientBuilder().build()) {
      final var processKey = deployTestProcess(client);

      // Create some process instances to have data to verify after restore
      timeRange = createProcessInstancesAndTakeBackups(client, processKey);

      takeAndAwaitBackup();
      progressClock(broker, 2000);
      client.newCreateInstanceCommand().processDefinitionKey(processKey).send().join();
      client.newCreateInstanceCommand().processDefinitionKey(processKey).send().join();
    }

    // when/then - restoring from a range that ends before the backup covering it exists must fail
    assertRestoreRejected(timeRange, IllegalStateException.class, "No usable range found");
  }

  @Test
  void shouldRestoreFromATimeRange() throws Exception {
    // given - some process instances with backups between them
    final long processKey;
    final RestoreTarget timeRange;
    try (final var client = broker.newClientBuilder().build()) {
      processKey = deployTestProcess(client);
      timeRange = createProcessInstancesAndTakeBackups(client, processKey);
    }
    takeAndAwaitBackup();

    disableRdbmsExporter();

    awaitBackupCoversExportedPositionsOnEveryPartition();

    // when - restore from the recorded time range
    restore(timeRange);

    // then - jobs from process instances created before the restore window's end are still there,
    // proving the RDBMS-resolved restore point was applied correctly
    completeJobs(4);
  }

  @Test
  void shouldRestoreWithoutArguments() throws Exception {
    // given - deploy a process and create instances, then take continuous backups.
    try (final var client = broker.newClientBuilder().build()) {
      final var processKey = deployTestProcess(client);
      createProcessInstancesAndTakeBackups(client, processKey);
    }
    takeAndAwaitBackup();

    disableRdbmsExporter();

    awaitBackupCoversExportedPositionsOnEveryPartition();

    // when - restore without specifying a time range (resolves the latest common checkpoint)
    restore(RestoreTarget.latest());

    // then
    completeJobs(4);
  }

  @Test
  void shouldRestoreToAMarkerCheckpoint() throws Exception {
    // given - instances created before and after a marker; the next backup holds both
    final List<Long> createdBeforeMarker;
    final Instant markerTimestamp;
    try (final var client = broker.newClientBuilder().build()) {
      deployTestProcess(client);
      takeAndAwaitBackup();
      disableRdbmsExporter();
      awaitBackupCoversExportedPositionsOnEveryPartition();

      createdBeforeMarker =
          InProcessRestoreTestUtil.createInstancesOnEveryPartition(
              client, PROCESS_ID, PARTITION_COUNT);
      markerTimestamp = awaitMarkerCoveringExportedPositions();
      InProcessRestoreTestUtil.createInstancesOnEveryPartition(client, PROCESS_ID, PARTITION_COUNT);
      takeAndAwaitBackup();
    }

    // when
    restore(RestoreTarget.closestTo(markerTimestamp));

    // then - every partition ends at the marker
    try (final var client = broker.newClientBuilder().build()) {
      final var activatedInstances =
          InProcessRestoreTestUtil.awaitJobsOfInstancesActivatable(
              client, JOB_TYPE, createdBeforeMarker);
      assertThat(activatedInstances)
          .describedAs("only the instances created before the marker survive the restore")
          .containsExactlyInAnyOrderElementsOf(createdBeforeMarker);
    }
  }

  @Test
  void shouldRestoreTheLatestCheckpointWhenOnlyFromIsGiven() throws Exception {
    // given - instances backed up before and after `from`, and some created after the last backup
    final List<Long> backedUp = new ArrayList<>();
    final Instant from;
    try (final var client = broker.newClientBuilder().build()) {
      deployTestProcess(client);
      backedUp.addAll(createInstancesOnEveryPartition(client));
      takeAndAwaitBackup();
      from = currentTime(broker);
      backedUp.addAll(createInstancesOnEveryPartition(client));
      takeAndAwaitBackup();

      disableRdbmsExporter();
      awaitBackupCoversExportedPositionsOnEveryPartition();
      createInstancesOnEveryPartition(client);
    }

    // when
    restore(RestoreTarget.timeRange(from, null));

    // then - every partition ends at the latest backup, without what was created after it
    assertOnlyInstancesRestored(backedUp);
  }

  @Test
  void shouldRestoreFromMultipleBackups() throws Exception {
    // given - the RDBMS holds an exported position for every partition
    final List<Long> backedUp = new ArrayList<>();
    final long firstBackup;
    final long secondBackup;
    final long thirdBackup;
    try (final var client = broker.newClientBuilder().build()) {
      deployTestProcess(client);
      disableRdbmsExporter();

      backedUp.addAll(createInstancesOnEveryPartition(client));
      firstBackup = takeAndAwaitBackup().getBackupId();
      backedUp.addAll(createInstancesOnEveryPartition(client));
      secondBackup = takeAndAwaitBackup().getBackupId();
      backedUp.addAll(createInstancesOnEveryPartition(client));
      thirdBackup = takeAndAwaitBackup().getBackupId();
      createInstancesOnEveryPartition(client);
    }

    // when
    restore(RestoreTarget.backups(firstBackup, secondBackup, thirdBackup));

    // then - every partition ends at the last backup, without what was created after it
    assertOnlyInstancesRestored(backedUp);
  }

  @Test
  void shouldNotRestoreOverAGapBetweenBackups() throws Exception {
    // given - three backups spanning multiple segments, with snapshots compacting the log between
    final long firstBackup;
    final long thirdBackup;
    try (final var client = broker.newClientBuilder().build()) {
      deployTestProcess(client);
      disableRdbmsExporter();

      createLargeInstances(client);
      firstBackup = takeAndAwaitBackup().getBackupId();

      PartitionsActuator.of(broker).takeSnapshot();
      createLargeInstances(client);
      takeAndAwaitBackup();

      PartitionsActuator.of(broker).takeSnapshot();
      createLargeInstances(client);
      thirdBackup = takeAndAwaitBackup().getBackupId();
    }

    // when / then - skipping the second backup leaves a gap in the restored log
    assertRestoreNotApplied(
        RestoreTarget.backups(firstBackup, thirdBackup),
        "Cannot find a record at checkpoint position");
  }

  @Test
  void shouldRestoreFromABackup() throws Exception {
    // given - the RDBMS holds an exported position for every partition
    final List<Long> backedUp;
    final long backupId;
    try (final var client = broker.newClientBuilder().build()) {
      deployTestProcess(client);
      disableRdbmsExporter();

      backedUp = createInstancesOnEveryPartition(client);
      backupId = takeAndAwaitBackup().getBackupId();
      createInstancesOnEveryPartition(client);
    }

    // when
    restore(RestoreTarget.backups(backupId));

    // then - every partition ends at the backup, without what was created after it
    assertOnlyInstancesRestored(backedUp);
  }

  @Test
  void shouldRejectANonExistingBackup() throws Exception {
    // given
    try (final var client = broker.newClientBuilder().build()) {
      deployTestProcess(client);
      createInstancesOnEveryPartition(client);
      takeAndAwaitBackup();
    }

    // when / then
    assertRestoreRejected(
        RestoreTarget.backups(1234L),
        NoSuchElementException.class,
        "No completed backup found with backup id 1234");
  }

  @Test
  void shouldRejectABackupTogetherWithATimeRange() throws Exception {
    // given
    final long backupId;
    final Instant from;
    try (final var client = broker.newClientBuilder().build()) {
      deployTestProcess(client);
      from = currentTime(broker);
      createInstancesOnEveryPartition(client);
      backupId = takeAndAwaitBackup().getBackupId();
    }

    // when / then - the restore app rejects it while parsing its arguments, in its own words
    assertRestoreRejected(
        new RestoreTarget(List.of(backupId), from, null), IllegalArgumentException.class, "both");
  }

  @Test
  void shouldRejectATimeRangeEndingBeforeItStarts() throws Exception {
    // given
    final Instant from;
    final Instant to;
    try (final var client = broker.newClientBuilder().build()) {
      deployTestProcess(client);
      createInstancesOnEveryPartition(client);
      to = currentTime(broker);
      takeAndAwaitBackup();
      from = currentTime(broker);
    }

    // when / then
    assertRestoreRejected(
        RestoreTarget.timeRange(from, to),
        IllegalArgumentException.class,
        "Invalid time range: from");
  }

  @Test
  void shouldRejectTimeRangeStartingBeforeTheBackups() throws Exception {
    // given
    final Instant beforeTheBackups = currentTime(broker).minus(MARKER_INTERVAL);
    final Instant to;
    try (final var client = broker.newClientBuilder().build()) {
      deployTestProcess(client);
      disableRdbmsExporter();
      awaitBackupCoversExportedPositionsOnEveryPartition();
      to = currentTime(broker);
    }

    // when / then - no range of backups starts at or before `from`
    assertRestoreRejected(
        RestoreTarget.timeRange(beforeTheBackups, to),
        IllegalStateException.class,
        "No usable range found");
  }

  @Test
  void shouldRestoreTheBackupsPartitionsOfABackupAfterAScaleUp() throws Exception {
    // given - a backup covering the exported positions of the 2 partitions
    final List<Long> backedUp;
    try (final var client = broker.newClientBuilder().build()) {
      deployTestProcess(client);
      backedUp = createInstancesOnEveryPartition(client);
    }
    disableRdbmsExporter();
    awaitBackupCoversExportedPositionsOnEveryPartition();
    final long backupId = takeAndAwaitBackup().getBackupId();

    // and - the cluster is scaled up to 3 partitions, and the RDBMS restored to the backup's point
    scaleUp(ClusterActuator.of(broker), SCALED_PARTITION_COUNT);
    deleteExportedPositionsOfScaledUpPartitions();

    // when
    configuredPartitionCount = SCALED_PARTITION_COUNT;
    restore(RestoreTarget.backups(backupId));

    // then - only the backup's partitions are restored, and the group routes over them again
    assertOnlyInstancesRestored(backedUp);
    assertRestoredTheBackupsPartitions();
  }

  @Test
  void shouldRestoreTheBackupsPartitionsAfterAScaleUp() throws Exception {
    // given - a backup covering the exported positions of the 2 partitions
    final List<Long> backedUp;
    try (final var client = broker.newClientBuilder().build()) {
      deployTestProcess(client);
      backedUp = createInstancesOnEveryPartition(client);
    }
    disableRdbmsExporter();
    awaitBackupCoversExportedPositionsOnEveryPartition();

    // and - the cluster is scaled up to 3 partitions
    scaleUp(ClusterActuator.of(broker), SCALED_PARTITION_COUNT);

    // and - the RDBMS is restored to the backup's point, so it only holds exported positions for
    // the backup's 2 partitions, not for the one added by the scale up
    deleteExportedPositionsOfScaledUpPartitions();

    // when - restoring the latest common checkpoint of the partitions the RDBMS holds
    configuredPartitionCount = SCALED_PARTITION_COUNT;
    restore(RestoreTarget.latest());

    // then - only the backup's partitions are restored, and the group routes over them again
    assertOnlyInstancesRestored(backedUp);
    assertRestoredTheBackupsPartitions();
  }

  /**
   * Restores to {@code target} and leaves the broker running and ready for verification (e.g. by
   * restarting it, for the standalone path, or by completing an in-process restore change plan and
   * waiting for the broker to return to {@code ACTIVE}).
   */
  abstract void restore(RestoreTarget target) throws Exception;

  /**
   * Asserts that the restore to {@code target} is rejected before anything is restored, by the
   * validation both paths share, with the given exception.
   */
  abstract void assertRestoreRejected(
      RestoreTarget target, Class<? extends RuntimeException> expectedType, String message)
      throws Exception;

  /**
   * Asserts that {@code target} passes validation but the resolved backups cannot be restored. The
   * standalone path fails with {@code expectedMessage}; the in-process path keeps retrying, so its
   * partitions never leave {@code RESTORING}.
   */
  abstract void assertRestoreNotApplied(RestoreTarget target, String expectedMessage)
      throws Exception;

  /**
   * The backup store's base directory. Declared as a {@code static @TempDir} field on each concrete
   * subclass (not here) so JUnit injects it before that subclass's {@code broker} field initializer
   * runs {@link #configureBroker}, and so the two subclasses never share one physical temp
   * directory.
   */
  protected abstract Path backupDir();

  /**
   * Disables the RDBMS exporter once it has recorded an exported position on every partition.
   *
   * <p>The exporter records its position only when it opens, and fails to open until the RDBMS
   * schema is ready. Its director retries the open with a backoff on the pinned actor clock, so
   * until the clock moves on, a retry can stay pending. Disabled before it opened, the exporter
   * never records a position on that partition, and no backup can ever cover it.
   */
  protected final void disableRdbmsExporter() {
    await("the RDBMS exporter recorded a position on every partition")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              progressClock(broker, 1000);
              assertThat(IntStream.rangeClosed(1, PARTITION_COUNT).boxed())
                  .allSatisfy(
                      partitionId ->
                          assertThat(exporterPositionMapper.findOne(partitionId))
                              .describedAs("exported position for partition %d", partitionId)
                              .isNotNull());
            });
    ExportersActuator.of(broker).disableExporter(RDBMS_EXPORTER_NAME);
  }

  /**
   * Waits until a freshly-taken backup exists whose per-partition checkpoint position is at or
   * ahead of that partition's last exported (to RDBMS) position - for every partition. Only then
   * does a time-range restore have a backup that covers the exported data on all partitions.
   */
  protected final void awaitBackupCoversExportedPositionsOnEveryPartition() {
    Awaitility.await("backup covers the exported position on every partition")
        .pollDelay(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(300))
        .untilAsserted(
            () -> {
              final var backup = takeAndAwaitBackup();
              assertThat(backup.getDetails())
                  .extracting(PartitionBackupInfo::getPartitionId)
                  .containsExactlyInAnyOrderElementsOf(
                      IntStream.rangeClosed(1, PARTITION_COUNT).boxed().toList());
              assertThat(backup.getDetails())
                  .allSatisfy(
                      details -> {
                        final var exportedPosition =
                            exporterPositionMapper.findOne(details.getPartitionId());
                        assertThat(exportedPosition)
                            .describedAs(
                                "exported position for partition %d", details.getPartitionId())
                            .isNotNull();
                        assertThat(details.getState()).isEqualTo(StateCode.COMPLETED);
                        assertThat(details.getCheckpointPosition())
                            .isGreaterThanOrEqualTo(exportedPosition.lastExportedPosition());
                      });
            });
  }

  private void deleteExportedPositionsOfScaledUpPartitions() throws SQLException {
    final var dataSource =
        broker.bean(RdbmsDataSources.class).dataSourceFor(DEFAULT_PHYSICAL_TENANT_ID);
    try (final var connection = dataSource.getConnection();
        final var statement = connection.createStatement()) {
      statement.executeUpdate(
          "DELETE FROM EXPORTER_POSITION WHERE PARTITION_ID > " + PARTITION_COUNT);
      connection.commit();
    }
  }

  /**
   * Asserts the broker holds and routes over the backup's partitions only. After the standalone
   * restore the broker takes the routing state from the restored data once it is up, so it settles
   * shortly after.
   */
  private void assertRestoredTheBackupsPartitions() {
    InProcessRestoreTestUtil.assertOnlyRestoredPartitionDirectories(
        broker.getWorkingDirectory(),
        DEFAULT_PHYSICAL_TENANT_ID,
        PARTITION_COUNT,
        SCALED_PARTITION_COUNT);
    final var clusterActuator = ClusterActuator.of(broker);
    await("the group routes over the backup's partitions")
        .timeout(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                InProcessRestoreTestUtil.assertRoutesOverPartitions(
                    clusterActuator, PARTITION_COUNT));
    InProcessRestoreTestUtil.assertNewInstancesLandOnPartitions(
        broker, PROCESS_ID, PARTITION_COUNT);
  }

  /**
   * Scales the cluster up. The broker's clock is pinned, so the configuration change only makes
   * progress, e.g. through a retry backoff, when the clock is moved on, which every poll here does.
   */
  private void scaleUp(final ClusterActuator clusterActuator, final int partitionCount) {
    final var scaleUp =
        clusterActuator.patchCluster(
            new ClusterConfigPatchRequest()
                .partitions(
                    new ClusterConfigPatchRequestPartitions()
                        .count(partitionCount)
                        .replicationFactor(1)),
            false,
            false);
    await("cluster is scaled up to %d partitions".formatted(partitionCount))
        .timeout(Duration.ofSeconds(90))
        .pollInterval(Duration.ofSeconds(2))
        .untilAsserted(
            () -> {
              progressClock(broker, 3000);
              ClusterActuatorAssert.assertThat(clusterActuator)
                  .hasCompletedChanges(scaleUp.getChangeId())
                  .doesNotHavePendingChanges();
            });
  }

  protected void completeJobs(final int expectedJobCount) {
    try (final var client = broker.newClientBuilder().build()) {
      int tries = 10;
      int remaining = expectedJobCount;
      while (remaining > 0 && tries-- > 0) {
        final var jobActivationResult =
            client
                .newActivateJobsCommand()
                .jobType(JOB_TYPE)
                .maxJobsToActivate(remaining)
                .send()
                .join();
        remaining -= jobActivationResult.getJobs().size();
        for (final var job : jobActivationResult.getJobs()) {
          client.newCompleteCommand(job.getKey()).send().join();
        }
      }
      assertThat(remaining).describedAs("all expected jobs were activated and completed").isZero();
    }
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

  private static List<Long> createInstancesOnEveryPartition(final CamundaClient client) {
    return InProcessRestoreTestUtil.createInstancesOnEveryPartition(
        client, PROCESS_ID, PARTITION_COUNT);
  }

  /** Creates enough data to span multiple log segments, so a snapshot can compact some away. */
  private static void createLargeInstances(final CamundaClient client) {
    for (int i = 0; i < 100; i++) {
      client
          .newCreateInstanceCommand()
          .bpmnProcessId(PROCESS_ID)
          .latestVersion()
          .variables(Map.of("test", RandomStringUtils.insecure().nextAlphabetic(100_000)))
          .send()
          .join();
    }
  }

  protected static final long deployTestProcess(final CamundaClient client) {
    return client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                .endEvent()
                .done(),
            "process.bpmn")
        .send()
        .join()
        .getProcesses()
        .getFirst()
        .getProcessDefinitionKey();
  }

  protected final RestoreTarget createProcessInstancesAndTakeBackups(
      final CamundaClient client, final long processKey) {

    // Create some process instances to have data to verify after restore
    client.newCreateInstanceCommand().processDefinitionKey(processKey).send().join();
    client.newCreateInstanceCommand().processDefinitionKey(processKey).send().join();

    takeAndAwaitBackup();
    // Record "from" before the first backup, with a small sleep to create clear separation

    final var from = currentTime(broker);
    LOG.info("Time range from = {}", from);

    client.newCreateInstanceCommand().processDefinitionKey(processKey).send().join();
    // Take first backup
    takeAndAwaitBackup();

    // Create another instance and take second backup
    client.newCreateInstanceCommand().processDefinitionKey(processKey).send().join();
    takeAndAwaitBackup();

    // Record "to" after second backup
    final Instant to = currentTime(broker);
    LOG.info("Time range to = {}", to);
    return RestoreTarget.timeRange(from, to);
  }

  /**
   * Moves the pinned clock past the marker interval, so the checkpoint scheduler takes a marker
   * checkpoint, and returns the marker's timestamp once every partition has it. Asserts that the
   * marker is at or after every partition's exported position, which a restore to it requires.
   */
  private Instant awaitMarkerCoveringExportedPositions() {
    final var previousCheckpointId =
        backupActuator.state().getCheckpointStates().stream()
            .mapToLong(PartitionCheckpointState::getCheckpointId)
            .max()
            .orElseThrow();
    progressClock(broker, MARKER_INTERVAL.plusSeconds(1).toMillis());
    final var markers =
        Awaitility.await("a marker checkpoint is taken on every partition")
            .atMost(Duration.ofSeconds(30))
            .ignoreExceptions()
            .until(
                () -> backupActuator.state().getCheckpointStates(),
                states ->
                    states.size() == PARTITION_COUNT
                        && states.stream()
                            .allMatch(
                                state ->
                                    state.getCheckpointType() == CheckpointType.MARKER
                                        && state.getCheckpointId() > previousCheckpointId));
    final var marker = markers.getFirst();
    assertThat(markers)
        .allSatisfy(
            partitionMarker -> {
              assertThat(partitionMarker.getCheckpointId()).isEqualTo(marker.getCheckpointId());
              assertThat(partitionMarker.getCheckpointPosition())
                  .describedAs("marker position on partition %d", partitionMarker.getPartitionId())
                  .isGreaterThanOrEqualTo(
                      exporterPositionMapper
                          .findOne(partitionMarker.getPartitionId())
                          .lastExportedPosition());
            });
    return marker.getCheckpointTimestamp().toInstant();
  }

  protected final BackupInfo takeAndAwaitBackup() {
    progressClock(broker, 2000);
    final var res = backupActuator.take();
    final long backupId = Optional.ofNullable(res.getBackupId()).orElseThrow();

    await("backup is completed")
        .timeout(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(backupActuator.status(backupId).getState())
                    .isEqualTo(StateCode.COMPLETED));

    return backupActuator.status(backupId);
  }

  private void configureBroker(final Camunda cfg) {
    cfg.getSystem().setClockControlled(true);
    cfg.getCluster().setPartitionCount(PARTITION_COUNT);
    configureRdbms(cfg);
    // Filesystem backup store
    final var fsConfig = new Filesystem();
    fsConfig.setBasePath(backupDir().toAbsolutePath().toString());
    cfg.getData().getPrimaryStorage().getBackup().setFilesystem(fsConfig);
    cfg.getData().getPrimaryStorage().getBackup().setStore(BackupStoreType.FILESYSTEM);

    // Enable continuous backups
    cfg.getData().getPrimaryStorage().getBackup().setContinuous(true);
    // A marker is taken at startup, as no checkpoint exists yet, and then an interval after the
    // latest checkpoint, which only a test moving the pinned clock by the interval reaches
    cfg.getData().getPrimaryStorage().getBackup().setCheckpointInterval(MARKER_INTERVAL);

    // Use smaller log segments for faster checkpoint creation.
    // Max message size must be <= log segment size (enforced by RaftPartitionFactory).
    cfg.getData().getPrimaryStorage().getLogStream().setLogSegmentSize(DataSize.ofMegabytes(1));
    cfg.getCluster().getNetwork().setMaxMessageSize(DataSize.ofKilobytes(500));

    // Disable auto-configure camunda exporter to avoid conflicts
    cfg.getData().getSecondaryStorage().setAutoconfigureCamundaExporter(false);
  }

  protected void configureRdbms(final Camunda cfg) {
    cfg.getData().getSecondaryStorage().setType(SecondaryStorageType.rdbms);
    final var rdbms = cfg.getData().getSecondaryStorage().getRdbms();
    rdbms.setUrl(h2Url);
    rdbms.setUsername("sa");
    rdbms.setPassword("");
  }

  /**
   * What a restore is asked for: either the backups to restore from, or a time range whose bounds
   * may each be {@code null} to leave it out; without {@code to}, the restore resolves the latest
   * common checkpoint.
   */
  protected record RestoreTarget(List<Long> backupIds, Instant from, Instant to) {

    static RestoreTarget latest() {
      return timeRange(null, null);
    }

    static RestoreTarget closestTo(final Instant to) {
      return timeRange(null, to);
    }

    static RestoreTarget timeRange(final Instant from, final Instant to) {
      return new RestoreTarget(List.of(), from, to);
    }

    static RestoreTarget backups(final Long... backupIds) {
      return new RestoreTarget(List.of(backupIds), null, null);
    }
  }
}
