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

import io.camunda.configuration.data.SecondaryStorage.SecondaryStorageType;
import io.camunda.zeebe.eventplane.consumer.ConsumerListener;
import io.camunda.zeebe.eventplane.consumer.EventPlaneConsumer;
import io.camunda.zeebe.eventplane.consumer.RecordHandler;
import io.camunda.zeebe.eventplane.consumer.Start;
import io.camunda.zeebe.eventplane.maintenance.Maintenance;
import io.camunda.zeebe.eventplane.maintenance.MaintenanceConfig;
import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.eventplane.store.FileSystemStore;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.nio.file.Path;
import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Maintenance rolls segments up and applies retention while workers read. */
@ZeebeIntegration
final class MaintenanceIT {

  @TempDir private static Path buckets;
  private final Path bucket = buckets.resolve(UUID.randomUUID().toString());
  private final FileSystemStore store = new FileSystemStore(bucket);
  private final List<EventPlaneConsumer> workers = new ArrayList<>();

  @TestZeebe
  private final TestStandaloneBroker broker =
      EventPlaneBrokers.withEventPlane(new TestStandaloneBroker(), bucket)
          .withRecordingExporter(false)
          .withSecondaryStorageType(SecondaryStorageType.none);

  @AfterEach
  void tearDown() {
    workers.forEach(EventPlaneConsumer::close);
  }

  @Test
  void shouldRollUpWhileWorkersRead() throws Exception {
    // given
    final Set<Long> delivered = new ConcurrentSkipListSet<>();
    startWorker(
        "readers", (log, record) -> delivered.add(record.getPosition()), ConsumerListener.NONE);
    final var maintenance =
        new Maintenance(
            store,
            new MaintenanceConfig(Duration.ofSeconds(2), 1L << 20, null),
            InstantSource.system());

    // when
    for (int round = 0; round < 8; round++) {
      EventPlaneBrokers.publishMessages(broker, 10);
      maintenance.runOnce();
      Thread.sleep(500);
    }
    await("old segments rolled up")
        .timeout(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              maintenance.runOnce();
              assertThat(levels()).contains(1);
            });

    // then
    final var log = Bucket.logs(store).getFirst();
    final var read = Bucket.readLog(store, log);
    assertThat(read.indexesAreContiguous()).isTrue();
    await("every record delivered across the rollups")
        .timeout(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(delivered)
                    .containsAll(LongStream.rangeClosed(1, read.lastPosition()).boxed().toList()));
  }

  @Test
  void shouldTellAGroupThatFellBehindRetentionAboutTheGap() throws Exception {
    // given a group that read the first records and stopped
    final Set<Long> delivered = new ConcurrentSkipListSet<>();
    final List<long[]> gaps = new CopyOnWriteArrayList<>();
    final ConsumerListener listener =
        new ConsumerListener() {
          @Override
          public void onGap(final LogId log, final long fromIndex, final long toIndex) {
            gaps.add(new long[] {fromIndex, toIndex});
          }
        };
    final var first =
        startWorker("slow", (log, record) -> delivered.add(record.getPosition()), listener);
    EventPlaneBrokers.publishMessages(broker, 5);
    await("the first records delivered")
        .timeout(Duration.ofSeconds(30))
        .until(() -> uploadedLastPosition() > 0 && delivered.contains(uploadedLastPosition()));
    first.close();
    final long stoppedAt = uploadedLastPosition();

    // and segments it didn't read, which age past the retention, and young ones after them
    EventPlaneBrokers.publishMessages(broker, 20);
    await("unread uploads").until(() -> uploadedLastPosition() > stoppedAt);
    Thread.sleep(5_000);
    EventPlaneBrokers.publishMessages(broker, 20);
    final long beforeYoung = uploadedLastPosition();
    await("young uploads").until(() -> uploadedLastPosition() > beforeYoung);
    Thread.sleep(1_000);

    // when
    final var report =
        new Maintenance(
                store,
                new MaintenanceConfig(Duration.ofDays(1), 1L << 20, Duration.ofSeconds(4)),
                InstantSource.system())
            .runOnce();
    EventPlaneBrokers.publishMessages(broker, 5);
    startWorker("slow", (log, record) -> delivered.add(record.getPosition()), listener);

    // then
    assertThat(report.markersWritten()).isOne();
    assertThat(report.segmentsDeleted()).isPositive();
    final var kept = Bucket.readLog(store, Bucket.logs(store).getFirst());
    final long firstKept =
        kept.all().stream()
            .filter(entry -> entry.hasRecords())
            .mapToLong(entry -> entry.firstPosition())
            .min()
            .orElseThrow();
    await("the group goes on after the gap")
        .timeout(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              assertThat(gaps).hasSize(1);
              assertThat(delivered)
                  .containsAll(
                      LongStream.rangeClosed(firstKept, uploadedLastPosition()).boxed().toList());
            });
  }

  private long uploadedLastPosition() throws Exception {
    final var logs = Bucket.logs(store);
    return logs.isEmpty() ? -1 : Bucket.readLog(store, logs.getFirst()).lastPosition();
  }

  private EventPlaneConsumer startWorker(
      final String group, final RecordHandler handler, final ConsumerListener listener) {
    final var worker =
        EventPlaneConsumer.builder()
            .store(store)
            .group(group)
            .workerId(group + "-1")
            .subscribe("*", "*")
            .start(Start.EARLIEST)
            .handler(handler)
            .listener(listener)
            .heartbeatInterval(Duration.ofMillis(200))
            .lease(Duration.ofSeconds(2))
            .cursorInterval(Duration.ofMillis(500))
            .listingInterval(Duration.ofMillis(500))
            .build();
    worker.start();
    workers.add(worker);
    return worker;
  }

  private Set<Integer> levels() throws Exception {
    final var levels = new TreeSet<Integer>();
    for (final var log : Bucket.logs(store)) {
      final var prefix = ObjectNames.logPrefix(log);
      for (final var directory : store.listPrefixes(prefix)) {
        for (final var object : store.list(directory, null, Integer.MAX_VALUE)) {
          if (object.key().endsWith(".seg")) {
            levels.add(ObjectNames.parseSegment(object.key()).level());
          }
        }
      }
    }
    return levels;
  }
}
