/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.data.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.data.primarystorage.PrimaryStorageBackup.BackupStoreType;
import io.camunda.management.backups.CheckpointType;
import io.camunda.management.backups.PartitionCheckpointState;
import io.camunda.management.backups.StateCode;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/**
 * Shared fixture and test cases for restoring without a secondary storage. Without exported
 * positions to keep consistent with, the restore point is resolved from the backups alone, over the
 * configured partitions.
 *
 * <p>Every test case here is exercised by two concrete subclasses, one per restore execution path:
 * {@link NoSecondaryStorageRestoreIT} (a separate, standalone restore application) and {@link
 * InProcessNoSecondaryStorageRestoreIT} (triggered over a running broker's REST endpoint while it
 * is in {@code RECOVERING} mode). Both resolve the request with the same validation, so they must
 * restore, and reject, the same requests.
 *
 * <p>Uses a filesystem backup store, so no containers are needed.
 */
@ZeebeIntegration
abstract class NoSecondaryStorageRestoreTestBase implements ClockSupport {

  protected static final int PARTITION_COUNT = 2;
  private static final String PROCESS_ID = "process";
  private static final String JOB_TYPE = "task";
  private static final Duration MARKER_INTERVAL = Duration.ofHours(1);

  @TestZeebe(partitionCount = PARTITION_COUNT)
  protected final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withSecondaryStorageType(SecondaryStorageType.none)
          .withUnifiedConfig(this::configureBroker);

  private BackupActuator backupActuator;

  @BeforeEach
  void setUpBase() {
    backupActuator = BackupActuator.of(broker);
    pinClock(broker);
  }

  @Test
  void shouldRestoreFromATimeRange() throws Exception {
    // given - instances backed up before and within the time range, and some after it
    final List<Long> createdUntilTo = new ArrayList<>();
    final Instant from;
    final Instant to;
    try (final var client = broker.newClientBuilder().build()) {
      deployTestProcess(client);
      createdUntilTo.addAll(createInstances(client));
      takeAndAwaitBackup();
      from = currentTime(broker);

      createdUntilTo.addAll(createInstances(client));
      takeAndAwaitBackup();
      createdUntilTo.addAll(createInstances(client));
      takeAndAwaitBackup();
      to = currentTime(broker);

      createInstances(client);
      takeAndAwaitBackup();
    }

    // when
    restore(RestoreRequest.timeRange(from, to));

    // then - every partition ends at the backup closest to `to`
    assertOnlyInstancesRestored(createdUntilTo);
  }

  @Test
  void shouldRestoreTheLatestCheckpointWhenOnlyFromIsGiven() throws Exception {
    // given
    final List<Long> backedUp = new ArrayList<>();
    final Instant from;
    try (final var client = broker.newClientBuilder().build()) {
      deployTestProcess(client);
      backedUp.addAll(createInstances(client));
      takeAndAwaitBackup();
      from = currentTime(broker);

      backedUp.addAll(createInstances(client));
      takeAndAwaitBackup();

      createInstances(client);
    }

    // when
    restore(RestoreRequest.timeRange(from, null));

    // then - every partition ends at the latest backup, without what was created after it
    assertOnlyInstancesRestored(backedUp);
  }

  @Test
  void shouldRestoreToAMarkerCheckpoint() throws Exception {
    // given - instances created before and after a marker; the next backup holds both
    final List<Long> createdBeforeMarker = new ArrayList<>();
    final Instant from;
    final Instant markerTimestamp;
    try (final var client = broker.newClientBuilder().build()) {
      deployTestProcess(client);
      createdBeforeMarker.addAll(createInstances(client));
      takeAndAwaitBackup();
      from = currentTime(broker);

      createdBeforeMarker.addAll(createInstances(client));
      markerTimestamp = awaitMarker();
      createInstances(client);
      takeAndAwaitBackup();
    }

    // when
    restore(RestoreRequest.timeRange(from, markerTimestamp));

    // then - every partition ends at the marker
    assertOnlyInstancesRestored(createdBeforeMarker);
  }

  @Test
  void shouldRestoreFromMultipleBackups() throws Exception {
    // given
    final List<Long> backedUp = new ArrayList<>();
    final long firstBackup;
    final long secondBackup;
    final long thirdBackup;
    try (final var client = broker.newClientBuilder().build()) {
      deployTestProcess(client);
      backedUp.addAll(createInstances(client));
      firstBackup = takeAndAwaitBackup();
      backedUp.addAll(createInstances(client));
      secondBackup = takeAndAwaitBackup();
      backedUp.addAll(createInstances(client));
      thirdBackup = takeAndAwaitBackup();
      createInstances(client);
    }

    // when
    restore(RestoreRequest.backups(firstBackup, secondBackup, thirdBackup));

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
      createLargeInstances(client);
      firstBackup = takeAndAwaitBackup();

      PartitionsActuator.of(broker).takeSnapshot();
      createLargeInstances(client);
      takeAndAwaitBackup();

      PartitionsActuator.of(broker).takeSnapshot();
      createLargeInstances(client);
      thirdBackup = takeAndAwaitBackup();
    }

