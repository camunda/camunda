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
import java.util.concurrent.atomic.AtomicLong;
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
  void shouldReportPartitionAsNotHealthyWhenReplayReachesZeroedCommittedRange() throws IOException {
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
    final long zeroedUntil = awaitFollowerStoppedAppending(segment, zeroedFrom);

    // when - the range reads back as zeros, and replay reaches it
    zero(segment, zeroedFrom, zeroedUntil);
    followerPartitions.resumeProcessing();
    publishMessages(10);

    // then - catching up is not an outcome here, as it would mean that nothing was zeroed
    Awaitility.await("follower reports its partition as not healthy")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(partitionHealthOf(follower))
                    .isNotEqualTo(PartitionBrokerHealth.HEALTHY));
  }

  /**
   * Waits until the follower's segment no longer grows, and returns where its data ends. Zeroing
   * while the follower appends would cut an entry in half, which fails its checksum instead of
   * making it look like the end of the log. The leader has processed every message when its publish
   * returns, so nothing more is replicated once the entries written for them arrived.
   */
  private static long awaitFollowerStoppedAppending(final Path segment, final long from) {
    final var previousEnd = new AtomicLong(from);
    return Awaitility.await("follower appended the new entries, and appends no more")
        .pollInterval(Duration.ofMillis(500))
        .atMost(Duration.ofSeconds(30))
        .until(
            () -> {
              final long end = endOfData(segment);
              return previousEnd.getAndSet(end) == end ? end : from;
            },
            end -> end > from);
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
