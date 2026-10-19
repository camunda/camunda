/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.logarchive;

import static io.camunda.zeebe.it.logarchive.LogArchiveBucket.TIMELINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.data.SecondaryStorage.SecondaryStorageType;
import io.camunda.management.backups.BackupInfo;
import io.camunda.management.backups.StateCode;
import io.camunda.zeebe.backup.logarchive.model.CommitEntry;
import io.camunda.zeebe.backup.logarchive.model.HandoverJson;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.LogArchiveIntent;
import io.camunda.zeebe.protocol.record.value.management.LogArchiveRecordValue;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestRestoreApp;
import io.camunda.zeebe.qa.util.cluster.TestSpringApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.restore.logarchive.LogArchiveRestore;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Restores across a real handover from the continuous backups to the log archive (spec sections
 * 18.5, 18.5a and 18.6). A single broker with two partitions takes manual backups with a known
 * amount of data in between, is handed over by the checkpoint scheduler, and is restored with the
 * restore application. The data of an instance phase is told apart by the phase variable of its
 * jobs, so each restore proves it holds exactly the data up to its checkpoint.
 *
 * <p>Every restore works on its own copy of the bucket, so a restored broker archiving or taking
 * backups never changes what the next restore reads.
 */
@ZeebeIntegration
final class LogArchiveHandoverRestoreIT {

  private static final int PARTITIONS = 2;
  private static final int PHASE_SIZE = 6;
  private static final String LATE_BACKUP_SCHEDULE = "PT1H";
  private static @TempDir Path tempDir;

  private final Path testDirectory = tempDir.resolve(UUID.randomUUID().toString());
  private final Path sourceBucket = testDirectory.resolve("source-bucket");
  private final String jobType = "job-" + UUID.randomUUID();
  private final String processId = "process-" + UUID.randomUUID();

  @TestZeebe(partitionCount = PARTITIONS)
  private final TestStandaloneBroker source =
      brokerConfiguredFor(
          new TestStandaloneBroker().withRecordingExporter(true),
          sourceBucket,
          testDirectory.resolve("source-data"),
          "PT5S");

  private LogArchiveBucket bucket;

  @BeforeEach
  void setUp() {
    RecordingExporter.reset();
    bucket = new LogArchiveBucket(sourceBucket);
  }

  @AfterEach
  void tearDown() {
    bucket.close();
  }

  @Test
  void shouldRestoreBeforeAtAndAfterTheHandover() {
    // given -- a completed handover with data created before and after it
    final var scenario = runSourceThroughHandover(true);
    source.stop();

    // when / then -- a backup before H is restored from the old backups
    assertRestore(
        "before",
        app -> app.withBackupId(scenario.old()),
        TIMELINE + ".b" + scenario.old(),
        Map.of("a", PHASE_SIZE));

    // and -- H itself is an old backup too
    assertRestore(
        "at",
        app -> app.withBackupId(scenario.handover()),
        TIMELINE + ".b" + scenario.handover(),
        Map.of("a", PHASE_SIZE, "b", PHASE_SIZE));

    // and -- a backup after H is restored from the archive, without the data created after it
    assertRestore(
        "after",
        app -> app.withBackupId(scenario.archived()),
        TIMELINE + ".r" + scenario.archived(),
        Map.of("a", PHASE_SIZE, "b", PHASE_SIZE, "c", PHASE_SIZE));
  }

