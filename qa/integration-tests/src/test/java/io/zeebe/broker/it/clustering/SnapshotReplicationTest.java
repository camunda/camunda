/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.Broker;
import io.zeebe.broker.it.util.GrpcClientRule;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.snapshots.broker.impl.FileBasedSnapshotMetadata;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class SnapshotReplicationTest {

  private static final int PARTITION_COUNT = 1;
  private static final Duration SNAPSHOT_PERIOD = Duration.ofMinutes(5);
  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

  private final ClusteringRule clusteringRule =
      new ClusteringRule(PARTITION_COUNT, 3, 3, SnapshotReplicationTest::configureBroker);
  public final GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);
  @Rule public RuleChain ruleChain = RuleChain.outerRule(clusteringRule).around(clientRule);

  @Test
  public void shouldReplicateSnapshots() throws IOException {
    // given
    final int leaderNodeId = clusteringRule.getLeaderForPartition(1).getNodeId();
    final Broker leader = clusteringRule.getBroker(leaderNodeId);

    // when
    triggerSnapshotCreation();
    clusteringRule.waitForSnapshotAtBroker(leader);
    final List<Broker> otherBrokers = clusteringRule.getOtherBrokerObjects(leaderNodeId);
    for (final Broker broker : otherBrokers) {
      clusteringRule.waitForSnapshotAtBroker(broker);
    }

    // then
    assertFollowersHaveSameSnapshotsAsLeader(0, 1, 2);
  }

  @Test
  public void shouldReceiveNewSnapshotsOnRejoin() throws IOException {
    // given
    final var leaderNodeId = clusteringRule.getLeaderForPartition(1).getNodeId();
    final var followers =
        clusteringRule.getOtherBrokerObjects(leaderNodeId).stream()
            .map(b -> b.getConfig().getCluster().getNodeId())
            .collect(Collectors.toList());

    final var firstFollowerId = followers.get(0);
    final var secondFollowerId = followers.get(1);

    // when - snapshot
    clusteringRule.stopBrokerAndAwaitNewLeader(firstFollowerId);
    triggerSnapshotCreation();
    final var snapshotAtSecondFollower =
        clusteringRule.waitForSnapshotAtBroker(clusteringRule.getBroker(secondFollowerId));
    clusteringRule.restartBroker(firstFollowerId);

    triggerSnapshotCreation();
    clusteringRule.waitForNewSnapshotAtBroker(
        clusteringRule.getBroker(firstFollowerId), snapshotAtSecondFollower);
    clusteringRule.waitForNewSnapshotAtBroker(
        clusteringRule.getBroker(secondFollowerId), snapshotAtSecondFollower);

    // then - replicated
    assertFollowersHaveSameSnapshotsAsLeader(leaderNodeId, firstFollowerId, secondFollowerId);
  }

  private void triggerSnapshotCreation() {
    clientRule.deployWorkflow(WORKFLOW);
    clusteringRule.getClock().addTime(SNAPSHOT_PERIOD);
  }

  @Test
  public void shouldReplicateSnapshotsOnLeaderChange() throws IOException {
    // given
    final var oldLeaderId = clusteringRule.getLeaderForPartition(1).getNodeId();
    final var otherBrokers = clusteringRule.getOtherBrokerObjects(oldLeaderId);
    final var firstFollowerId = otherBrokers.get(0).getConfig().getCluster().getNodeId();
    final var secondFollowerId = otherBrokers.get(1).getConfig().getCluster().getNodeId();

    // when
    triggerSnapshotCreation();
    final var snapshotAtLeader =
        clusteringRule.waitForSnapshotAtBroker(clusteringRule.getBroker(oldLeaderId));
    final var snapshotAtFirstFollower =
        clusteringRule.waitForSnapshotAtBroker(clusteringRule.getBroker(firstFollowerId));
    final var snapshotAtSecondFollower =
        clusteringRule.waitForSnapshotAtBroker(clusteringRule.getBroker(secondFollowerId));

    triggerLeaderChange(oldLeaderId);
    triggerSnapshotCreation();
    clusteringRule.waitForNewSnapshotAtBroker(
        clusteringRule.getBroker(firstFollowerId), snapshotAtFirstFollower);
    clusteringRule.waitForNewSnapshotAtBroker(
        clusteringRule.getBroker(secondFollowerId), snapshotAtSecondFollower);
    clusteringRule.waitForNewSnapshotAtBroker(
        clusteringRule.getBroker(oldLeaderId), snapshotAtLeader);

    // then
    assertFollowersHaveSameSnapshotsAsLeader(oldLeaderId, firstFollowerId, secondFollowerId);
  }

  private void assertFollowersHaveSameSnapshotsAsLeader(
      final int leaderNodeId, final int... followerIds) throws IOException {
    final List<Path> leaderSnapshots = getSnapshotsForBroker(leaderNodeId);
    for (final int followerId : followerIds) {
      final List<Path> followerSnapshots = getSnapshotsForBroker(followerId);
      assertThat(followerSnapshots)
          .as("broker %d has the same snapshots as broker %d", followerId, leaderNodeId)
          .zipSatisfy(leaderSnapshots, this::assertThatSnapshotIsEqualTo);
    }
  }

  private List<Path> getSnapshotsForBroker(final int brokerId) throws IOException {
    final Broker broker = clusteringRule.getBroker(brokerId);
    final Path snapshotsDirectory = clusteringRule.getSnapshotsDirectory(broker).toPath();
    try (final Stream<Path> files = Files.list(snapshotsDirectory)) {
      return files
          .filter(p -> FileBasedSnapshotMetadata.ofPath(p).isPresent())
          .collect(Collectors.toList());
    }
  }

  private void assertThatSnapshotIsEqualTo(final Path actualPath, final Path expectedPath) {
    final Path actualChecksumPath =
        actualPath.resolveSibling(actualPath.getFileName() + ".checksum");
    final Path expectedChecksumPath =
        expectedPath.resolveSibling(expectedPath.getFileName() + ".checksum");

    assertThat(actualPath)
        .isDirectory()
        .exists()
        .hasFileName(expectedPath.getFileName().toString());
    assertThat(actualChecksumPath)
        .exists()
        .hasFileName(expectedChecksumPath.getFileName().toString())
        .hasSameBinaryContentAs(expectedChecksumPath);

    final List<Path> expectedFiles;
    try {
      expectedFiles = Files.list(expectedPath).map(Path::getFileName).collect(Collectors.toList());
      assertThat(Files.list(actualPath).map(Path::getFileName))
          .containsExactlyInAnyOrderElementsOf(expectedFiles);

      for (final Path expectedFile : expectedFiles) {
        assertThat(actualPath.resolve(expectedFile))
            .hasSameBinaryContentAs(expectedPath.resolve(expectedFile));
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void triggerLeaderChange(final int oldLeaderId) {
    long newLeaderId;
    do {
      clusteringRule.restartBroker(oldLeaderId);
      newLeaderId = clusteringRule.getLeaderForPartition(1).getNodeId();
    } while (newLeaderId == oldLeaderId);
  }

  private static void configureBroker(final BrokerCfg brokerCfg) {
    brokerCfg.getData().setSnapshotPeriod(SNAPSHOT_PERIOD);
    brokerCfg.getData().setLogIndexDensity(1);
  }
}
