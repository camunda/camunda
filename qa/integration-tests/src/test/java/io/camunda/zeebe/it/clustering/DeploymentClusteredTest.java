/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering;

import static io.camunda.zeebe.broker.engine.impl.DeploymentDistributorImpl.PUSH_REQUEST_TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public final class DeploymentClusteredTest {

  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

  public final Timeout testTimeout = Timeout.seconds(120);
  public final ClusteringRule clusteringRule = new ClusteringRule(3, 1, 3);
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
  public void shouldDistributedDeploymentWhenStoppedBrokerIsRestarted() {
    // given
    final var leaderOfPartitionThree = clusteringRule.getLeaderForPartition(3).getNodeId();
    clusteringRule.stopBroker(leaderOfPartitionThree);

    // when
    final DeploymentEvent deploymentEvent =
        clientRule
            .getClient()
            .newDeployCommand()
            .addProcessModel(PROCESS, "process.bpmn")
            .send()
            .join();
    final var processDefinitionKey = deploymentEvent.getKey();

    // wait for long before restart.
    // Add time in small increments to simulate gradual progression of the time.
    // For each increment, we expect the broker to retry sending the deployment.
    // https://github.com/camunda-cloud/zeebe/issues/8525
    clusteringRule.getClock().addTime(PUSH_REQUEST_TIMEOUT);
    clusteringRule.getClock().addTime(PUSH_REQUEST_TIMEOUT);
    clusteringRule.getClock().addTime(PUSH_REQUEST_TIMEOUT);

    clusteringRule.restartBroker(leaderOfPartitionThree);

    // increase the clock to trigger resending the deployment.
    clusteringRule.getClock().addTime(PUSH_REQUEST_TIMEOUT);

    // then
    clientRule.waitUntilDeploymentIsDone(processDefinitionKey);
  }
}
