/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.logarchive;

import static io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride.RDBMS_EXPORTER_NAME;
import static io.camunda.zeebe.it.logarchive.LogArchiveBucket.TIMELINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.data.SecondaryStorage.SecondaryStorageType;
import io.camunda.management.backups.StateCode;
import io.camunda.zeebe.journal.JournalMetaStore.InMemory;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.LogArchiveIntent;
import io.camunda.zeebe.protocol.record.value.management.LogArchiveRecordValue;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.actuator.ExportersActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestRestoreApp;
import io.camunda.zeebe.qa.util.cluster.TestSpringApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import io.camunda.zeebe.restore.logarchive.LogArchiveRestore;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

/**
 * The bridged restore with an RDBMS that is behind the start of the log archive (spec section
 * 18.5a), as in the manual test H3: the RDBMS exporter is disabled early, so its exported position
 * E freezes and no longer holds compaction; the log is compacted past E + 1 before the log archive
 * is enabled, so the archive starts after E + 1. A restore to a checkpoint after the handover must
 * then take the log from E + 1 up to the start of the archive from the old continuous backups and
 * the rest from the archive, so the restored RDBMS exporter fills the database without a gap.
 */
@ZeebeIntegration
final class LogArchiveBridgedRdbmsRestoreIT {

  private static final int PARTITION = 1;
  private static final int SMALL_PHASE = 5;
  private static final int LARGE_PHASE = 300;
  private static final String PAYLOAD = "x".repeat(10 * 1024);
  private static @TempDir Path tempDir;

  private final Path testDirectory = tempDir.resolve(UUID.randomUUID().toString());
  private final Path sourceBucket = testDirectory.resolve("source-bucket");
  private final Path sourceData = testDirectory.resolve("source-data");
  private final String jobType = "job-" + UUID.randomUUID();
  private final String processId = "process-" + UUID.randomUUID();
  // DB_CLOSE_DELAY keeps the in-memory database alive while the brokers stop and start
  private final String h2Url =
      "jdbc:h2:mem:bridged-rdbms-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL";

  // continuous backup only; the log archive is enabled later, like an upgrade
  @TestZeebe(partitionCount = 1)
  private final TestStandaloneBroker source =
      configured(
          new TestStandaloneBroker().withRecordingExporter(true), sourceBucket, sourceData, "PT5S");

  private LogArchiveBucket bucket;

  @BeforeEach
  void setUp() {
    RecordingExporter.reset();
    bucket = new LogArchiveBucket(sourceBucket);
  }

  @AfterEach
  void tearDown() throws SQLException {
    bucket.close();
    try (final var connection = connect();
        final var statement = connection.createStatement()) {
      statement.execute("DROP ALL OBJECTS");
    }
  }

  @Test
  void shouldRestoreAfterTheHandoverWithOldBackupsBridgingToTheArchive() throws Exception {
    // given -- the RDBMS exported phase a, then its exporter is disabled, freezing E
    try (final var client = source.newClientBuilder().build()) {
      final long process = deployProcess(client);
      createInstances(client, process, "a", SMALL_PHASE, "");
      await("the RDBMS exported the instances of phase a")
          .atMost(Duration.ofSeconds(60))
          .untilAsserted(() -> assertThat(exportedInstances()).isEqualTo(SMALL_PHASE));
      disableRdbmsExporter();
      final long exported = exportedPosition();

      // and -- about 3 MB of log after E, compacted on disk once old backups cover it
      createInstances(client, process, "b", LARGE_PHASE, PAYLOAD);
      awaitCompactionPastTheFirstSegment();

      // and -- the upgrade: the log archive starts at the first index on disk, after E + 1
      source.stop();
      LogArchiveBucket.enableLogArchive(source);
      source.start().awaitCompleteTopology(1, 1, 1, Duration.ofSeconds(30));
      final long handover = awaitHandover();
      final var view = bucket.tryView(TIMELINE, PARTITION).orElseThrow();
      assertThat(view.startIndex()).isGreaterThan(1);
      assertThat(view.firstArchivedPosition())
          .as("the archive starts after E + 1")
          .isGreaterThan(exported + 1);

      // and -- data after the handover and a backup C that the archive covers
      try (final var upgradedClient = source.newClientBuilder().build()) {
        createInstances(upgradedClient, process, "c", SMALL_PHASE, "");
      }
      final long checkpoint = takeBackup(BackupActuator.of(source));
      assertThat(checkpoint).isGreaterThan(handover);
      assertThat(exportedInstances()).as("nothing exported after E").isEqualTo(SMALL_PHASE);
      source.stop();
      final var to = Instant.now();

      // when -- restoring the time range up to C with the RDBMS at E; without `from`, like the
      // manual test, as the old restore rejects a `from` after which the first backup lies past E
      final var restoredBucket = copyBucket("restored-bucket");
      final var data = testDirectory.resolve("restored-data");
      restore(restoredBucket, data, to);

      // then -- the archive path to C or later, not the fallback to the old backups up to H
      final var timeline = timelineOf(data);
      assertThat(timeline).startsWith(TIMELINE + ".r");
      assertThat(Long.parseLong(timeline.substring((TIMELINE + ".r").length())))
          .isGreaterThanOrEqualTo(checkpoint);

      // and -- the restored journal reaches back to E + 1, before the start of the archive
      assertThat(firstPositionOfJournal(data)).isLessThanOrEqualTo(exported + 1);

      // and -- the restored RDBMS exporter fills the database from E + 1 without a gap
      RecordingExporter.reset();
      try (final var restored = startBroker(restoredBucket, data);
          final var restoredClient = restored.newClientBuilder().build()) {
        final int total = 2 * SMALL_PHASE + LARGE_PHASE;
        await("the RDBMS exporter resumed after E and exported phases b and c")
            .atMost(Duration.ofMinutes(2))
            .untilAsserted(() -> assertThat(exportedInstances()).isEqualTo(total));

        // and -- the restored data holds everything up to C
        assertThat(activateAll(restoredClient, total))
            .isEqualTo(Map.of("a", SMALL_PHASE, "b", LARGE_PHASE, "c", SMALL_PHASE));
      }
    }
  }

