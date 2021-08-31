/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering;

import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class InstallRequestHandlingTest {
  private static final Duration SNAPSHOT_PERIOD = Duration.ofMinutes(5);

  public final Timeout testTimeout = Timeout.seconds(120);
  public final ClusteringRule clusteringRule =
      new ClusteringRule(
          1,
          3,
          3,
          config -> {
            config.getData().setSnapshotPeriod(SNAPSHOT_PERIOD);
          });
  public final GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(testTimeout).around(clusteringRule).around(clientRule);

  @Test
  public void shouldBeAbleToBecomeLeader() {
    // given
    final var followerId = clusteringRule.stopAnyFollower();
    final var jobKey = clientRule.createSingleJob("type");

    clusteringRule.triggerAndWaitForSnapshots();

    // when
    clusteringRule.startBroker(followerId);

    // then
    clusteringRule.waitForSnapshotAtBroker(followerId);
    clusteringRule.forceClusterToHaveNewLeader(followerId);

    clientRule.getClient().newCompleteCommand(jobKey).send().join();
    ZeebeAssertHelper.assertProcessInstanceCompleted("process");
  }

  @Test
  public void shouldContinueProcessingAfterReplay() {
    // given
    final var followerId = clusteringRule.stopAnyFollower();

    final var processDefinitionKey =
        clientRule.deployProcess(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("task", task -> task.zeebeJobType("type"))
                .endEvent()
                .done());
    clusteringRule.triggerAndWaitForSnapshots();
    clusteringRule.startBroker(followerId);
    clusteringRule.waitForSnapshotAtBroker(followerId);

    // when
    clientRule.createProcessInstance(processDefinitionKey);

    // then
    clusteringRule.forceClusterToHaveNewLeader(followerId);

    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withType("type")
            .withElementId("task")
            .getFirst()
            .getKey();
    clientRule.getClient().newCompleteCommand(jobKey).send().join();
    ZeebeAssertHelper.assertProcessInstanceCompleted("process");
  }
}
