/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering;

import static io.camunda.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public final class DeploymentClusteredTest {

  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

  public final Timeout testTimeout = Timeout.seconds(120);
  public final ClusteringRule clusteringRule = new ClusteringRule(3, 3, 3);
  public final GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(testTimeout).around(clusteringRule).around(clientRule);

  @Test
  public void shouldDeployProcessAndCreateInstances() {
    // when
    final var processDefinitionKey = clientRule.deployProcess(PROCESS);

    final var processInstanceKeys =
        clusteringRule.getPartitionIds().stream()
            .map(
                partitionId ->
                    clusteringRule.createProcessInstanceOnPartition(partitionId, "process"))
            .collect(Collectors.toList());

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .filterRootScope()
                .withProcessDefinitionKey(processDefinitionKey)
                .limit(clusteringRule.getPartitionCount()))
        .extracting(Record::getKey)
        .containsExactlyInAnyOrderElementsOf(processInstanceKeys);
  }

  @Test
  public void shouldDeployProcessEvenAfterLeaderChange() {
    // given
    final Set<Integer> nodes =
        clusteringRule.getBrokers().stream()
            .map(b -> b.getConfig().getCluster().getNodeId())
            .collect(Collectors.toSet());

    final var leaderForPartitionOne = clusteringRule.getLeaderForPartition(1);
    final var leaderForPartitionThree = clusteringRule.getLeaderForPartition(3);
    assertThat(leaderForPartitionOne.getNodeId()).isNotEqualTo(leaderForPartitionThree.getNodeId());

    nodes.remove(leaderForPartitionOne.getNodeId());
    nodes.remove(leaderForPartitionThree.getNodeId());

    // when
    clusteringRule.disconnectOneWay(
        leaderForPartitionThree.getNodeId(), leaderForPartitionOne.getNodeId());
    final var processDefinitionKey = clientRule.deployProcessWithoutAwait(PROCESS);
    clusteringRule.connectOneWay(
        leaderForPartitionThree.getNodeId(), leaderForPartitionOne.getNodeId());

    waitUntil(
        () -> RecordingExporter.deploymentRecords(DeploymentIntent.FULLY_DISTRIBUTED).exists());

    // then
    final var processInstanceKeys =
        clusteringRule.getPartitionIds().stream()
            .map(
                partitionId ->
                    clusteringRule.createProcessInstanceOnPartition(partitionId, "process"))
            .collect(Collectors.toList());

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .filterRootScope()
                .withProcessDefinitionKey(processDefinitionKey)
                .limit(clusteringRule.getPartitionCount()))
        .extracting(Record::getKey)
        .containsExactlyInAnyOrderElementsOf(processInstanceKeys);
  }
}
