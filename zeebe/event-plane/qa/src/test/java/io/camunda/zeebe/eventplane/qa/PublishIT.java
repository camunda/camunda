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
import io.camunda.zeebe.eventplane.store.FileSystemStore;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A broker publishes its journal into a bucket on the filesystem, and the floor follows. */
@ZeebeIntegration
final class PublishIT {

  @TempDir private static Path buckets;
  private final Path bucket = buckets.resolve(UUID.randomUUID().toString());

  @TestZeebe
  private final TestStandaloneBroker broker =
      EventPlaneBrokers.withEventPlane(new TestStandaloneBroker(), bucket)
          .withRecordingExporter(false)
          .withSecondaryStorageType(SecondaryStorageType.none);

  @Test
  void shouldPublishTheJournalIntoTheBucket() throws Exception {
    // given
    final var store = new FileSystemStore(bucket);

    // when
    EventPlaneBrokers.publishMessages(broker, 20);

    // then
    final var log = await("the first segments").until(() -> Bucket.logs(store), l -> !l.isEmpty());
    final var segments =
        await("segments with records")
            .timeout(Duration.ofSeconds(30))
            .until(() -> Bucket.readLog(store, log.getFirst()), read -> read.lastPosition() > 0);
    assertThat(segments.indexesAreContiguous()).isTrue();
    assertThat(segments.positionsIncrease()).isTrue();
    assertThat(
            store.get(
                ObjectNames.historyPart(
                    log.getFirst().clusterId(), "default", segments.history(), 1)))
        .isPresent();

    // the floor follows the uploads, and compaction stops there
    final var partitions = PartitionsActuator.of(broker);
    await("the floor reaches the uploaded records")
        .timeout(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(partitions.query().get(1).exportedPosition())
                    .isEqualTo(Bucket.readLog(store, log.getFirst()).lastPosition()));
  }

  @Test
  void shouldResumeAfterTheFloorWhenTheBrokerRestarts() throws Exception {
    // given
    final var store = new FileSystemStore(bucket);
    EventPlaneBrokers.publishMessages(broker, 10);
    final var log = await("the first segments").until(() -> Bucket.logs(store), l -> !l.isEmpty());
    final var before =
        await("records in the bucket")
            .until(() -> Bucket.readLog(store, log.getFirst()), read -> read.lastPosition() > 0);
    // the floor lives in the exporter state, which survives a restart in a snapshot
    PartitionsActuator.of(broker).takeSnapshot();
    await("a snapshot")
        .until(() -> PartitionsActuator.of(broker).query().get(1).snapshotId() != null);

    // when
    broker.stop();
    broker.start().awaitCompleteTopology();
    EventPlaneBrokers.publishMessages(broker, 10);

    // then
    final var after =
        await("the new records in the bucket")
            .timeout(Duration.ofSeconds(30))
            .until(
                () -> Bucket.readLog(store, log.getFirst()),
                read -> read.lastPosition() > before.lastPosition());
    assertThat(after.indexesAreContiguous()).isTrue();
    assertThat(after.duplicateEntries())
        .describedAs("the restarted leader resumes after the snapshot's floor, not from the start")
        .isLessThan(before.entries());
    assertThat(after.histories()).containsExactly(before.history());
  }
}