  /**
   * Takes snapshots until the journal's first segment is deleted: continuous backups pin the log
   * until they cover it, the disabled RDBMS exporter does not.
   */
  private void awaitCompactionPastTheFirstSegment() {
    final var partitions = PartitionsActuator.of(source);
    await("the first segment of the journal is compacted")
        .atMost(Duration.ofMinutes(2))
        .pollInterval(Duration.ofSeconds(3))
        .ignoreExceptions()
        .until(
            () -> {
              partitions.takeSnapshot();
              return segments(partitionDirectory(sourceData)).stream()
                  .noneMatch(file -> file.getFileName().toString().endsWith("-1.log"));
            });
  }

  private void disableRdbmsExporter() {
    final var change = ExportersActuator.of(source).disableExporter(RDBMS_EXPORTER_NAME);
    final var cluster = ClusterActuator.of(source);
    await("the RDBMS exporter is disabled")
        .atMost(Duration.ofMinutes(1))
        .untilAsserted(
            () ->
                ClusterActuatorAssert.assertThat(cluster)
                    .hasCompletedChanges(change.getChangeId())
                    .doesNotHavePendingChanges());
  }

  private long awaitHandover() {
    final var partitions = PartitionsActuator.of(source);
    return await("the handover is completed")
        .atMost(Duration.ofMinutes(2))
        .pollInterval(Duration.ofSeconds(2))
        .until(
            () -> {
              // the scheduler hands over once the archive holds a snapshot
              try {
                partitions.takeSnapshot();
              } catch (final RuntimeException ignored) {
                // nothing new to snapshot
              }
              return RecordingExporter.getRecords().stream()
                  .filter(r -> r.getValueType() == ValueType.LOG_ARCHIVE)
                  .filter(r -> r.getIntent() == LogArchiveIntent.HANDOVER_COMPLETED)
                  .map(r -> ((LogArchiveRecordValue) r.getValue()).getHandoverCheckpointId())
                  .findFirst()
                  .orElse(-1L);
            },
            handover -> handover > 0);
  }

  /** The asqn of the first application entry of the restored partition's journal. */
  private static long firstPositionOfJournal(final Path data) throws IOException {
    final var directory = partitionDirectory(data);
    final var fileName =
        segments(directory).stream()
            .map(path -> path.getFileName().toString())
            .sorted()
            .findFirst()
            .orElseThrow();
    final var journalName = fileName.substring(0, fileName.lastIndexOf('-'));
    final var meterRegistry = new SimpleMeterRegistry();
    try (final var journal =
            SegmentedJournal.builder(meterRegistry)
                .withDirectory(directory.toFile())
                .withName(journalName)
                .withMetaStore(new InMemory())
                .build();
        final var reader = journal.openReader()) {
      while (reader.hasNext()) {
        final var record = reader.next();
        if (record.asqn() >= 0) {
          return record.asqn();
        }
      }
    } finally {
      meterRegistry.close();
    }
    throw new IllegalStateException("No application entry in the journal of " + directory);
  }

