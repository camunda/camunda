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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.data.SecondaryStorage.SecondaryStorageType;
import io.camunda.management.backups.StateCode;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import io.camunda.zeebe.protocol.record.intent.management.LogArchiveIntent;
import io.camunda.zeebe.protocol.record.value.management.CheckpointRecordValue;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Restores a time range across a real handover with an RDBMS as secondary storage (spec section
 * 18.5a). The restore resolves the exported position E of the RDBMS per partition, so it must pick
 * a checkpoint at or after E and a journal that holds the log from E + 1, which lets the RDBMS
 * exporter resume without a gap.
 *
 * <p>The scenario freezes E by disabling the RDBMS exporter while more data is created, so that the
 * data after E is only in the log. The restored broker has to export exactly that data.
 */
@ZeebeIntegration
final class LogArchiveHandoverRdbmsRestoreIT {

  private static final int PARTITIONS = 2;
  private static final int PHASE_SIZE = 6;
  private static @TempDir Path tempDir;

  private final Path testDirectory = tempDir.resolve(UUID.randomUUID().toString());
  private final Path sourceBucket = testDirectory.resolve("source-bucket");
  private final String jobType = "job-" + UUID.randomUUID();
  private final String processId = "process-" + UUID.randomUUID();
  // DB_CLOSE_DELAY keeps the in-memory database alive while the brokers stop and start
  private final String h2Url =
      "jdbc:h2:mem:handover-rdbms-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL";

