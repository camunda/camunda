/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.data.SecondaryStorage.SecondaryStorageType;
import io.camunda.zeebe.eventplane.history.HistoryManifest.Base;
import io.camunda.zeebe.eventplane.history.ManifestJson;
import io.camunda.zeebe.eventplane.history.RestoredHistory;
import io.camunda.zeebe.eventplane.segment.Entry;
import io.camunda.zeebe.eventplane.snapshot.SnapshotArchive;
import io.camunda.zeebe.eventplane.snapshot.SnapshotDescriptor;
import io.camunda.zeebe.eventplane.store.FileSystemStore;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.journal.JournalMetaStore;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.restore.RestoreManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A broker's data directory is restored from the bucket alone, an archived snapshot and the
 * segments after it, and the broker goes on from it in a new history.
 */
@ZeebeIntegration
final class RestoreFromBucketIT {

  private static final String PROCESS = "event-plane-restore";
  private static final String JOB_TYPE = "event-plane-restore-job";
  private static final String PARTITION_NAME = "default-partition-1";

  @TempDir private static Path directories;
  private final Path bucket = directories.resolve("bucket-" + UUID.randomUUID());
  private final Path workingDirectory = directories.resolve("broker-" + UUID.randomUUID());
  private final Path original = directories.resolve("original-" + UUID.randomUUID());

  @TestZeebe
  private final TestStandaloneBroker broker =
      EventPlaneBrokers.withEventPlane(new TestStandaloneBroker(), bucket)
          .withProperty(EventPlaneBrokers.PREFIX + "snapshotInterval", "1s")
          .withWorkingDirectory(workingDirectory)
          .withRecordingExporter(false)
          .withSecondaryStorageType(SecondaryStorageType.none);

  @Test
  void shouldRestoreTheArchivedJournalAndGoOnInANewHistory() throws Exception {
    // given: instances before a snapshot, which the bucket gets, and instances after it
    final var store = new FileSystemStore(bucket);
    final var instances = new HashSet<Long>();
    deployProcess();
    instances.addAll(createInstances(10));
    final var log = await("the first segments").until(() -> Bucket.logs(store), l -> !l.isEmpty());
    final var cluster = log.getFirst().clusterId();
    // a snapshot keeps every entry after the floor, so it is taken once the floor has moved on
    awaitTheWholeJournalInTheBucket(store, log.getFirst());
    final var partitions = PartitionsActuator.of(broker);
    partitions.takeSnapshot();
    final var snapshotId =
        await("a snapshot")
            .timeout(Duration.ofSeconds(60))
            .until(() -> partitions.query().get(1).snapshotId(), Objects::nonNull);
    await("the snapshot in the bucket")
        .timeout(Duration.ofSeconds(60))
        .until(
            () -> SnapshotArchive.descriptor(store, cluster, "default", 1, snapshotId),
            Optional::isPresent);
    final long beforeTheSecondBatch = Bucket.readLog(store, log.getFirst()).lastPosition();
    instances.addAll(createInstances(10));
    final var archived = awaitTheWholeJournalInTheBucket(store, log.getFirst());
    final var snapshots = SnapshotArchive.list(store, cluster, "default", 1).complete();
    final var configuration = broker.bean(BrokerBasedProperties.class);
    final var partitionDirectory =
        Path.of(configuration.getData().getDirectory()).resolve("default/partitions/1");

    // when
    broker.stop();
    Files.move(partitionDirectory, original);
    final var restoreHistory = UUID.randomUUID();
    configuration.getExperimental().getEventPlane().setRestoreFromBucket(true);
    configuration.getExperimental().getEventPlane().setRestoreHistory(restoreHistory.toString());
    try (final var manager = new RestoreManager(configuration, null, new SimpleMeterRegistry())) {
      manager.restoreFromBucket();
    }

    // then: the newest snapshot whose positions the bucket's log reaches is installed
    final var snapshot =
        snapshots.stream()
            .filter(descriptor -> descriptor.requiredPosition() <= archived.lastPosition())
            .max(Comparator.comparingLong(SnapshotDescriptor::index))
            .orElseThrow();
    final var snapshotsShown =
        snapshots.stream()
            .map(
                d ->
                    "%s (index %d, processed %d, required %d)"
                        .formatted(
                            d.snapshotId(), d.index(), d.processedPosition(), d.requiredPosition()))
            .toList();
    assertThat(snapshot.index())
        .describedAs("the index of a snapshot of the journal, of %s", snapshotsShown)
        .isPositive();
    assertThat(snapshot.processedPosition())
        .describedAs("the snapshot's processed position, before the second batch of instances")
        .isLessThanOrEqualTo(beforeTheSecondBatch);
    assertThat(partitionDirectory.resolve("snapshots").resolve(snapshot.snapshotId()))
        .isDirectory();

    // and the journal after it is the archived one, byte for byte
    final var restored = readJournal(partitionDirectory);
    final var archivedByIndex = new TreeMap<Long, Entry>();
    archived.all().forEach(entry -> archivedByIndex.putIfAbsent(entry.index(), entry));
    assertThat(restored.getFirst().index())
        .describedAs("the first restored entry, after snapshot %s of %s", snapshot, snapshotsShown)
        .isEqualTo(snapshot.index() + 1);
    assertThat(restored)
        .containsExactlyElementsOf(
            archivedByIndex
                .subMap(snapshot.index() + 1, true, archivedByIndex.lastKey(), true)
                .values());
    assertThat(restored)
        .describedAs("the same journal as the broker had")
        .containsExactlyElementsOf(
            readJournal(original).stream()
                .filter(entry -> entry.index() > snapshot.index())
                .toList());
    final var restoredHistory = RestoredHistory.readFrom(partitionDirectory).orElseThrow();
    assertThat(restoredHistory.history()).isEqualTo(restoreHistory);
    assertThat(restoredHistory.source()).isEqualTo(archived.history());
    assertThat(restoredHistory.index()).isEqualTo(restored.getLast().index());
    assertThat(restoredHistory.position()).isEqualTo(archived.lastPosition());

    // and the broker keeps its state, and publishes on in the new history
    broker.start().awaitCompleteTopology();
    assertThat(activateAllJobs(instances.size()))
        .extracting(ActivatedJob::getProcessInstanceKey)
        .containsExactlyInAnyOrderElementsOf(instances);
    final var part =
        await("the new history's part")
            .until(
                () -> store.get(ObjectNames.historyPart(cluster, "default", restoreHistory, 1)),
                content -> content.isPresent());
    assertThat(ManifestJson.read(part.orElseThrow()).partitions())
        .containsEntry(
            1, new Base(archived.history(), restoredHistory.index(), restoredHistory.position()));
    final var created = createInstances(5);
    final var after =
        await("segments of the new history")
            .timeout(Duration.ofSeconds(30))
            .until(
                () -> Bucket.readLog(store, log.getFirst()),
                read ->
                    read.histories().contains(restoreHistory)
                        && read.lastPosition() > restoredHistory.position());
    assertThat(after.histories()).containsExactlyInAnyOrder(archived.history(), restoreHistory);
    assertThat(created).hasSize(5);
  }