  @Test
  void shouldHandOverAgainOnTheBranchOfARestoreToBeforeTheHandover() {
    // given -- a completed handover H1, and a restore to a backup before it
    final var scenario = runSourceThroughHandover(false);
    source.stop();
    final var branchBucket = copyBucket("branch");
    final var branchData = testDirectory.resolve("branch-data");
    restore(branchBucket, branchData, app -> app.withBackupId(scenario.old()));
    final String branchTimeline = TIMELINE + ".b" + scenario.old();

    // when -- the restored cluster takes an old backup, with a schedule that stays idle
    final long branchBackup;
    try (final var branch = startBroker(branchBucket, branchData, LATE_BACKUP_SCHEDULE);
        final var client = branch.newClientBuilder().build()) {
      final long process = deployProcess(client);
      createInstances(client, process, "x", PHASE_SIZE);
      branchBackup = takeBackup(BackupActuator.of(branch));
      assertThat(branchBackup).isGreaterThan(scenario.handover());
    }

    // and -- continues the continuous backup on schedule until it hands over a second time
    RecordingExporter.reset();
    final long secondHandover;
    final long branchArchived;
    try (final var branch = startBroker(branchBucket, branchData, "PT5S");
        final var client = branch.newClientBuilder().build()) {
      PartitionsActuator.of(branch).takeSnapshot();
      secondHandover = awaitHandover(branchBackup);
      assertThat(secondHandover).isGreaterThan(branchBackup);

      final long process = deployProcess(client);
      createInstances(client, process, "y", PHASE_SIZE);
      branchArchived = takeBackup(BackupActuator.of(branch));
      createInstances(client, process, "z", PHASE_SIZE);
    }

    // then -- both handovers are recorded next to the old backups, which resumed on the branch
    try (final var branchArchive = new LogArchiveBucket(branchBucket)) {
      IntStream.rangeClosed(1, PARTITIONS)
          .forEach(
              partition -> {
                assertThat(handoverFile(branchBucket, partition, scenario.handover()))
                    .isRegularFile();
                assertThat(handoverFile(branchBucket, partition, secondHandover)).isRegularFile();
                assertThat(oldBackup(branchBucket, partition, branchBackup)).isDirectory();
                assertThat(oldBackup(branchBucket, partition, secondHandover)).isDirectory();
                assertThat(
                        branchArchive.readAndVerify(branchTimeline, partition).commits().stream()
                            .filter(entry -> entry.type() == CommitEntry.Type.HANDOVER)
                            .map(CommitEntry::handover)
                            .map(HandoverJson::checkpointId))
                    .as(
                        "the second handover is the one of the new timeline of partition %d",
                        partition)
                    .containsExactly(secondHandover);
              });
    }

    // and -- an old backup of the new branch, newer than H1, is restored from the old backups
    assertRestore(
        "branch-old",
        branchBucket,
        app -> app.withBackupId(branchBackup),
        TIMELINE + ".b" + branchBackup,
        Map.of("a", PHASE_SIZE, "x", PHASE_SIZE));

    // and -- a backup after H2 is restored from the archive of the new timeline
    assertRestore(
        "branch-archive",
        branchBucket,
        app -> app.withBackupId(branchArchived),
        branchTimeline + ".r" + branchArchived,
        Map.of("a", PHASE_SIZE, "x", PHASE_SIZE, "y", PHASE_SIZE));
  }

  @Test
  void shouldFindTheArchiveWhenTheOldSideHasNoHandoverFile() throws IOException {
    // given -- a completed handover, whose old-side files are lost
    final var scenario = runSourceThroughHandover(false);
    source.stop();
    final var lostBucket = copyBucket("lost");
    for (int partition = 1; partition <= PARTITIONS; partition++) {
      Files.delete(handoverFile(lostBucket, partition, scenario.handover()));
    }

    // when / then -- a backup after H is restored from the archive of the configured timeline
    assertRestore(
        "lost",
        lostBucket,
        app -> app.withBackupId(scenario.archived()),
        TIMELINE + ".r" + scenario.archived(),
        Map.of("a", PHASE_SIZE, "b", PHASE_SIZE, "c", PHASE_SIZE));
  }

  @Test
  void shouldRestoreAcrossTheHandoverWithTheLogArchiveDisabledInTheRestoreConfiguration() {
    // given -- a completed handover, restored with a configuration from before the upgrade
    final var scenario = runSourceThroughHandover(false);
    source.stop();
    final Consumer<TestRestoreApp> archiveDisabled =
        app -> app.withProperty("zeebe.broker.data.backup.logArchive.enabled", false);

    // when / then -- a backup after H is still found in the archive
    assertRestore(
        "disabled-after",
        app -> archiveDisabled.accept(app.withBackupId(scenario.archived())),
        TIMELINE + ".r" + scenario.archived(),
        Map.of("a", PHASE_SIZE, "b", PHASE_SIZE, "c", PHASE_SIZE));

    // and -- a backup before H takes the old path and still names the new branch
    assertRestore(
        "disabled-before",
        app -> archiveDisabled.accept(app.withBackupId(scenario.old())),
        TIMELINE + ".b" + scenario.old(),
        Map.of("a", PHASE_SIZE));
  }

