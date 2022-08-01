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
import io.camunda.zeebe.it.util.GrpcClientRule;
import java.time.Duration;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;
import org.springframework.util.unit.DataSize;

public class ReaderCloseTest {

  public final Timeout testTimeout = Timeout.seconds(120);
  public final ClusteringRule clusteringRule =
      new ClusteringRule(
          1,
          3,
          3,
          config -> {
            config.getNetwork().setMaxMessageSize(DataSize.ofKilobytes(8));
            config.getData().setLogSegmentSize(DataSize.ofKilobytes(8));
            // no exporters so that segments are immediately compacted after a snapshot.
            config.setExporters(Map.of());
          });
  public final GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(testTimeout).around(clusteringRule).around(clientRule);

  // Regression test for https://github.com/camunda/zeebe/issues/7767
  @Test
  public void shouldDeleteCompactedSegmentsFiles() {
    // given
    fillSegments();

    // when
    clusteringRule.triggerAndWaitForSnapshots();

    // then
    for (final Broker broker : clusteringRule.getBrokers()) {
      awaitNoDanglingReaders(broker);
    }
  }

  // Regression test for https://github.com/camunda/zeebe/issues/7767
  @Test
  public void shouldDeleteCompactedSegmentsFilesAfterLeaderChange() {
    // given
    fillSegments();
    final var leaderId = clusteringRule.getLeaderForPartition(1).getNodeId();
    final var followerId =
        clusteringRule.getOtherBrokerObjects(leaderId).stream()
            .findAny()
            .orElseThrow()
            .getConfig()
            .getCluster()
            .getNodeId();
    clusteringRule.forceClusterToHaveNewLeader(followerId);
    // because of https://github.com/camunda/zeebe/issues/8329
    // we need to add another record so we can do a snapshot
    clientRule
        .getClient()
        .newPublishMessageCommand()
        .messageName("test")
        .correlationKey("test")
        .send();

    // when
    clusteringRule.triggerAndWaitForSnapshots();

    // then
    for (final Broker broker : clusteringRule.getBrokers()) {
      awaitNoDanglingReaders(broker);
    }

    assertThat(leaderId).isNotEqualTo(clusteringRule.getLeaderForPartition(1).getNodeId());
  }

  private void awaitNoDanglingReaders(final Broker broker) {
    Awaitility.await("until all readers are closed, observed via segment deletion")
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(() -> assertThatFilesOfDeletedSegmentsDoesNotExist(broker));
  }

  private void assertThatFilesOfDeletedSegmentsDoesNotExist(final Broker leader) {
    final var segmentDirectory = clusteringRule.getSegmentsDirectory(leader);
    assertThat(segmentDirectory)
        .as(
            "broker <%s> closed all readers as it doesn't contain any marked-for-deletion segments",
            leader.getConfig().getCluster().getNodeId())
        .isDirectoryNotContaining("regex:.*-deleted");
  }

  private void fillSegments() {
    clusteringRule.runUntilSegmentsFilled(
        clusteringRule.getBrokers(),
        2,
        () ->
            clientRule
                .getClient()
                .newPublishMessageCommand()
                .messageName("msg")
                .correlationKey("key")
                .send()
                .join());
  }
}