  private static List<Path> segments(final Path partitionDirectory) throws IOException {
    try (final Stream<Path> files = Files.list(partitionDirectory)) {
      return files.filter(file -> file.getFileName().toString().endsWith(".log")).toList();
    }
  }

  private static Path partitionDirectory(final Path data) {
    return data.resolve("default/partitions/" + PARTITION);
  }

  private long exportedPosition() throws SQLException {
    try (final var connection = connect();
        final var statement = connection.createStatement();
        final var result =
            statement.executeQuery(
                "SELECT LAST_EXPORTED_POSITION FROM EXPORTER_POSITION WHERE PARTITION_ID = "
                    + PARTITION)) {
      assertThat(result.next()).as("the RDBMS exporter wrote its position").isTrue();
      return result.getLong(1);
    }
  }

  /** Connects to the database directly, as it has to be read while no broker is running. */
  private Connection connect() throws SQLException {
    return DriverManager.getConnection(h2Url, "sa", "");
  }

  private int exportedInstances() throws SQLException {
    try (final var connection = connect();
        final var statement = connection.createStatement();
        final var result = statement.executeQuery("SELECT COUNT(*) FROM PROCESS_INSTANCE")) {
      result.next();
      return result.getInt(1);
    }
  }

  private void restore(final Path restoredBucket, final Path data, final Instant to) {
    final var app =
        LogArchiveBucket.enableLogArchive(
                configured(new TestRestoreApp(), restoredBucket, data, null))
            .withTimeRange(null, to);
    try {
      app.start();
    } finally {
      app.close();
    }
  }

  private TestStandaloneBroker startBroker(final Path bucketPath, final Path data) {
    final var broker =
        LogArchiveBucket.enableLogArchive(
            configured(
                new TestStandaloneBroker().withRecordingExporter(true), bucketPath, data, null));
    broker.start().awaitCompleteTopology(1, 1, 1, Duration.ofSeconds(30));
    return broker;
  }

  private <T extends TestSpringApplication<T>> T configured(
      final T application, final Path bucketPath, final Path data, final String backupSchedule) {
    return application.withUnifiedConfig(
        cfg -> {
          cfg.getCluster().setPartitionCount(1);
          // the segment size must exceed the max message size
          cfg.getCluster().getNetwork().setMaxMessageSize(DataSize.ofKilobytes(512));
          LogArchiveBucket.configureStore(cfg, bucketPath);
          configureRdbms(cfg);
          final var primaryStorage = cfg.getData().getPrimaryStorage();
          primaryStorage.setDirectory(data.toAbsolutePath().toString());
          primaryStorage.getLogStream().setLogSegmentSize(DataSize.ofMegabytes(1));
          if (backupSchedule != null) {
            primaryStorage.getBackup().setContinuous(true);
            primaryStorage.getBackup().setSchedule(backupSchedule);
          }
        });
  }

  private void configureRdbms(final Camunda cfg) {
    final var secondaryStorage = cfg.getData().getSecondaryStorage();
    secondaryStorage.setType(SecondaryStorageType.rdbms);
    secondaryStorage.setAutoconfigureCamundaExporter(false);
    secondaryStorage.getRdbms().setUrl(h2Url);
    secondaryStorage.getRdbms().setUsername("sa");
    secondaryStorage.getRdbms().setPassword("");
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

  private Map<String, Integer> activateAll(final CamundaClient client, final int expected) {
    final var activated = new ArrayList<ActivatedJob>();
    await("the expected jobs are activated")
        .atMost(Duration.ofSeconds(90))
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
      final CamundaClient client,
      final long process,
      final String phase,
      final int count,
      final String payload) {
    for (int i = 0; i < count; i++) {
      client
          .newCreateInstanceCommand()
          .processDefinitionKey(process)
          .variables(Map.of("phase", phase, "payload", payload))
          .send()
          .join();
    }
  }

  private static String timelineOf(final Path data) {
    try {
      return Files.readString(
          partitionDirectory(data).resolve(LogArchiveRestore.TIMELINE_FILE_NAME));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private Path copyBucket(final String name) {
    final var copy = testDirectory.resolve(name);
    try (final Stream<Path> files = Files.walk(sourceBucket)) {
      for (final var path : files.toList()) {
        final var target = copy.resolve(sourceBucket.relativize(path).toString());
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
}