  @Test
  void shouldReportAManualBackupAfterTheHandoverFromTheArchive() throws IOException {
    // given -- a completed handover
    final var scenario = runSourceThroughHandover(false);
    final var backups = BackupActuator.of(source);

    // when -- a manual backup is requested
    final long backupId = backups.take().getBackupId();

    // then -- it is in progress or completed, and completed once archived
    assertThat(backups.status(backupId).getState())
        .isIn(StateCode.IN_PROGRESS, StateCode.COMPLETED);
    await("the manual backup is completed")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(
            () -> assertThat(backups.status(backupId).getState()).isEqualTo(StateCode.COMPLETED));

    // and -- the backup of the scenario, an older one after H, stays visible once the index chunk
    // holding it is sealed
    final long older = scenario.archived();
    takeBackup(backups);
    await("the index chunk holding the older backup is sealed on every partition")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                IntStream.rangeClosed(1, PARTITIONS)
                    .forEach(
                        partition ->
                            assertThat(bucket.tryView(TIMELINE, partition))
                                .hasValueSatisfying(
                                    view ->
                                        assertThat(view.chunks())
                                            .anyMatch(chunk -> chunk.lastCheckpointId() > older))));
    final var olderStatus = backups.status(older);
    assertThat(olderStatus.getState()).isEqualTo(StateCode.COMPLETED);
    assertThat(olderStatus.getDetails())
        .isNotEmpty()
        .allSatisfy(detail -> assertThat(detail.getCheckpointPosition()).isPositive());

    // and -- the listing holds all of them next to the old backups, newest first like the old
    // listing
    final var listed = backups.list();
    assertThat(listed.stream().map(BackupInfo::getBackupId))
        .contains(scenario.old(), scenario.handover(), older, backupId)
        .isSortedAccordingTo(Comparator.reverseOrder());
    assertThat(listed)
        .filteredOn(info -> info.getBackupId() == older)
        .isNotEmpty()
        .allSatisfy(info -> assertThat(info.getState()).isEqualTo(StateCode.COMPLETED));

