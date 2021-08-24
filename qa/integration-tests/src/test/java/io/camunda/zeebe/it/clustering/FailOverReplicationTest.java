/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.snapshots.SnapshotId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.springframework.util.unit.DataSize;

public class FailOverReplicationTest {

  private static final int PARTITION_COUNT = 1;
  private static final Duration SNAPSHOT_PERIOD = Duration.ofMinutes(5);
  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess("process").startEvent().endEvent().done();
  private static final String PROCESS_RESOURCE_NAME = "process.bpmn";

  private final ClusteringRule clusteringRule =
      new ClusteringRule(PARTITION_COUNT, 3, 3, FailOverReplicationTest::configureBroker);
  public final GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);
  @Rule public RuleChain ruleChain = RuleChain.outerRule(clusteringRule).around(clientRule);
  private ZeebeClient client;

  @Before
  public void init() {
    client = clientRule.getClient();
  }

  @Test
  public void shouldFailOverOnNetworkPartition() {
    // given
    final var leaderNodeId = clusteringRule.getLeaderForPartition(1).getNodeId();
    final var leader = clusteringRule.getBroker(leaderNodeId);

    // when
    clusteringRule.disconnect(leader);

    // then
    final var newLeader = clusteringRule.awaitOtherLeader(1, leaderNodeId);
    assertThat(newLeader.getNodeId()).isNotEqualTo(leaderNodeId);
  }

  // This test verifies that disconnect works as expected, otherwise the other tests are useless.
  // This test can be removed if we migrate the tests to test containers
  @Test
  public void shouldNotReceiveEntriesOnDisconnect() {
    // given
    final var segmentCount = 2;
    final var oldLeaderId = clusteringRule.getLeaderForPartition(1).getNodeId();
    final var oldLeader = clusteringRule.getBroker(oldLeaderId);
    clusteringRule.disconnect(oldLeader);

    // when
    final var brokerInfo = clusteringRule.awaitOtherLeader(1, oldLeaderId);
    assertThat(brokerInfo.getNodeId()).isNotEqualTo(oldLeaderId);
    final List<Broker> others = clusteringRule.getOtherBrokerObjects(oldLeaderId);
    awaitFilledSegmentsOnBrokers(others, segmentCount);

    // then
    assertThat(getSegmentsCount(oldLeader)).isLessThan(segmentCount);
  }

  @Test
  public void shouldReceiveEntriesAfterNetworkPartition() {
    // given
    final var segmentCount = 2;
    final var oldLeaderId = clusteringRule.getLeaderForPartition(1).getNodeId();
    final var oldLeader = clusteringRule.getBroker(oldLeaderId);
    clusteringRule.disconnect(oldLeader);
    clusteringRule.awaitOtherLeader(1, oldLeaderId);
    final List<Broker> followers = clusteringRule.getOtherBrokerObjects(oldLeaderId);
    awaitFilledSegmentsOnBrokers(followers, segmentCount);

    // when
    clusteringRule.connect(oldLeader);

    // then
    Awaitility.await()
        .pollInterval(Duration.ofMillis(100))
        .atMost(Duration.ofSeconds(10))
        .until(() -> getSegmentsCount(oldLeader), count -> count >= segmentCount);
    assertThat(getSegmentsCount(oldLeader)).isGreaterThanOrEqualTo(segmentCount);
  }

  @Test
  public void shouldReceiveSnapshotAfterNetworkPartition() {
    // given
    final var segmentCount = 2;
    final var previousLeaderId = clusteringRule.getLeaderForPartition(1).getNodeId();
    final var previousLeader = clusteringRule.getBroker(previousLeaderId);
    clusteringRule.disconnect(previousLeader);
    final var newLeaderInfo = clusteringRule.awaitOtherLeader(1, previousLeaderId);
    final var newLeader = clusteringRule.getBroker(newLeaderInfo.getNodeId());
    final List<Broker> followers = clusteringRule.getOtherBrokerObjects(previousLeaderId);
    awaitFilledSegmentsOnBrokers(followers, segmentCount);
    final var snapshotMetadata = awaitSnapshot(newLeader);

    // when
    clusteringRule.connect(previousLeader);

    // then
    final var receivedSnapshot = clusteringRule.waitForSnapshotAtBroker(previousLeader);
    assertThat(receivedSnapshot).isEqualTo(snapshotMetadata);
  }

  // regression test for https://github.com/zeebe-io/zeebe/issues/4810
  @Test
  public void shouldFormClusterEvenWhenMissingEvents() {
    // given
    final var previousLeaderId = clusteringRule.getLeaderForPartition(1).getNodeId();
    final var previousLeader = clusteringRule.getBroker(previousLeaderId);
    client.newDeployCommand().addProcessModel(PROCESS, PROCESS_RESOURCE_NAME).send().join();

    // disconnect leader - becomes follower
    clusteringRule.disconnect(previousLeader);
    // IMPORTANT NOTE:
    // BE AWARE THAT WE NEED TO STEP DOWN AFTER DISCONNECT
    // IT HAPPENS FROM TIME TO TIME THAT THE LEADER DOESN'T DETECT FAST ENOUGH THE NETWORK
    // PARTITION.
    // THIS MEANS IT WILL NOT STEP DOWN AND SEND NO NEW APPENDS TO THE FOLLOWER, BECAUSE IT HAS ONE
    // ON GOING,
    // WHICH SEEM NOT TO TIMEOUT CORRECTLY. SINCE MESSAGING SERVICE IS NOT STOPPED IN PRODUCTION
    // ENV.
    // WE WILL NOT FIX THIS - INSTEAD USE THIS AS WORK AROUND.
    clusteringRule.stepDown(previousLeader, 1);
    final var newLeaderInfo = clusteringRule.awaitOtherLeader(1, previousLeaderId);
    final var newLeaderId = newLeaderInfo.getNodeId();
    assertThat(newLeaderId).isNotEqualTo(previousLeaderId);
    final var newLeader = clusteringRule.getBroker(newLeaderId);

    final List<Broker> followers = clusteringRule.getOtherBrokerObjects(newLeaderId);
    final var followerA =
        followers.stream()
            .filter(broker -> broker.getConfig().getCluster().getNodeId() != previousLeaderId)
            .findFirst()
            .orElseThrow();

    // Leader and Follower A have new entries
    // which Follower B - old leader hasn't
    awaitFilledSegmentsOnBrokers(List.of(newLeader, followerA), 2);
    awaitSnapshot(newLeader);
    clusteringRule.takeSnapshot(followerA);
    clusteringRule.waitForSnapshotAtBroker(followerA);

    // when shutdown current leader and connect follower (old leader with old log)
    clusteringRule.stopBroker(newLeaderId);
    clusteringRule.connect(previousLeader);

    // then
    // we should be able to form a cluster via replicating snapshot etc.
    clusteringRule.waitForSnapshotAtBroker(previousLeader);
    final var nextLeader = clusteringRule.awaitOtherLeader(1, newLeaderId);

    assertThat(nextLeader.getNodeId()).isEqualTo(getNodeId(followerA));
    clusteringRule.waitForSnapshotAtBroker(previousLeader);
  }

  private int getNodeId(final Broker broker) {
    return broker.getConfig().getCluster().getNodeId();
  }

  private SnapshotId awaitSnapshot(final Broker leader) {
    triggerSnapshotCreation();
    return clusteringRule.waitForSnapshotAtBroker(leader);
  }

  private int getSegmentsCount(final Broker broker) {
    return getSegments(broker).size();
  }

  private Collection<Path> getSegments(final Broker broker) {
    try {
      return Files.list(clusteringRule.getSegmentsDirectory(broker))
          .filter(path -> path.toString().endsWith(".log"))
          .collect(Collectors.toList());
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void awaitFilledSegmentsOnBrokers(final List<Broker> brokers, final int segmentCount) {

    while (brokers.stream().map(this::getSegmentsCount).allMatch(count -> count <= segmentCount)) {

      writeRecord();
    }
  }

  private void writeRecord() {
    clusteringRule
        .getClient()
        .newPublishMessageCommand()
        .messageName("msg")
        .correlationKey("key")
        .send()
        .join();
  }

  private void triggerSnapshotCreation() {
    client.newDeployCommand().addProcessModel(PROCESS, PROCESS_RESOURCE_NAME).send().join();
    clusteringRule.getClock().addTime(SNAPSHOT_PERIOD);
  }

  private static void configureBroker(final BrokerCfg brokerCfg) {
    final var data = brokerCfg.getData();
    data.setSnapshotPeriod(SNAPSHOT_PERIOD);

    data.setLogSegmentSize(DataSize.ofKilobytes(8));
    data.setLogIndexDensity(1);
    brokerCfg.getNetwork().setMaxMessageSize(DataSize.ofKilobytes(8));
  }
}
