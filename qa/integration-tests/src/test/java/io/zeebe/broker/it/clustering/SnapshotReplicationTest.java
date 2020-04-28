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
import io.zeebe.broker.clustering.atomix.storage.snapshot.DbSnapshotMetadata;
import io.zeebe.broker.it.util.GrpcClientRule;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.CRC32C;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class SnapshotReplicationTest {

  private static final int PARTITION_COUNT = 1;
  private static final Duration SNAPSHOT_PERIOD = Duration.ofMinutes(5);
  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("process").startEvent().endEvent().done();
  private static final String WORKFLOW_RESOURCE_NAME = "workflow.bpmn";

  private final ClusteringRule clusteringRule =
      new ClusteringRule(PARTITION_COUNT, 3, 3, SnapshotReplicationTest::configureBroker);
  public final GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);
  @Rule public RuleChain ruleChain = RuleChain.outerRule(clusteringRule).around(clientRule);
  private ZeebeClient client;

  @Before
  public void init() {
    client = clientRule.getClient();
  }

  @Test
  public void shouldReplicateSnapshots() {
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
    final Map<Integer, Map<String, Long>> brokerSnapshotChecksums = getBrokerSnapshotChecksums();
    final Map<String, Long> checksumFirstNode = brokerSnapshotChecksums.get(0);
    assertThat(checksumFirstNode).isEqualTo(brokerSnapshotChecksums.get(1));
    assertThat(checksumFirstNode).isEqualTo(brokerSnapshotChecksums.get(2));
  }

  @Test
  public void shouldReceiveLatestSnapshotOnRejoin() {
    // given
    final var leaderNodeId = clusteringRule.getLeaderForPartition(1).getNodeId();
    final var followers =
        clusteringRule.getOtherBrokerObjects(leaderNodeId).stream()
            .map(b -> b.getConfig().getCluster().getNodeId())
            .collect(Collectors.toList());

    final var firstFollowerId = followers.get(0);
    final var secondFollowerId = followers.get(1);

    // when - snapshot
    clusteringRule.stopBroker(firstFollowerId);
    triggerSnapshotCreation();
    clusteringRule.restartBroker(firstFollowerId);
    clusteringRule.waitForSnapshotAtBroker(clusteringRule.getBroker(secondFollowerId));
    clusteringRule.waitForSnapshotAtBroker(clusteringRule.getBroker(firstFollowerId));

    // then - replicated
    final Map<Integer, Map<String, Long>> brokerSnapshotChecksums = getBrokerSnapshotChecksums();
    final var leaderChecksums = Objects.requireNonNull(brokerSnapshotChecksums.get(leaderNodeId));
    assertThat(brokerSnapshotChecksums.get(firstFollowerId)).containsAllEntriesOf(leaderChecksums);
    assertThat(brokerSnapshotChecksums.get(secondFollowerId)).containsAllEntriesOf(leaderChecksums);
  }

  @Test
  public void shouldReceiveNewSnapshotsOnRejoin() {
    // given
    final var leaderNodeId = clusteringRule.getLeaderForPartition(1).getNodeId();
    final var followers =
        clusteringRule.getOtherBrokerObjects(leaderNodeId).stream()
            .map(b -> b.getConfig().getCluster().getNodeId())
            .collect(Collectors.toList());

    final var firstFollowerId = followers.get(0);
    final var secondFollowerId = followers.get(1);

    // when - snapshot
    clusteringRule.stopBroker(firstFollowerId);
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
    final Map<Integer, Map<String, Long>> brokerSnapshotChecksums = getBrokerSnapshotChecksums();
    final var leaderChecksums = Objects.requireNonNull(brokerSnapshotChecksums.get(leaderNodeId));
    assertThat(brokerSnapshotChecksums.get(firstFollowerId)).containsAllEntriesOf(leaderChecksums);
    assertThat(brokerSnapshotChecksums.get(secondFollowerId)).containsAllEntriesOf(leaderChecksums);
  }

  private void triggerSnapshotCreation() {
    client.newDeployCommand().addWorkflowModel(WORKFLOW, WORKFLOW_RESOURCE_NAME).send().join();
    clusteringRule.getClock().addTime(SNAPSHOT_PERIOD);
  }

  @Test
  public void shouldReplicateSnapshotsOnLeaderChange() {
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
    // the old leader will have an extra snapshot it created on shut down, so we swap the condition
    // to check that the other nodes contain a subset of the old leader's snapshots
    final Map<Integer, Map<String, Long>> brokerSnapshotChecksums = getBrokerSnapshotChecksums();
    final var oldLeaderChecks = Objects.requireNonNull(brokerSnapshotChecksums.get(oldLeaderId));
    assertThat(oldLeaderChecks).containsAllEntriesOf(brokerSnapshotChecksums.get(firstFollowerId));
    assertThat(oldLeaderChecks).containsAllEntriesOf(brokerSnapshotChecksums.get(secondFollowerId));
  }

  private Map<Integer, Map<String, Long>> getBrokerSnapshotChecksums() {
    final Map<Integer, Map<String, Long>> brokerSnapshotChecksums = new HashMap<>();
    for (final var broker : clusteringRule.getBrokers()) {
      final var checksums = createSnapshotDirectoryChecksums(broker);
      brokerSnapshotChecksums.put(broker.getConfig().getCluster().getNodeId(), checksums);
    }
    return brokerSnapshotChecksums;
  }

  private void triggerLeaderChange(final int oldLeaderId) {
    long newLeaderId;
    do {
      clusteringRule.restartBroker(oldLeaderId);
      newLeaderId = clusteringRule.getLeaderForPartition(1).getNodeId();
    } while (newLeaderId == oldLeaderId);
  }

  private Map<String, Long> createSnapshotDirectoryChecksums(final Broker broker) {
    final File snapshotsDir = clusteringRule.getSnapshotsDirectory(broker);
    final Map<String, Long> checksums = createChecksumsForSnapshotDirectory(snapshotsDir);

    assertThat(checksums.size()).isGreaterThan(0);
    return checksums;
  }

  private Map<String, Long> createChecksumsForSnapshotDirectory(final File snapshotDirectory) {
    final Map<String, Long> checksums = new HashMap<>();
    final var snapshotPath = snapshotDirectory.toPath();
    try (final var snapshots = Files.newDirectoryStream(snapshotPath)) {
      for (final var validSnapshotDir : snapshots) {
        final Map<String, Long> snapshotChecksum = createChecksumsForSnapshot(validSnapshotDir);
        checksums.putAll(snapshotChecksum);
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    return checksums;
  }

  /**
   * Builds a map where each entry is a file of the snapshot, with the key being its filename
   * prefixed with the snapshot index/term/timestamp, and the value the checksum of the file. We
   * ignore the position in the prefix because it can differ, as snapshots taking by the async
   * director will have a lower bound estimate of the last processed position, but snapshots
   * replicated through Atomix actually read the snapshot for the real last processed position which
   * may be slightly different.
   */
  private Map<String, Long> createChecksumsForSnapshot(final Path validSnapshotDir)
      throws IOException {
    final var snapshotMetadata =
        DbSnapshotMetadata.ofPath(validSnapshotDir.getFileName()).orElseThrow();
    final String prefix =
        String.format(
            "%d-%d-%d",
            snapshotMetadata.getIndex(),
            snapshotMetadata.getTerm(),
            snapshotMetadata.getTimestamp().unixTimestamp());
    try (final var files = Files.list(validSnapshotDir)) {
      return files.collect(
          Collectors.toMap(
              p -> prefix + "/" + p.getFileName().toString(), this::createCheckSumForFile));
    }
  }

  private long createCheckSumForFile(final Path snapshotFile) {
    final var checksum = new CRC32C();
    final byte[] bytes;
    try {
      bytes = Files.readAllBytes(snapshotFile);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    checksum.update(bytes, 0, bytes.length);
    return checksum.getValue();
  }

  private static void configureBroker(final BrokerCfg brokerCfg) {
    brokerCfg.getData().setSnapshotPeriod(SNAPSHOT_PERIOD);
    brokerCfg.getData().setLogIndexDensity(1);
  }
}
