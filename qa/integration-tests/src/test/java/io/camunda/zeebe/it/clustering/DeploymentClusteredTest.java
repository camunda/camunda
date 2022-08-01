/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering;

import static io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentRedistributor.DEPLOYMENT_REDISTRIBUTION_INTERVAL;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public final class DeploymentClusteredTest {

  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

  public final Timeout testTimeout = Timeout.seconds(120);
  public final ClusteringRule clusteringRule =
      new ClusteringRule(
          3,
          1,
          3,
          brokerCfg ->
              brokerCfg.getExperimental().getConsistencyChecks().setEnablePreconditions(true));
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
            .newDeployResourceCommand()
            .addProcessModel(PROCESS, "process.bpmn")
            .send()
            .join();
    final var processDefinitionKey = deploymentEvent.getKey();

    // wait for long before restart.
    // Add time in small increments to simulate gradual progression of the time.
    // For each increment, we expect the broker to retry sending the deployment.
    // https://github.com/camunda/zeebe/issues/8525

    clusteringRule.getClock().addTime(DEPLOYMENT_REDISTRIBUTION_INTERVAL);
    clusteringRule.getClock().addTime(DEPLOYMENT_REDISTRIBUTION_INTERVAL);
    clusteringRule.getClock().addTime(DEPLOYMENT_REDISTRIBUTION_INTERVAL);

    clusteringRule.restartBroker(leaderOfPartitionThree);

    // increase the clock to trigger resending the deployment.
    clusteringRule.getClock().addTime(DEPLOYMENT_REDISTRIBUTION_INTERVAL);

    // then
    clientRule.waitUntilDeploymentIsDone(processDefinitionKey);
  }

  /**
   * Regression test against https://github.com/camunda/zeebe/issues/9877
   *
   * <p>We expect that the DISTRIBUTE command is written a second time, after restart of the leader
   * of the deployment partition. Both DISTRIBUTE commands should be processed and resulting in
   * DISTRIBUTED events. These should be idempotently applied, for example using `upsert`.
   */
  @Test
  public void shouldDistributeDmnResourceOnRetry() {
    final var leaderForDeploymentPartition =
        clusteringRule.getLeaderForPartition(Protocol.DEPLOYMENT_PARTITION).getNodeId();
    final var leaderForPartitionTwo = clusteringRule.getLeaderForPartition(2).getNodeId();
    final var adminServiceLeaderTwo =
        clusteringRule.getBroker(leaderForPartitionTwo).getBrokerContext().getBrokerAdminService();

    // given
    adminServiceLeaderTwo.pauseStreamProcessing();

    clientRule
        .getClient()
        .newDeployResourceCommand()
        .addResourceFromClasspath("dmn/decision-table.dmn")
        .send()
        .join();

    Awaitility.await("until deployment distribution is send once to partition 2")
        .atMost(Duration.ofMinutes(1))
        .pollInterval(Duration.ofMillis(200))
        .until(
            () ->
                RecordingExporter.deploymentRecords(DeploymentIntent.DISTRIBUTE)
                    .withPartitionId(2)
                    .withResourceName("dmn/decision-table.dmn")
                    .findAny()
                    .isPresent());

    RecordingExporter.reset();

    // when
    clusteringRule.stopBroker(leaderForDeploymentPartition);
    adminServiceLeaderTwo.resumeStreamProcessing();
    clusteringRule.startBroker(leaderForDeploymentPartition);

    // then
    Awaitility.await("until partition 2 has processed distribute command twice")
        .atMost(Duration.ofMinutes(1))
        .pollInterval(Duration.ofMillis(200))
        .untilAsserted(
            () ->
                assertThat(
                        RecordingExporter.deploymentRecords(DeploymentIntent.DISTRIBUTED)
                            .withPartitionId(2)
                            .withResourceName("dmn/decision-table.dmn")
                            .limit(2))
                    .describedAs("expect that deployment is distributed twice")
                    .hasSize(2));
  }
}
