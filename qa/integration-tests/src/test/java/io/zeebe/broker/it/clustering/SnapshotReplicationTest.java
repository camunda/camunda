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
import io.zeebe.broker.it.clustering.ClusteredDataDeletionTest.TestExporter;
import io.zeebe.broker.it.util.GrpcClientRule;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.DataCfg;
import io.zeebe.broker.system.configuration.ExporterCfg;
import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class SnapshotReplicationTest {

  private static final int PARTITION_COUNT = 1;
  private static final int SNAPSHOT_PERIOD_SECONDS = 30;

  // ensures we do not trigger deletion after replication; if we do, then the followers might open
  // the database which causes extra files to appear and messes up the checksum counting
  private static final int MAX_SNAPSHOTS = 2;

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

  // NOTE: the configuration removes the RecordingExporter from the broker's configuration to enable
  // data deletion so it can't be used in tests
  public ClusteringRule clusteringRule =
      new ClusteringRule(PARTITION_COUNT, 3, 3, SnapshotReplicationTest::configureCustomExporter);
  public GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(clusteringRule).around(clientRule);

  @Rule public ExpectedException expectedException = ExpectedException.none();

  private ZeebeClient client;

  @Before
  public void init() {
    client = clientRule.getClient();
  }

  @Test
  public void shouldReplicateSnapshots() {
    // given
    client.newDeployCommand().addWorkflowModel(WORKFLOW, "workflow.bpmn").send().join();
    final int leaderNodeId = clusteringRule.getLeaderForPartition(1).getNodeId();
    final Broker leader = clusteringRule.getBroker(leaderNodeId);
    clusteringRule.getClock().addTime(Duration.ofSeconds(SNAPSHOT_PERIOD_SECONDS));

    // when - snapshot
    clusteringRule.waitForValidSnapshotAtBroker(leader);

    final List<Broker> otherBrokers = clusteringRule.getOtherBrokerObjects(leaderNodeId);
    for (final Broker broker : otherBrokers) {
      clusteringRule.waitForValidSnapshotAtBroker(broker);
    }

    // then - replicated
    final Collection<Broker> brokers = clusteringRule.getBrokers();
    final Map<Integer, Map<String, Long>> brokerSnapshotChecksums = new HashMap<>();
    for (Broker broker : brokers) {
      final Map<String, Long> checksums = createSnapshotDirectoryChecksums(broker);
      brokerSnapshotChecksums.put(broker.getConfig().getCluster().getNodeId(), checksums);
    }

    final Map<String, Long> checksumFirstNode = brokerSnapshotChecksums.get(0);
    assertThat(checksumFirstNode).isEqualTo(brokerSnapshotChecksums.get(1));
    assertThat(checksumFirstNode).isEqualTo(brokerSnapshotChecksums.get(2));
  }

  private Map<String, Long> createSnapshotDirectoryChecksums(Broker broker) {
    final File snapshotsDir = clusteringRule.getSnapshotsDirectory(broker);

    final Map<String, Long> checksums = createChecksumsForSnapshotDirectory(snapshotsDir);

    assertThat(checksums.size()).isGreaterThan(0);
    return checksums;
  }

  private Map<String, Long> createChecksumsForSnapshotDirectory(File snapshotDirectory) {
    final Map<String, Long> checksums = new HashMap<>();
    final File[] snapshotDirs = snapshotDirectory.listFiles();
    if (snapshotDirs != null) {
      Arrays.stream(snapshotDirs)
          .filter(f -> !f.getName().contains("tmp"))
          .forEach(
              validSnapshotDir -> {
                final File[] snapshotFiles = validSnapshotDir.listFiles();
                if (snapshotFiles != null) {
                  for (final File snapshotFile : snapshotFiles) {
                    final long checksum = createCheckSumForFile(snapshotFile);
                    checksums.put(snapshotFile.getName(), checksum);
                  }
                }
              });
    }

    return checksums;
  }

  private long createCheckSumForFile(File snapshotFile) {
    try (CheckedInputStream checkedInputStream =
        new CheckedInputStream(Files.newInputStream(snapshotFile.toPath()), new CRC32())) {
      while (checkedInputStream.skip(512) > 0) {}

      return checkedInputStream.getChecksum().getValue();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void configureCustomExporter(final BrokerCfg brokerCfg) {
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
}
