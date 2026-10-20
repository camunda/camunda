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
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A new leader goes on publishing where the floor it got from the old leader stands. */
@ZeebeIntegration
final class LeaderChangeIT {

  @TempDir private static Path buckets;
  private final Path bucket = buckets.resolve(UUID.randomUUID().toString());

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(3)
          .withPartitionsCount(1)
          .withReplicationFactor(3)
          .withEmbeddedGateway(true)
          .withBrokerConfig(
              broker ->
                  EventPlaneBrokers.withEventPlane(broker, bucket)
                      .withRecordingExporter(false)
                      .withSecondaryStorageType(SecondaryStorageType.none))
          .build();

  @Test
  void shouldGoOnPublishingAfterALeaderChange() throws Exception {
    // given
    final var store = new FileSystemStore(bucket);
    publishMessages(20);
    final var log = await("the first segments").until(() -> Bucket.logs(store), l -> !l.isEmpty());
    final var before =
        await("records in the bucket")
            .timeout(Duration.ofSeconds(30))
            .until(() -> Bucket.readLog(store, log.getFirst()), read -> read.lastPosition() > 0);

    // when
    cluster.leaderForPartition(1).stop();
    await("a new leader")
        .timeout(Duration.ofMinutes(1))
        .ignoreExceptions()
        .untilAsserted(() -> publishMessages(1));
    publishMessages(20);

    // then
    final var after =
        await("the new leader's uploads")
            .timeout(Duration.ofSeconds(60))
            .until(
                () -> Bucket.readLog(store, log.getFirst()),
                read -> read.lastPosition() > before.lastPosition());
    assertThat(Bucket.logs(store)).containsExactly(log.getFirst());
    assertThat(after.indexesAreContiguous()).isTrue();
    assertThat(after.positionsIncrease()).isTrue();
    assertThat(after.histories()).containsExactly(before.history());
  }

  private void publishMessages(final int count) {
    try (final var client = cluster.newClientBuilder().build()) {
      for (int i = 0; i < count; i++) {
        client
            .newPublishMessageCommand()
            .messageName("event-plane")
            .correlationKey("key-" + i)
            .send()
            .join();
      }
    }
  }
}
