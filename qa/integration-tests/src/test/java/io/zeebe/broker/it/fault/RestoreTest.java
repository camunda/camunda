/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.broker.it.fault;

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.ATOMIX_SERVICE;
import static io.zeebe.protocol.Protocol.START_PARTITION_ID;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.core.Atomix;
import io.atomix.protocols.raft.partition.RaftPartition;
import io.zeebe.broker.Broker;
import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.it.clustering.ClusteringRule;
import io.zeebe.distributedlog.impl.LogstreamConfig;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.util.ByteValue;
import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class RestoreTest {
  private static final int ATOMIX_SEGMENT_SIZE = (int) ByteValue.ofMegabytes(2).toBytes();
  private static final int LARGE_PAYLOAD_BYTESIZE = (int) ByteValue.ofKilobytes(32).toBytes();
  private static final String LARGE_PAYLOAD =
      "{\"blob\": \"" + getRandomBase64Bytes(LARGE_PAYLOAD_BYTESIZE) + "\"}";

  private static final int SNAPSHOT_PERIOD_MIN = 5;
  private final ClusteringRule clusteringRule =
      new ClusteringRule(
          1,
          3,
          3,
          cfg -> {
            cfg.getData().setMaxSnapshots(1);
            cfg.getData().setSnapshotPeriod(SNAPSHOT_PERIOD_MIN + "m");
            cfg.getData().setSnapshotReplicationPeriod(SNAPSHOT_PERIOD_MIN + "m");
            cfg.getData().setRaftSegmentSize(ByteValue.ofBytes(ATOMIX_SEGMENT_SIZE).toString());
          });
  private final GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(clusteringRule).around(clientRule);

  @Test
  public void shouldReplicateLogEvents() {
    // given
    clusteringRule.stopBroker(2);

    final BpmnModelInstance firstWorkflow =
        Bpmn.createExecutableProcess("process-test1").startEvent().endEvent().done();

    final BpmnModelInstance secondWorkflow =
        Bpmn.createExecutableProcess("process-test2").startEvent().endEvent().done();

    final BpmnModelInstance thirdWorkflow =
        Bpmn.createExecutableProcess("process-test3").startEvent().endEvent().done();

    // when
    final long firstWorkflowKey = clientRule.deployWorkflow(firstWorkflow);
    clusteringRule.getClock().addTime(Duration.ofMinutes(SNAPSHOT_PERIOD_MIN));
    waitForValidSnapshotAtBroker(getLeader());

    final long secondWorkflowKey = clientRule.deployWorkflow(secondWorkflow);

    writeManyEventsUntilAtomixLogIsCompactable();
    waitUntilAtomixBackup(clusteringRule.getLeaderForPartition(1).getNodeId());

    clusteringRule.restartBroker(2);
    waitUntil(() -> !LogstreamConfig.isRestoring("2", 1));
    waitForValidSnapshotAtBroker(clusteringRule.getBroker(2));

    clusteringRule.stopBroker(1);

    final long thirdWorkflowKey = clientRule.deployWorkflow(thirdWorkflow);

    writeManyEventsUntilAtomixLogIsCompactable();
    waitUntilAtomixBackup(clusteringRule.getLeaderForPartition(1).getNodeId());

    clusteringRule.restartBroker(1);
    waitUntil(() -> !LogstreamConfig.isRestoring("1", 1));

    clusteringRule.stopBroker(0);

    // then
    // If restore did not happen, following workflows won't be deployed
    assertThat(clientRule.createWorkflowInstance(firstWorkflowKey)).isPositive();
    assertThat(clientRule.createWorkflowInstance(secondWorkflowKey)).isPositive();
    assertThat(clientRule.createWorkflowInstance(thirdWorkflowKey)).isPositive();
  }

  private Broker getLeader() {
    return clusteringRule.getBroker(
        clusteringRule.getLeaderForPartition(START_PARTITION_ID).getNodeId());
  }

  private void writeManyEventsUntilAtomixLogIsCompactable() {
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess("process").startEvent().endEvent().done();
    final long workflowKey = clientRule.deployWorkflow(workflow);
    final int requiredInstances = Math.floorDiv(ATOMIX_SEGMENT_SIZE, LARGE_PAYLOAD_BYTESIZE) + 1;
    IntStream.range(0, requiredInstances)
        .forEach(i -> clientRule.createWorkflowInstance(workflowKey, LARGE_PAYLOAD));
  }

  private static String getRandomBase64Bytes(long size) {
    final byte[] bytes = new byte[(int) size];
    ThreadLocalRandom.current().nextBytes(bytes);

    return Base64.getEncoder().encodeToString(bytes);
  }

  private void waitUntilAtomixBackup(int nodeId) {
    final Atomix atomix = clusteringRule.getService(ATOMIX_SERVICE, nodeId);
    atomix.getPartitionService().getPartitionGroup("raft-atomix").getPartitions().stream()
        .map(RaftPartition.class::cast)
        .forEach(p -> p.snapshot().join());

    final File atomixBackupDirectory = getAtomixBackupDirectory(clusteringRule.getBroker(nodeId));
    waitUntil(
        () ->
            Arrays.stream(atomixBackupDirectory.listFiles())
                .anyMatch(f -> f.getName().contains(".snapshot")));
  }

  private File getAtomixBackupDirectory(Broker broker) {
    final String dataDir = broker.getConfig().getData().getDirectories().get(0);
    return new File(dataDir, "raft-atomix/partitions/1/");
  }

  private File getSnapshotsDirectory(Broker broker) {
    final String dataDir = broker.getConfig().getData().getDirectories().get(0);
    return new File(dataDir, "partition-1/state/1_zb-stream-processor/snapshots");
  }

  private void waitForValidSnapshotAtBroker(Broker broker) {
    final File snapshotsDir = getSnapshotsDirectory(broker);

    waitUntil(
        () -> Arrays.stream(snapshotsDir.listFiles()).anyMatch(f -> !f.getName().contains("tmp")));
  }
}