  private void deployProcess() {
    try (final var client = broker.newClientBuilder().build()) {
      client
          .newDeployResourceCommand()
          .addProcessModel(
              Bpmn.createExecutableProcess(PROCESS)
                  .startEvent()
                  .serviceTask("task", task -> task.zeebeJobType(JOB_TYPE))
                  .endEvent()
                  .done(),
              PROCESS + ".bpmn")
          .send()
          .join();
    }
  }

  private List<Long> createInstances(final int count) {
    final var keys = new ArrayList<Long>();
    try (final var client = broker.newClientBuilder().build()) {
      for (int i = 0; i < count; i++) {
        keys.add(
            client
                .newCreateInstanceCommand()
                .bpmnProcessId(PROCESS)
                .latestVersion()
                .send()
                .join()
                .getProcessInstanceKey());
      }
    }
    return keys;
  }

  private List<ActivatedJob> activateAllJobs(final int expected) {
    final var jobs = new ArrayList<ActivatedJob>();
    try (final var client = broker.newClientBuilder().build()) {
      await("every instance's job")
          .timeout(Duration.ofSeconds(60))
          .untilAsserted(
              () -> {
                jobs.addAll(
                    client
                        .newActivateJobsCommand()
                        .jobType(JOB_TYPE)
                        .maxJobsToActivate(expected)
                        .timeout(Duration.ofMinutes(5))
                        .send()
                        .join()
                        .getJobs());
                assertThat(jobs).hasSizeGreaterThanOrEqualTo(expected);
              });
    }
    return jobs;
  }

  /**
   * Waits until the publisher has uploaded every entry: the bucket's last entry stays the same for
   * longer than a segment's cut interval, and the floor stands at its last record.
   */
  private Bucket.LogRead awaitTheWholeJournalInTheBucket(
      final FileSystemStore store, final Bucket.LogRef log) {
    final var lastIndex = new AtomicLong(-1);
    final var partitions = PartitionsActuator.of(broker);
    return await("the whole journal in the bucket")
        .timeout(Duration.ofSeconds(60))
        .pollInterval(Duration.ofSeconds(2))
        .until(
            () -> Bucket.readLog(store, log),
            read -> {
              final long last = read.all().stream().mapToLong(Entry::index).max().orElse(-1);
              final var status = partitions.query().get(1);
              return last == lastIndex.getAndSet(last)
                  && status.processedPosition() != null
                  && read.lastPosition() >= status.processedPosition()
                  && Objects.equals(status.exportedPosition(), read.lastPosition());
            });
  }

  /** Every entry of a partition's journal, as the journal holds it. */
  private static List<Entry> readJournal(final Path partitionDirectory) {
    final var entries = new ArrayList<Entry>();
    try (final var journal =
            SegmentedJournal.builder(new SimpleMeterRegistry())
                .withDirectory(partitionDirectory.toFile())
                .withName(PARTITION_NAME)
                .withMetaStore(new JournalMetaStore.InMemory())
                .build();
        final var reader = journal.openReader()) {
      while (reader.hasNext()) {
        final var record = reader.next();
        final var bytes = new byte[record.serializedRecord().capacity()];
        record.serializedRecord().getBytes(0, bytes);
        entries.add(new Entry(record.checksum(), bytes));
      }
    }
    return entries;
  }
}
