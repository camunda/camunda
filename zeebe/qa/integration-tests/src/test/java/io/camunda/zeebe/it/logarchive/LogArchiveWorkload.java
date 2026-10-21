/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.logarchive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.management.backups.StateCode;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.model.LogObjectJson;
import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import io.camunda.zeebe.db.impl.rocksdb.RocksDBSnapshotFileInfoProvider;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.cluster.TestSpringApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.restore.logarchive.LogArchiveRestore;
import io.camunda.zeebe.restore.logarchive.LogArchiveRestoreResult;
import io.camunda.zeebe.restore.logarchive.LogArchiveRestoreTarget;
import io.camunda.zeebe.restore.logarchive.LogArchiveView;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.util.unit.DataSize;

/** The process, instances, jobs and backups the merge and release tests share. */
final class LogArchiveWorkload {

  private static final Duration TIMEOUT = Duration.ofSeconds(60);

  private static final Pattern DATA_KEY = Pattern.compile("data/(\\d+)-(\\d+)-t(\\d+)\\.seg$");

  final String jobType = "job-" + UUID.randomUUID();

  /**
   * Makes the log archive merge and clean up within seconds: small journal segments (the segment
   * size must exceed the max message size), a few objects per merge, a short cleanup interval and
   * grace period. Call it on a broker that {@link LogArchiveBucket#enableLogArchive} configured.
   */
  static <T extends TestSpringApplication<T>> T enableMerging(
      final T broker, final int mergeMaxObjects) {
    final var prefix = "zeebe.broker.data.backup.logArchive.";
    return broker
        .withUnifiedConfig(
            cfg -> {
              cfg.getData()
                  .getPrimaryStorage()
                  .getLogStream()
                  .setLogSegmentSize(DataSize.ofMegabytes(1));
              cfg.getCluster().getNetwork().setMaxMessageSize(DataSize.ofKilobytes(512));
            })
        .withProperty(prefix + "mergeMaxObjects", mergeMaxObjects)
        .withProperty(prefix + "mergeFrameBytes", "16KB")
        .withProperty(prefix + "cleanupInterval", "PT1S")
        .withProperty(prefix + "cleanupGracePeriod", "PT2S");
  }

  /** The key of a log object in the store, as the objects of the view name it relative to it. */
  static String dataKeyPrefix(final LogArchiveView view) {
    return LogArchiveKeys.partitionPrefix(view.timelineId(), view.partitionId());
  }

  /**
   * The archived (small) objects a merge replaced: archived objects up to M whose key is not the
   * key of a merged object. A run of one object is merged under its own key, so it is not replaced.
   */
  static List<LogObjectJson> replacedObjects(final LogArchiveView view) {
    final var mergedKeys = new HashSet<String>();
    view.mergedObjects().forEach(o -> mergedKeys.add(o.key()));
    return LogArchiveBucket.objects(view).stream()
        .filter(o -> o.lastIndex() <= view.mergedIndex() && !mergedKeys.contains(o.key()))
        .toList();
  }

  static boolean exists(final LogArchiveStore store, final LogArchiveView view, final String key) {
    return store.getIfExists(dataKeyPrefix(view) + key).join().isPresent();
  }

  /**
   * Keys in {@code data/} that no archived or merged object of the view names and whose last index
   * is at most {@code upToIndex}: leftovers of merges that lost or of leaders that were replaced.
   * Lists before reading the view, so that an object committed meanwhile is not mistaken for one.
   */
  static List<String> orphans(
      final LogArchiveStore store,
      final String timelineId,
      final int partitionId,
      final long upToIndex) {
    final var prefix = LogArchiveKeys.partitionPrefix(timelineId, partitionId);
    final var keys = store.list(prefix + "data/").join();
    final var view = LogArchiveView.read(store, timelineId, partitionId);
    final var referenced = new HashSet<String>();
    LogArchiveBucket.objects(view).forEach(o -> referenced.add(prefix + o.key()));
    view.mergedObjects().forEach(o -> referenced.add(prefix + o.key()));
    return keys.stream()
        .filter(k -> !referenced.contains(k))
        .filter(k -> lastIndexOf(k) <= upToIndex)
        .toList();
  }

  static long lastIndexOf(final String dataKey) {
    final var matcher = DATA_KEY.matcher(dataKey);
    if (!matcher.find()) {
      throw new IllegalArgumentException("not a data key: " + dataKey);
    }
    return Long.parseLong(matcher.group(2));
  }

  /** Restores partition 1 of the given timeline into an empty data directory. */
  static LogArchiveRestoreResult restore(
      final LogArchiveStore store,
      final String timelineId,
      final long checkpointId,
      final Path restoredData) {
    return new LogArchiveRestore(
            store, new RocksDBSnapshotFileInfoProvider(), new SimpleMeterRegistry())
        .restorePartitions(
            timelineId,
            1,
            Optional.of(checkpointId),
            List.of(
                new LogArchiveRestoreTarget(
                    1, restoredData.resolve("default/partitions/1"), "default-partition-1", 0)));
  }

  long deployProcess(final CamundaClient client) {
    return client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("process")
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

  /** Creates instances whose job carries the given phase, so that a restore can tell them apart. */
  static void createInstances(
      final CamundaClient client, final long process, final String phase, final int count) {
    for (int i = 0; i < count; i++) {
      client
          .newCreateInstanceCommand()
          .processDefinitionKey(process)
          .variables(Map.of("phase", phase, "payload", "x".repeat(512)))
          .send()
          .join();
    }
  }

  List<ActivatedJob> activateJobs(final CamundaClient client) {
    return client
        .newActivateJobsCommand()
        .jobType(jobType)
        .maxJobsToActivate(500)
        .fetchVariables("phase")
        .requestTimeout(Duration.ofSeconds(2))
        .send()
        .join()
        .getJobs();
  }

  static long takeBackup(final TestStandaloneBroker broker) {
    final var backupActuator = BackupActuator.of(broker);
    final long backupId = System.currentTimeMillis();
    backupActuator.take(backupId);
    await("backup " + backupId + " is completed")
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(backupActuator.status(backupId).getState())
                    .isEqualTo(StateCode.COMPLETED));
    return backupId;
  }

  /** Creates process instances in the background until closed; failures are ignored. */
  static final class Load implements AutoCloseable {
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread thread;

    Load(final Supplier<CamundaClient> clients, final long process, final String phase) {
      thread =
          new Thread(
              () -> {
                while (running.get()) {
                  try (final var client = clients.get()) {
                    for (int i = 0; i < 10 && running.get(); i++) {
                      createInstances(client, process, phase, 1);
                    }
                  } catch (final Exception e) {
                    // the leader may be gone, try again
                  }
                }
              },
              "log-archive-it-load");
      thread.setDaemon(true);
      thread.start();
    }

    @Override
    public void close() {
      running.set(false);
      try {
        thread.join(30_000);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