    // and -- no old-style backup files were written for it
    source.stop();
    final String id = String.valueOf(backupId);
    for (final var directory : List.of("manifests", "contents")) {
      try (final var files = Files.walk(sourceBucket.resolve(directory))) {
        assertThat(files.map(path -> sourceBucket.resolve(directory).relativize(path)))
            .noneMatch(
                relative ->
                    relative.getNameCount() > 1 && relative.getName(1).toString().equals(id));
      }
    }
  }

  @Test
  void shouldRestoreATimeRangeAcrossTheHandover() {
    // given -- a completed handover, and a time range from before the first old backup on
    final var from = Instant.now();
    final var scenario = runSourceThroughHandover(false);
    source.stop();
    final var to = Instant.now();

    // when / then -- the checkpoint closest to the end of the range is an archive checkpoint
    final var data = testDirectory.resolve("range-data");
    final var rangeBucket = copyBucket("range");
    restore(rangeBucket, data, app -> app.withTimeRange(from, to));
    final var timeline = timelineOf(data, 1);
    assertThat(timeline).startsWith(TIMELINE + ".r");
    assertThat(Long.parseLong(timeline.substring((TIMELINE + ".r").length())))
        .isGreaterThanOrEqualTo(scenario.archived());
    IntStream.rangeClosed(1, PARTITIONS)
        .forEach(partition -> assertThat(timelineOf(data, partition)).isEqualTo(timeline));
    try (final var restored = startBroker(rangeBucket, data, null);
        final var client = restored.newClientBuilder().build()) {
      assertThat(activateAll(client, 3 * PHASE_SIZE))
          .isEqualTo(Map.of("a", PHASE_SIZE, "b", PHASE_SIZE, "c", PHASE_SIZE));
    }
  }

  /**
   * Takes old backups with known data, hands over to the log archive and takes a backup after it.
   *
   * <p>The phases: {@code a}, old backup, {@code b}, a snapshot that lets the scheduler hand over,
   * the handover H, {@code c}, a manual backup that is archived, and, if asked, {@code d}.
   */
  private Scenario runSourceThroughHandover(final boolean dataAfterLastBackup) {
    final var backups = BackupActuator.of(source);
    final long old;
    try (final var client = source.newClientBuilder().build()) {
      final long process = deployProcess(client);
      createInstances(client, process, "a", PHASE_SIZE);
      old = takeBackup(backups);
      createInstances(client, process, "b", PHASE_SIZE);
    }
    // the broker takes no snapshot by itself within a minute, so the handover cannot happen before
    PartitionsActuator.of(source).takeSnapshot();

    final long handover = awaitHandover(old);
    await("the old side holds the handover file of every partition")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                IntStream.rangeClosed(1, PARTITIONS)
                    .forEach(
                        partition ->
                            assertThat(handoverFile(sourceBucket, partition, handover))
                                .isRegularFile()));

    final long archived;
    try (final var client = source.newClientBuilder().build()) {
      final long process = deployProcess(client);
      createInstances(client, process, "c", PHASE_SIZE);
      archived = takeBackup(backups);
      if (dataAfterLastBackup) {
        createInstances(client, process, "d", PHASE_SIZE);
      }
    }
    return new Scenario(old, handover, archived);
  }

  /** Waits until every partition completed one handover after the given checkpoint. */
  private static long awaitHandover(final long after) {
    final var handovers =
        await("the handover is completed on every partition")
            .atMost(Duration.ofMinutes(2))
            .until(() -> handoversCompleted(after), completed -> completed.size() == PARTITIONS);
    assertThat(handovers.values().stream().distinct())
        .as("one handover checkpoint for all partitions")
        .hasSize(1);
    return handovers.values().iterator().next();
  }

  /** Per partition, the checkpoint id of its HANDOVER_COMPLETED event newer than the given id. */
  private static Map<Integer, Long> handoversCompleted(final long after) {
    return RecordingExporter.getRecords().stream()
        .filter(r -> r.getValueType() == ValueType.LOG_ARCHIVE)
        .filter(r -> r.getIntent() == LogArchiveIntent.HANDOVER_COMPLETED)
        .filter(r -> ((LogArchiveRecordValue) r.getValue()).getHandoverCheckpointId() > after)
        .collect(
            Collectors.toMap(
                Record::getPartitionId,
                r -> ((LogArchiveRecordValue) r.getValue()).getHandoverCheckpointId(),
                (a, b) -> a));
  }

  private void assertRestore(
      final String name,
      final Consumer<TestRestoreApp> selection,
      final String expectedTimeline,
      final Map<String, Integer> expectedPhases) {
    assertRestore(name, sourceBucket, selection, expectedTimeline, expectedPhases);
  }

  /**
   * Restores a copy of the bucket, checks which path took each partition by the timeline it starts
   * (an old restore starts {@code <tl>.b<id>}, an archive restore {@code <tl>.r<id>}), and checks
   * that the restored broker holds exactly the expected jobs.
   */
  private void assertRestore(
      final String name,
      final Path from,
      final Consumer<TestRestoreApp> selection,
      final String expectedTimeline,
      final Map<String, Integer> expectedPhases) {
    final var restoredBucket = copyBucket(name + "-restore", from);
    final var data = testDirectory.resolve(name + "-restored-data");
    restore(restoredBucket, data, selection);
    IntStream.rangeClosed(1, PARTITIONS)
        .forEach(
            partition ->
                assertThat(timelineOf(data, partition))
                    .as("timeline of partition %d restored for %s", partition, name)
                    .isEqualTo(expectedTimeline));

    RecordingExporter.reset();
    try (final var restored = startBroker(restoredBucket, data, null);
        final var client = restored.newClientBuilder().build()) {
      assertThat(activateAll(client, expectedPhases.values().stream().mapToInt(i -> i).sum()))
          .as("jobs of the cluster restored for %s", name)
          .isEqualTo(expectedPhases);
    }
  }

  private void restore(
      final Path restoredBucket, final Path data, final Consumer<TestRestoreApp> selection) {
    final var app =
        brokerConfiguredFor(new TestRestoreApp(), restoredBucket, data, null)
            .withUnifiedConfig(
                cfg -> cfg.getData().getSecondaryStorage().setType(SecondaryStorageType.none));
    selection.accept(app);
    try {
      app.start();
    } finally {
      app.close();
    }
  }

  private TestStandaloneBroker startBroker(
      final Path bucketPath, final Path data, final String backupSchedule) {
    final var broker =
        brokerConfiguredFor(
                new TestStandaloneBroker().withRecordingExporter(true),
                bucketPath,
                data,
                backupSchedule)
            .withUnifiedConfig(cfg -> cfg.getCluster().setPartitionCount(PARTITIONS));
    broker.start().awaitCompleteTopology(1, PARTITIONS, 1, Duration.ofSeconds(30));
    return broker;
  }

  private static <T extends TestSpringApplication<T>> T brokerConfiguredFor(
      final T application, final Path bucketPath, final Path data, final String backupSchedule) {
    return LogArchiveBucket.enableLogArchive(application)
        .withUnifiedConfig(
            cfg -> {
              cfg.getCluster().setPartitionCount(PARTITIONS);
              LogArchiveBucket.configureStore(cfg, bucketPath);
              configureBackup(cfg, data, backupSchedule);
            });
  }

  private static void configureBackup(
      final Camunda cfg, final Path data, final String backupSchedule) {
    final var primaryStorage = cfg.getData().getPrimaryStorage();
    primaryStorage.setDirectory(data.toAbsolutePath().toString());
    if (backupSchedule != null) {
      primaryStorage.getBackup().setContinuous(true);
      primaryStorage.getBackup().setSchedule(backupSchedule);
    }
  }

  private long takeBackup(final BackupActuator backups) {
    final long backupId = backups.take().getBackupId();
    await("backup " + backupId + " is completed")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(
            () -> assertThat(backups.status(backupId).getState()).isEqualTo(StateCode.COMPLETED));
    return backupId;
  }

  /** Activates the jobs until the expected number is found and counts them by phase. */
  private Map<String, Integer> activateAll(final CamundaClient client, final int expected) {
    final var activated = new ArrayList<ActivatedJob>();
    await("the expected jobs are activated")
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(500))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              activated.addAll(activateJobs(client));
              assertThat(activated).hasSize(expected);
            });
    assertThat(activateJobs(client)).as("no further job").isEmpty();
    final var phases = new TreeMap<String, Integer>();
    activated.forEach(
        job -> phases.merge((String) job.getVariablesAsMap().get("phase"), 1, Integer::sum));
    return phases;
  }

  private List<ActivatedJob> activateJobs(final CamundaClient client) {
    return client
        .newActivateJobsCommand()
        .jobType(jobType)
        .maxJobsToActivate(100)
        .fetchVariables("phase")
        .requestTimeout(Duration.ofSeconds(2))
        .send()
        .join()
        .getJobs();
  }

  private long deployProcess(final CamundaClient client) {
    return client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .endEvent()
                .done(),
            "process.bpmn")
        .send()
        .join()
        .getProcesses()
        .getFirst()
        .getProcessDefinitionKey();
  }

  private static void createInstances(
      final CamundaClient client, final long process, final String phase, final int count) {
    for (int i = 0; i < count; i++) {
      client
          .newCreateInstanceCommand()
          .processDefinitionKey(process)
          .variables(Map.of("phase", phase))
          .send()
          .join();
    }
  }

  private static String timelineOf(final Path data, final int partition) {
    try {
      return Files.readString(
          data.resolve("default/partitions/" + partition)
              .resolve(LogArchiveRestore.TIMELINE_FILE_NAME));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Path handoverFile(final Path bucketPath, final int partition, final long id) {
    return bucketPath.resolve("metadata/%d/handover/%d.json".formatted(partition, id));
  }

  private static Path oldBackup(final Path bucketPath, final int partition, final long id) {
    return bucketPath.resolve("manifests/%d/%d".formatted(partition, id));
  }

  private Path copyBucket(final String name) {
    return copyBucket(name, sourceBucket);
  }

  private Path copyBucket(final String name, final Path from) {
    final var copy = testDirectory.resolve(name + "-bucket");
    try (final Stream<Path> files = Files.walk(from)) {
      for (final var path : files.toList()) {
        final var target = copy.resolve(from.relativize(path).toString());
        if (Files.isDirectory(path)) {
          Files.createDirectories(target);
        } else {
          Files.copy(path, target);
        }
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return copy;
  }

  /**
   * @param old an old backup before the handover, holding phase a
   * @param handover the handover checkpoint H, holding phases a and b
   * @param archived a backup after the handover, holding phases a to c
   */
  private record Scenario(long old, long handover, long archived) {}
}
