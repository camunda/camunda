/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.clustering;

import static io.zeebe.test.util.TestUtil.waitUntil;

import io.zeebe.broker.Broker;
import io.zeebe.broker.it.DataDeleteTest;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.DataCfg;
import io.zeebe.test.util.TestUtil;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClusteredDataDeletionTest {
  private static final int SNAPSHOT_PERIOD_SECONDS = 30;
  private static final int MAX_SNAPSHOTS = 1;
  @Parameter public Consumer<BrokerCfg> configurator;

  @Parameter(1)
  public String name;

  private ClusteringRule clusteringRule;

  @Parameters(name = "{index}: {1}")
  public static Object[][] configurators() {
    return new Object[][] {
      new Object[] {
        (Consumer<BrokerCfg>) ClusteredDataDeletionTest::configureNoExporters, "no-exporter"
      },
      new Object[] {
        (Consumer<BrokerCfg>) DataDeleteTest::configureCustomExporter, "updating-exporter"
      }
    };
  }

  @Before
  public void setup() throws IOException {
    clusteringRule = new ClusteringRule(1, 3, 3, configurator);
    clusteringRule.before();
  }

  @After
  public void tearDown() {
    clusteringRule.after();
  }

  private static void configureNoExporters(final BrokerCfg brokerCfg) {
    final DataCfg data = brokerCfg.getData();
    data.setMaxSnapshots(MAX_SNAPSHOTS);
    data.setSnapshotPeriod(SNAPSHOT_PERIOD_SECONDS + "s");
    data.setLogSegmentSize("8k");

    brokerCfg.setExporters(Collections.EMPTY_LIST);
  }

  @Test
  public void shouldDeleteDataOnLeader() {
    // given
    final int leaderNodeId = clusteringRule.getLeaderForPartition(1).getNodeId();
    final Broker leader = clusteringRule.getBroker(leaderNodeId);

    while (getSegmentsDirectory(leader).listFiles().length <= 2) {
      clusteringRule
          .getClient()
          .newPublishMessageCommand()
          .messageName("msg")
          .correlationKey("key")
          .send()
          .join();
    }

    // when
    final HashMap<Integer, Integer> segmentCount =
        takeSnapshotAndWaitForReplication(Collections.singletonList(leader), clusteringRule);

    // then
    TestUtil.waitUntil(
        () -> getSegmentsDirectory(leader).listFiles().length < segmentCount.get(leaderNodeId));
  }

  @Test
  public void shouldDeleteDataOnFollowers() {
    // given
    final int leaderNodeId = clusteringRule.getLeaderForPartition(1).getNodeId();
    final List<Broker> followers =
        clusteringRule.getBrokers().stream()
            .filter(b -> b.getConfig().getCluster().getNodeId() != leaderNodeId)
            .collect(Collectors.toList());

    while (followers.stream()
        .map(this::getSegmentsDirectory)
        .allMatch(dir -> dir.listFiles().length <= 2)) {
      clusteringRule
          .getClient()
          .newPublishMessageCommand()
          .messageName("msg")
          .correlationKey("key")
          .send()
          .join();
    }

    // when
    final HashMap<Integer, Integer> followerSegmentCounts =
        takeSnapshotAndWaitForReplication(followers, clusteringRule);

    // then
    TestUtil.waitUntil(
        () ->
            followers.stream()
                .allMatch(
                    b ->
                        getSegmentsDirectory(b).listFiles().length
                            < followerSegmentCounts.get(b.getConfig().getCluster().getNodeId())));
  }

  private HashMap<Integer, Integer> takeSnapshotAndWaitForReplication(
      final List<Broker> brokers, ClusteringRule clusteringRule) {
    final HashMap<Integer, Integer> segmentCounts = new HashMap();
    brokers.forEach(
        b -> {
          final int nodeId = b.getConfig().getCluster().getNodeId();
          segmentCounts.put(nodeId, getSegmentsDirectory(b).list().length);
        });

    clusteringRule.getClock().addTime(Duration.ofSeconds(DataDeleteTest.SNAPSHOT_PERIOD_SECONDS));
    brokers.forEach(this::waitForValidSnapshotAtBroker);
    return segmentCounts;
  }

  private File getSnapshotsDirectory(Broker broker) {
    final String dataDir = broker.getConfig().getData().getDirectories().get(0);
    return new File(dataDir, "partition-1/state/snapshots");
  }

  private File getSegmentsDirectory(Broker broker) {
    final String dataDir = broker.getConfig().getData().getDirectories().get(0);
    return new File(dataDir, "/partition-1/segments");
  }

  private void waitForValidSnapshotAtBroker(Broker broker) {
    final File snapshotsDir = getSnapshotsDirectory(broker);

    waitUntil(
        () -> Arrays.stream(snapshotsDir.listFiles()).anyMatch(f -> !f.getName().contains("tmp")));
  }
}
