/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering;

import io.camunda.zeebe.client.api.response.BrokerInfo;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CompleteProcessInstanceAfterLeaderChangeTest {

  public final Timeout testTimeout = Timeout.seconds(120);

  public final ClusteringRule clusteringRule = new ClusteringRule(1, 3, 3);
  public final GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(testTimeout).around(clusteringRule).around(clientRule);

  @Parameter public String name;

  @Parameter(1)
  public Consumer<GrpcClientRule> beforeRestart;

  @Parameter(2)
  public BiConsumer<ClusteringRule, GrpcClientRule> afterRestart;

  @Parameters(name = "{index}: {0}")
  public static Object[][] actions() {
    return new Object[][] {
      new Object[] {
        "create instance after restart",
        (Consumer<GrpcClientRule>)
            (clientRule) ->
                clientRule.deployProcess(
                    Bpmn.createExecutableProcess("process").startEvent().endEvent().done()),
        (BiConsumer<ClusteringRule, GrpcClientRule>)
            (clusteringRule, clientRule) ->
                clientRule
                    .getClient()
                    .newCreateInstanceCommand()
                    .bpmnProcessId("process")
                    .latestVersion()
                    .withResult()
                    .send()
                    .join(),
      },
      new Object[] {
        "correlate message after restart",
        (Consumer<GrpcClientRule>)
            (clientRule) ->
                clientRule
                    .getClient()
                    .newPublishMessageCommand()
                    .messageName("msg")
                    .correlationKey("123")
                    .send(),
        (BiConsumer<ClusteringRule, GrpcClientRule>)
            (clusteringRule, clientRule) -> {
              final var processDefinitionKey =
                  clientRule.deployProcess(
                      Bpmn.createExecutableProcess("process")
                          .startEvent()
                          .intermediateCatchEvent()
                          .message(msg -> msg.name("msg").zeebeCorrelationKey("=key"))
                          .endEvent()
                          .done());
              clientRule
                  .getClient()
                  .newCreateInstanceCommand()
                  .processDefinitionKey(processDefinitionKey)
                  .variables(Map.of("key", "123"))
                  .withResult()
                  .send()
                  .join();
            },
      },
      new Object[] {
        "trigger timer after restart",
        (Consumer<GrpcClientRule>)
            (clientRule) -> {
              final var processDefinitionKey =
                  clientRule.deployProcess(
                      Bpmn.createExecutableProcess("process")
                          .startEvent()
                          .intermediateCatchEvent()
                          .timerWithDuration("PT15S")
                          .endEvent()
                          .done());
              clientRule.createProcessInstance(processDefinitionKey);
            },
        (BiConsumer<ClusteringRule, GrpcClientRule>)
            (clusteringRule, clientRule) ->
                Awaitility.await("timer should trigger and complete instance")
                    .untilAsserted(
                        () -> {
                          clusteringRule.getClock().addTime(Duration.ofSeconds(15));
                          ZeebeAssertHelper.assertProcessInstanceCompleted("process");
                        })
      },
      new Object[] {
        "complete job after restart",
        (Consumer<GrpcClientRule>) (clientRule) -> clientRule.createSingleJob("testTask"),
        (BiConsumer<ClusteringRule, GrpcClientRule>)
            (clusteringRule, clientRule) ->
                Awaitility.await("timer should trigger and complete instance")
                    .untilAsserted(
                        () -> {
                          final var jobKey =
                              RecordingExporter.jobRecords(JobIntent.CREATED)
                                  .withType("testTask")
                                  .getFirst()
                                  .getKey();

                          clientRule.getClient().newCompleteCommand(jobKey).send().join();
                          ZeebeAssertHelper.assertJobCompleted();
                        })
      }
    };
  }

  @Test
  public void shouldCompleteInstanceAfterLeaderChange() {
    // given
    final BrokerInfo leaderForPartition = clusteringRule.getLeaderForPartition(1);
    beforeRestart.accept(clientRule);

    // when
    clusteringRule.stopBrokerAndAwaitNewLeader(leaderForPartition.getNodeId());

    // then
    afterRestart.accept(clusteringRule, clientRule);
    ZeebeAssertHelper.assertProcessInstanceCompleted("process");
  }

  @Test
  public void shouldCompleteProcessInstanceAfterSeveralLeaderChanges() {
    // given
    var leaderForPartition = clusteringRule.getLeaderForPartition(1);
    beforeRestart.accept(clientRule);

    clusteringRule.stopBrokerAndAwaitNewLeader(leaderForPartition.getNodeId());
    clusteringRule.startBroker(leaderForPartition.getNodeId());
    leaderForPartition = clusteringRule.getLeaderForPartition(1);

    // when
    clusteringRule.stopBrokerAndAwaitNewLeader(leaderForPartition.getNodeId());

    // then
    afterRestart.accept(clusteringRule, clientRule);
    ZeebeAssertHelper.assertProcessInstanceCompleted("process");
  }

  @Test
  public void shouldCompleteProcessInstanceAfterLeaderChangeWithSnapshot() {
    // given
    final BrokerInfo leaderForPartition = clusteringRule.getLeaderForPartition(1);
    beforeRestart.accept(clientRule);
    clusteringRule.triggerAndWaitForSnapshots();

    // when
    clusteringRule.stopBrokerAndAwaitNewLeader(leaderForPartition.getNodeId());

    // then
    afterRestart.accept(clusteringRule, clientRule);
    ZeebeAssertHelper.assertProcessInstanceCompleted("process");
  }
}
