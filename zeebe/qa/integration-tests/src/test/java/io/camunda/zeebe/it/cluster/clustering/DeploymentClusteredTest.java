/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering;

import static io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentRedistributor.DEPLOYMENT_REDISTRIBUTION_INTERVAL;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.stream.Collectors;
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
    // https://github.com/camunda/camunda/issues/8525

    clusteringRule.getClock().addTime(DEPLOYMENT_REDISTRIBUTION_INTERVAL);
    clusteringRule.getClock().addTime(DEPLOYMENT_REDISTRIBUTION_INTERVAL);
    clusteringRule.getClock().addTime(DEPLOYMENT_REDISTRIBUTION_INTERVAL);

    clusteringRule.restartBroker(leaderOfPartitionThree);

    // increase the clock to trigger resending the deployment.
    clusteringRule.getClock().addTime(DEPLOYMENT_REDISTRIBUTION_INTERVAL);

    // then
    clientRule.waitUntilDeploymentIsDone(processDefinitionKey);
  }

  @Test
  public void shouldRedistributeDeploymentWhenDeploymentPartitionIsRestarted() {
    // given
    final var deploymentPartitionLeader =
        clusteringRule.getLeaderForPartition(Protocol.DEPLOYMENT_PARTITION).getNodeId();
    // We must pause the second partition. If we don't do this the deployment will send the
    // distribution and it will be handled by the second partition accordingly. Without pausing
    // it would be a regular deployment, instead of a redestributed deployment.
    final var secondPartitionLeader = clusteringRule.getLeaderForPartition(2).getNodeId();
    final var adminServiceLeaderTwo =
        clusteringRule.getBroker(secondPartitionLeader).getBrokerContext().getBrokerAdminService();
    adminServiceLeaderTwo.pauseStreamProcessing();

    final DeploymentEvent deploymentEvent =
        clientRule
            .getClient()
            .newDeployResourceCommand()
            .addProcessModel(PROCESS, "process.bpmn")
            .send()
            .join();
    final var deploymentKey = deploymentEvent.getKey();

    clusteringRule.stopBroker(deploymentPartitionLeader);
    adminServiceLeaderTwo.resumeStreamProcessing();

    // when
    clusteringRule.restartBroker(deploymentPartitionLeader);
    clusteringRule.getClock().addTime(DEPLOYMENT_REDISTRIBUTION_INTERVAL);

    // then
    clientRule.waitUntilDeploymentIsDone(deploymentKey);

    assertThat(
            RecordingExporter.commandDistributionRecords(CommandDistributionIntent.ACKNOWLEDGED)
                .withRecordKey(deploymentKey)
                .withDistributionPartitionId(2)
                .findFirst())
        .isPresent();
  }

  /**
   * Regression test against https://github.com/camunda/camunda/issues/9877
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

    RecordingExporter.await(Duration.ofMinutes(1))
        .until(
            () ->
                RecordingExporter.deploymentRecords(DeploymentIntent.CREATE)
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
    RecordingExporter.await(Duration.ofMinutes(1))
        .untilAsserted(
            () ->
                assertThat(
                        RecordingExporter.deploymentRecords(DeploymentIntent.CREATED)
                            .withPartitionId(2)
                            .limit(1))
                    .describedAs("expect that deployment is distributed")
                    .isNotEmpty());

    RecordingExporter.await(Duration.ofMinutes(1))
        .untilAsserted(
            () ->
                assertThat(
                        RecordingExporter.records()
                            .onlyCommandRejections()
                            .withPartitionId(2)
                            .limit(1))
                    .describedAs("expect that retried deployment is rejected")
                    .isNotEmpty());
  }
}
