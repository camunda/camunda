/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.clustering;

import io.zeebe.broker.Broker;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.DataCfg;
import io.zeebe.broker.system.configuration.ExporterCfg;
import io.zeebe.exporter.api.Exporter;
import io.zeebe.exporter.api.context.Controller;
import io.zeebe.protocol.record.Record;
import io.zeebe.test.util.TestUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
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
        (Consumer<BrokerCfg>) ClusteredDataDeletionTest::configureCustomExporter,
        "updating-exporter"
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
    brokerCfg.getNetwork().setMaxMessageSize("8K");

    brokerCfg.setExporters(Collections.EMPTY_LIST);
  }

  public static void configureCustomExporter(final BrokerCfg brokerCfg) {
    final DataCfg data = brokerCfg.getData();
    data.setMaxSnapshots(MAX_SNAPSHOTS);
    data.setSnapshotPeriod(SNAPSHOT_PERIOD_SECONDS + "s");
    data.setLogSegmentSize("8k");
    brokerCfg.getNetwork().setMaxMessageSize("8K");

    final ExporterCfg exporterCfg = new ExporterCfg();
    exporterCfg.setClassName(TestExporter.class.getName());
    exporterCfg.setId("data-delete-test-exporter");

    // overwrites RecordingExporter on purpose because since it doesn't update its position
    // we wouldn't be able to delete data
    brokerCfg.setExporters(Collections.singletonList(exporterCfg));
  }

  @Test
  public void shouldDeleteDataOnLeader() {
    // given
    final int leaderNodeId = clusteringRule.getLeaderForPartition(1).getNodeId();
    final Broker leader = clusteringRule.getBroker(leaderNodeId);

    while (getSegmentsCount(leader) <= 2) {
      clusteringRule
          .getClient()
          .newPublishMessageCommand()
          .messageName("msg")
          .correlationKey("key")
          .send()
          .join();
    }

    // when
    final var segmentCount =
        takeSnapshotAndWaitForReplication(Collections.singletonList(leader), clusteringRule);

    // then
    TestUtil.waitUntil(() -> getSegments(leader).size() < segmentCount.get(leaderNodeId));
  }

  @Test
  public void shouldDeleteDataOnFollowers() {
    // given
    final int leaderNodeId = clusteringRule.getLeaderForPartition(1).getNodeId();
    final List<Broker> followers =
        clusteringRule.getBrokers().stream()
            .filter(b -> b.getConfig().getCluster().getNodeId() != leaderNodeId)
            .collect(Collectors.toList());

    while (followers.stream().map(this::getSegmentsCount).allMatch(count -> count <= 2)) {
      clusteringRule
          .getClient()
          .newPublishMessageCommand()
          .messageName("msg")
          .correlationKey("key")
          .send()
          .join();
    }

    // when
    final var followerSegmentCounts = takeSnapshotAndWaitForReplication(followers, clusteringRule);

    // then
    TestUtil.waitUntil(
        () ->
            followers.stream()
                .allMatch(
                    b ->
                        getSegments(b).size()
                            < followerSegmentCounts.get(b.getConfig().getCluster().getNodeId())));
  }

  private Map<Integer, Integer> takeSnapshotAndWaitForReplication(
      final List<Broker> brokers, ClusteringRule clusteringRule) {
    final Map<Integer, Integer> segmentCounts = new HashMap<>();
    brokers.forEach(
        b -> {
          final int nodeId = b.getConfig().getCluster().getNodeId();
          segmentCounts.put(nodeId, getSegments(b).size());
        });

    clusteringRule.getClock().addTime(Duration.ofSeconds(SNAPSHOT_PERIOD_SECONDS));
    brokers.forEach(clusteringRule::waitForValidSnapshotAtBroker);
    return segmentCounts;
  }

  private int getSegmentsCount(Broker broker) {
    return getSegments(broker).size();
  }

  private Collection<Path> getSegments(Broker broker) {
    try {
      return Files.list(clusteringRule.getSegmentsDirectory(broker))
          .filter(path -> path.toString().endsWith(".log"))
          .collect(Collectors.toList());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static class TestExporter implements Exporter {
    public static List<Record> records = new CopyOnWriteArrayList<>();
    private Controller controller;

    @Override
    public void open(final Controller controller) {
      this.controller = controller;
    }

    @Override
    public void export(final Record record) {
      records.add(record);
      controller.updateLastExportedRecordPosition(record.getPosition());
    }
  }
}
