/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.partitioning.startup.RaftPartitionFactory;
import io.camunda.zeebe.broker.system.configuration.DataCfg;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.PartitionBrokerHealth;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Reproduces https://github.com/camunda/camunda/issues/69499: committed entries of a follower's
 * journal segment read back as zeros while the broker keeps the segment mapped, as after a lost
 * writeback.
 */
@ZeebeIntegration
final class ZeroedCommittedJournalRangeIT {
  private static final int PARTITION_ID = 1;

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withEmbeddedGateway(true)
          .withBrokersCount(3)
          .withPartitionsCount(1)
          .withReplicationFactor(3)
          .build();

  @AutoClose private ZeebeClient client;

  @BeforeEach
  void setup() {
    client = cluster.newClientBuilder().build();
  }

  @Test
  void shouldNotSilentlyStallReplayOnZeroedCommittedRange() throws IOException {
    // given - a follower whose replay is paused, so that it has not yet read what is zeroed below
    final var leader = cluster.leaderForPartition(PARTITION_ID);
    final var follower =
        cluster.brokers().values().stream().filter(b -> b != leader).findFirst().orElseThrow();
    final var leaderPartitions = PartitionsActuator.of(leader);
    final var followerPartitions = PartitionsActuator.of(follower);

    publishMessages(10);
    awaitFollowerReplayedEverything(leaderPartitions, followerPartitions);
    followerPartitions.pauseProcessing();

    final var segment = onlySegmentOf(follower);
    final long zeroedFrom = endOfData(segment);
    publishMessages(10);
    final long zeroedUntil =
        Awaitility.await("follower appended the new entries")
            .atMost(Duration.ofSeconds(30))
            .until(() -> endOfData(segment), end -> end > zeroedFrom);

    // when - the range reads back as zeros, and replay reaches it
    zero(segment, zeroedFrom, zeroedUntil);
    followerPartitions.resumeProcessing();
    publishMessages(10);

    // then - the follower either replays up to the leader or reports that it cannot
    Awaitility.await("follower catches up with the leader or reports itself as not healthy")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var followerHealth = partitionHealthOf(follower);
              if (followerHealth != PartitionBrokerHealth.HEALTHY) {
                return;
              }
              final long leaderPosition = processedPosition(leaderPartitions);
              final long followerPosition = processedPosition(followerPartitions);
              assertThat(followerPosition)
                  .describedAs(
                      "follower reports its partition as %s, so it must replay until the position"
                          + " %d that the leader processed",
                      followerHealth, leaderPosition)
                  .isGreaterThanOrEqualTo(leaderPosition);
            });
  }

  private void publishMessages(final int count) {
    for (int i = 0; i < count; i++) {
      client
          .newPublishMessageCommand()
          .messageName("msg-" + UUID.randomUUID())
          .correlationKey("key-" + UUID.randomUUID())
          .send()
          .join();
    }
  }

  private void awaitFollowerReplayedEverything(
      final PartitionsActuator leaderPartitions, final PartitionsActuator followerPartitions) {
    Awaitility.await("follower replayed everything the leader processed")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(processedPosition(followerPartitions))
                    .isPositive()
                    .isEqualTo(processedPosition(leaderPartitions)));
  }

  private static long processedPosition(final PartitionsActuator partitions) {
    final var status = partitions.query().get(PARTITION_ID);
    assertThat(status).describedAs("status of partition %d", PARTITION_ID).isNotNull();
    return status.processedPosition();
  }

  private PartitionBrokerHealth partitionHealthOf(final TestStandaloneBroker broker) {
    final int nodeId = Integer.parseInt(broker.nodeId().id());
    return client.newTopologyRequest().send().join().getBrokers().stream()
        .filter(b -> b.getNodeId() == nodeId)
        .flatMap(b -> b.getPartitions().stream())
        .filter(p -> p.getPartitionId() == PARTITION_ID)
        .findFirst()
        .orElseThrow()
        .getHealth();
  }

  private static Path onlySegmentOf(final TestStandaloneBroker broker) throws IOException {
    final var partitionDirectory =
        broker
            .getWorkingDirectory()
            .resolve(DataCfg.DEFAULT_DIRECTORY)
            .resolve(RaftPartitionFactory.GROUP_NAME)
            .resolve("partitions")
            .resolve(String.valueOf(PARTITION_ID));
    try (final var files = Files.list(partitionDirectory)) {
      final var segments = files.filter(file -> file.toString().endsWith(".log")).toList();
      assertThat(segments).describedAs("journal segments of the follower").hasSize(1);
      return segments.getFirst();
    }
  }

  /**
   * Returns the offset after the last non-zero byte. Segments are pre-allocated with zeros, so this
   * is at most the end of the last appended entry; an entry may itself end in zero padding.
   */
  private static long endOfData(final Path segment) throws IOException {
    final byte[] bytes = Files.readAllBytes(segment);
    int end = bytes.length;
    while (end > 0 && bytes[end - 1] == 0) {
      end--;
    }
    return end;
  }

  /** Writes through the file, not the broker's mapping, which then reads the zeros back. */
  private static void zero(final Path segment, final long from, final long until)
      throws IOException {
    try (final var channel = FileChannel.open(segment, StandardOpenOption.WRITE)) {
      final var zeros = ByteBuffer.allocate(Math.toIntExact(until - from));
      while (zeros.hasRemaining()) {
        channel.write(zeros, from + zeros.position());
      }
      channel.force(false);
    }
  }
}
