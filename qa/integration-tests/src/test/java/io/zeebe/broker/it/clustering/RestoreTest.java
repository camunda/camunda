/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.clustering;

import static io.zeebe.protocol.Protocol.START_PARTITION_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.Broker;
import io.zeebe.broker.it.util.GrpcClientRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.util.ByteValue;
import java.time.Duration;
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
            cfg.getData().setRaftSegmentSize(ByteValue.ofBytes(ATOMIX_SEGMENT_SIZE).toString());
            cfg.getNetwork().setMaxMessageSize(ByteValue.ofBytes(ATOMIX_SEGMENT_SIZE).toString());
          });
  private final GrpcClientRule clientRule =
      new GrpcClientRule(
          config ->
              config
                  .brokerContactPoint(clusteringRule.getGatewayAddress().toString())
                  .defaultRequestTimeout(Duration.ofMinutes(1))
                  .usePlaintext());

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
    clusteringRule.waitForValidSnapshotAtBroker(getLeader());

    final long secondWorkflowKey = clientRule.deployWorkflow(secondWorkflow);

    writeManyEventsUntilAtomixLogIsCompactable();
    clusteringRule.waitForValidSnapshotAtBroker(
        clusteringRule.getBroker(clusteringRule.getLeaderForPartition(1).getNodeId()));

    clusteringRule.restartBroker(2);
    clusteringRule.waitForValidSnapshotAtBroker(clusteringRule.getBroker(2));

    clusteringRule.stopBroker(1);

    final long thirdWorkflowKey = clientRule.deployWorkflow(thirdWorkflow);

    writeManyEventsUntilAtomixLogIsCompactable();
    clusteringRule.waitForValidSnapshotAtBroker(
        clusteringRule.getBroker(clusteringRule.getLeaderForPartition(1).getNodeId()));

    clusteringRule.restartBroker(1);
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

  private static String getRandomBase64Bytes(final long size) {
    final byte[] bytes = new byte[(int) size];
    ThreadLocalRandom.current().nextBytes(bytes);

    return Base64.getEncoder().encodeToString(bytes);
  }
}