    // when / then - skipping the second backup leaves a gap in the restored log
    assertRestoreNotApplied(
        RestoreRequest.backups(firstBackup, thirdBackup),
        "Cannot find a record at checkpoint position");
  }

  @Test
  void shouldRejectRestoreWithoutArguments() throws Exception {
    // given
    try (final var client = broker.newClientBuilder().build()) {
      deployTestProcess(client);
      createInstances(client);
      takeAndAwaitBackup();
    }

    // when / then - without exported positions, there is no latest checkpoint to restore
    assertRestoreRejected(
        RestoreRequest.latest(),
        IllegalArgumentException.class,
        "no backupId was specified and no exported-position data is available");
  }

  @Test
  void shouldRejectTimeRangeWithoutFrom() throws Exception {
    // given
    final Instant to;
    try (final var client = broker.newClientBuilder().build()) {
      deployTestProcess(client);
      createInstances(client);
      takeAndAwaitBackup();
      to = currentTime(broker);
    }

    // when / then - without exported positions, only `from` picks the range to restore from
    assertRestoreRejected(
        RestoreRequest.timeRange(null, to),
        IllegalArgumentException.class,
        "from is required for a time range restore when no exported-position data is available");
  }

  @Test
  void shouldRejectTimeRangeStartingBeforeTheBackups() throws Exception {
    // given
    final Instant beforeTheBackups = currentTime(broker).minus(MARKER_INTERVAL);
    final Instant to;
    try (final var client = broker.newClientBuilder().build()) {
      deployTestProcess(client);
      createInstances(client);
      takeAndAwaitBackup();
      to = currentTime(broker);
    }

    // when / then - no range of backups starts at or before `from`
    assertRestoreRejected(
        RestoreRequest.timeRange(beforeTheBackups, to),
        IllegalStateException.class,
        "No usable range found");
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
   * Asserts that the request passes validation but the resolved backups cannot be restored. The
   * standalone path fails with the given message; the in-process path keeps retrying, so its
   * partitions never leave {@code RESTORING}.
   */
  abstract void assertRestoreNotApplied(RestoreRequest request, String message) throws Exception;

  /**
   * The backup store's base directory. Declared as a {@code static @TempDir} field on each concrete
   * subclass so JUnit injects it before that subclass's {@code broker} field initializer runs
   * {@link #configureBroker}.
   */
  protected abstract Path backupDir();

  protected void configureBackupStore(final Camunda cfg) {
    final var backup = cfg.getData().getPrimaryStorage().getBackup();
    backup.setStore(BackupStoreType.FILESYSTEM);
    backup.getFilesystem().setBasePath(backupDir().toAbsolutePath().toString());
    backup.setContinuous(true);
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

  private long takeAndAwaitBackup() {
    progressClock(broker, 2000);
    final long backupId = Optional.ofNullable(backupActuator.take().getBackupId()).orElseThrow();
    await("backup is completed")
        .timeout(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(backupActuator.status(backupId).getState())
                    .isEqualTo(StateCode.COMPLETED));
    return backupId;
  }

  /**
   * Moves the pinned clock past the marker interval, so the checkpoint scheduler takes a marker
   * checkpoint, and returns the marker's timestamp once every partition has it.
   */
  private Instant awaitMarker() {
    final var previousCheckpointId =
        backupActuator.state().getCheckpointStates().stream()
            .mapToLong(PartitionCheckpointState::getCheckpointId)
            .max()
            .orElseThrow();
    progressClock(broker, MARKER_INTERVAL.plusSeconds(1).toMillis());
    final var markers =
        await("a marker checkpoint is taken on every partition")
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
    return markers.getFirst().getCheckpointTimestamp().toInstant();
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
    cfg.getSystem().setClockControlled(true);
    cfg.getCluster().setPartitionCount(PARTITION_COUNT);
    configureBackupStore(cfg);
    // A marker is taken at startup, as no checkpoint exists yet, and then an interval after the
    // latest checkpoint, which only a test moving the pinned clock by the interval reaches
    cfg.getData().getPrimaryStorage().getBackup().setCheckpointInterval(MARKER_INTERVAL);
    // Smaller log segments for faster checkpoint creation; the max message size must fit in one
    cfg.getData().getPrimaryStorage().getLogStream().setLogSegmentSize(DataSize.ofMegabytes(1));
    cfg.getCluster().getNetwork().setMaxMessageSize(DataSize.ofKilobytes(500));
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