  @TestZeebe(partitionCount = PARTITIONS)
  private final TestStandaloneBroker source =
      configured(
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
  void tearDown() throws SQLException {
    bucket.close();
    try (final var connection = connect();
        final var statement = connection.createStatement()) {
      statement.execute("DROP ALL OBJECTS");
    }
  }

  @Test
  void shouldRestoreFromTheArchiveAndResumeTheRdbmsExporterAfterTheExportedPosition()
      throws Exception {
    // given -- a completed handover, an RDBMS that exported phases a to c, and data after the
    // exported position E (phase d) that only the log holds; the range starts inside the old
    // backups' range, before H, as continuous backup requires a backup at or before `from`
    final var client = source.newClientBuilder().build();
    final long process = deployProcess(client);
    takeBackup(BackupActuator.of(source));
    createInstances(client, process, "a", PHASE_SIZE);
    final var from = Instant.now();
    final long old = takeBackup(BackupActuator.of(source));
    createInstances(client, process, "b", PHASE_SIZE);
    PartitionsActuator.of(source).takeSnapshot();
    final long handover = awaitHandover(old);
    createInstances(client, process, "c", PHASE_SIZE);
    await("the RDBMS exported the instances of phases a to c")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(() -> assertThat(exportedInstances()).isEqualTo(3 * PHASE_SIZE));
    disableRdbmsExporter();
    final var exported = exportedPositions();
    createInstances(client, process, "d", PHASE_SIZE);
    client.close();
    // a snapshot newer than E + 1, so that the restore must not start its journal at the snapshot
    PartitionsActuator.of(source).takeSnapshot();
    final long archived = takeBackup(BackupActuator.of(source));
    assertThat(archived).isGreaterThan(handover);
    assertThat(checkpointPositions(archived))
        .allSatisfy(
            (partition, position) -> assertThat(position).isGreaterThan(exported.get(partition)));
    assertThat(exportedInstances()).as("nothing exported after E").isEqualTo(3 * PHASE_SIZE);
    source.stop();
    final var to = Instant.now().plusSeconds(60);

    // when -- restoring a range from inside the old backups, before the handover, to after the
    // archive checkpoint
    final var restoredBucket = copyBucket("restored-bucket");
    final var data = testDirectory.resolve("restored-data");
    restore(restoredBucket, data, app -> app.withTimeRange(from, to));

    // then -- every partition took the archive path to the same checkpoint, which is no older than
    // the one taken after E (scheduled backups kept running while the source was up)
    final var timeline = timelineOf(data, 1);
    assertThat(timeline).startsWith(TIMELINE + ".r");
    assertThat(Long.parseLong(timeline.substring((TIMELINE + ".r").length())))
        .isGreaterThanOrEqualTo(archived);
    IntStream.rangeClosed(1, PARTITIONS)
        .forEach(partition -> assertThat(timelineOf(data, partition)).isEqualTo(timeline));

    // and -- the restored broker exports the data after E, so the journal holds the log from E + 1
    RecordingExporter.reset();
    try (final var restored = startBroker(restoredBucket, data);
        final var restoredClient = restored.newClientBuilder().build()) {
      await("the RDBMS exporter resumed after E and exported phase d")
          .atMost(Duration.ofSeconds(60))
          .untilAsserted(
              () -> {
                assertThat(exportedInstances()).isEqualTo(4 * PHASE_SIZE);
                exportedPositions()
                    .forEach(
                        (partition, position) ->
                            assertThat(position).isGreaterThan(exported.get(partition)));
              });

      // and -- the restored data matches the target checkpoint
      assertThat(activateAll(restoredClient, 4 * PHASE_SIZE))
          .isEqualTo(Map.of("a", PHASE_SIZE, "b", PHASE_SIZE, "c", PHASE_SIZE, "d", PHASE_SIZE));
    }

    // and -- a range that starts before every backup is rejected as with continuous backup
    final var beforeEveryBackup = from.minus(Duration.ofDays(1));
    final var earlyBucket = copyBucket("early-bucket");
    assertThatThrownBy(
            () ->
                restore(
                    earlyBucket,
                    testDirectory.resolve("early-data"),
                    app -> app.withTimeRange(beforeEveryBackup, to)))
        .hasStackTraceContaining(
            "No usable range found for partition 1 with from=" + beforeEveryBackup);

    // and -- an RDBMS ahead of every checkpoint cannot be restored to, with the same message as
    // with continuous backup
    setExportedPositions(Long.MAX_VALUE / 2);
    final var aheadBucket = copyBucket("ahead-bucket");
    assertThatThrownBy(
            () ->
                restore(
                    aheadBucket,
                    testDirectory.resolve("ahead-data"),
                    app -> app.withTimeRange(from, to)))
        .hasStackTraceContaining("No usable range found for partition 1")
        .hasStackTraceContaining("exportedPosition=" + Long.MAX_VALUE / 2);
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

  private Map<Integer, Long> exportedPositions() throws SQLException {
    final var positions = new TreeMap<Integer, Long>();
    try (final var connection = connect();
        final var statement = connection.createStatement();
        final var result =
            statement.executeQuery(
                "SELECT PARTITION_ID, LAST_EXPORTED_POSITION FROM EXPORTER_POSITION")) {
      while (result.next()) {
        positions.put(result.getInt(1), result.getLong(2));
      }
    }
    return positions;
  }

  /** Connects to the database directly, as it has to be read while no broker is running. */
  private Connection connect() throws SQLException {
    return DriverManager.getConnection(h2Url, "sa", "");
  }

  private void setExportedPositions(final long position) throws SQLException {
    try (final var connection = connect();
        final var statement = connection.createStatement()) {
      statement.executeUpdate("UPDATE EXPORTER_POSITION SET LAST_EXPORTED_POSITION = " + position);
    }
  }

  private int exportedInstances() throws SQLException {
    try (final var connection = connect();
        final var statement = connection.createStatement();
        final var result = statement.executeQuery("SELECT COUNT(*) FROM PROCESS_INSTANCE")) {
      result.next();
      return result.getInt(1);
    }
  }

  /** Per partition, the position of the checkpoint of the given id. */
  @SuppressWarnings("unchecked")
  private static Map<Integer, Long> checkpointPositions(final long checkpointId) {
    return RecordingExporter.getRecords().stream()
        .filter(r -> r.getValueType() == ValueType.CHECKPOINT)
        .filter(r -> r.getIntent() == CheckpointIntent.CREATED)
        .map(r -> (Record<CheckpointRecordValue>) r)
        .filter(r -> r.getValue().getCheckpointId() == checkpointId)
        .collect(
            Collectors.toMap(
                Record::getPartitionId, r -> r.getValue().getCheckpointPosition(), (a, b) -> a));
  }

  private static long awaitHandover(final long after) {
    final var handovers =
        await("the handover is completed on every partition")
            .atMost(Duration.ofMinutes(2))
            .until(
                () ->
                    RecordingExporter.getRecords().stream()
                        .filter(r -> r.getValueType() == ValueType.LOG_ARCHIVE)
                        .filter(r -> r.getIntent() == LogArchiveIntent.HANDOVER_COMPLETED)
                        .collect(
                            Collectors.toMap(
                                Record::getPartitionId,
                                r ->
                                    ((LogArchiveRecordValue) r.getValue())
                                        .getHandoverCheckpointId(),
                                (a, b) -> a)),
                completed -> completed.size() == PARTITIONS);
    assertThat(handovers.values().stream().distinct()).hasSize(1);
    final long handover = handovers.values().iterator().next();
    assertThat(handover).isGreaterThan(after);
    return handover;
  }

  private void restore(
      final Path restoredBucket, final Path data, final Consumer<TestRestoreApp> selection) {
    final var app = configured(new TestRestoreApp(), restoredBucket, data, null);
    selection.accept(app);
    try {
      app.start();
    } finally {
      app.close();
    }
  }

  private TestStandaloneBroker startBroker(final Path bucketPath, final Path data) {
    final var broker =
        configured(new TestStandaloneBroker().withRecordingExporter(true), bucketPath, data, null);
    broker.start().awaitCompleteTopology(1, PARTITIONS, 1, Duration.ofSeconds(30));
    return broker;
  }

  private <T extends TestSpringApplication<T>> T configured(
      final T application, final Path bucketPath, final Path data, final String backupSchedule) {
    return LogArchiveBucket.enableLogArchive(application)
        .withUnifiedConfig(
            cfg -> {
              cfg.getCluster().setPartitionCount(PARTITIONS);
              LogArchiveBucket.configureStore(cfg, bucketPath);
              configureRdbms(cfg);
              final var primaryStorage = cfg.getData().getPrimaryStorage();
              primaryStorage.setDirectory(data.toAbsolutePath().toString());
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
