/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.startup;

import io.camunda.zeebe.broker.TestLoggers;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.it.clustering.ClusteringRule;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.Protocol;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;

@RunWith(Parameterized.class)
public final class MultiPartitionRecoveryTest {

  private static final Logger LOG = TestLoggers.TEST_LOGGER;

  private static final String PROCESS_ID = "process";

  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .serviceTask("task", t -> t.zeebeJobType("foo"))
          .endEvent("end")
          .done();

  @Parameter(0)
  public Consumer<MultiPartitionRecoveryTest> reprocessingTrigger;

  @Parameter(1)
  public String name;

  public final ClusteringRule clusteringRule = new ClusteringRule();
  public final GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);
  @Rule public RuleChain ruleChain = RuleChain.outerRule(clusteringRule).around(clientRule);
  @Rule public ExpectedException exception = ExpectedException.none();
  @Rule public Timeout timeout = new Timeout(120, TimeUnit.SECONDS);

  @Parameters(name = "{index}: {1}")
  public static Object[][] restartAction() {
    return new Object[][] {
      new Object[] {
        (Consumer<MultiPartitionRecoveryTest>) (test -> test.restartLeader(1)), "restart one"
      },
      new Object[] {
        (Consumer<MultiPartitionRecoveryTest>) (test -> test.restartLeader(2)), "restart two"
      },
      new Object[] {
        (Consumer<MultiPartitionRecoveryTest>) (test -> test.restartLeader(3)), "restart three"
      }
    };
  }

  @Test
  public void shouldDistributeDeploymentAfterRestart() {
    // given
    final DeploymentEvent deploymentEvent =
        clientRule
            .getClient()
            .newDeployCommand()
            .addProcessModel(PROCESS, "process.bpmn")
            .send()
            .join();

    // when
    reprocessingTrigger.accept(this);

    // then
    clientRule.waitUntilDeploymentIsDone(deploymentEvent.getKey());
  }

  @Test
  public void shouldCreateProcessInstanceOnAllPartitionsAfterRestart() {
    // given
    deploy(PROCESS, "process.bpmn");

    // when
    reprocessingTrigger.accept(this);

    // then
    final var partitionIds = clusteringRule.getPartitionIds();

    Awaitility.await("createInstancesOnAllPartitions")
        .until(
            () -> {
              final var processInstanceEvent =
                  clientRule
                      .getClient()
                      .newCreateInstanceCommand()
                      .bpmnProcessId(PROCESS_ID)
                      .latestVersion()
                      .send()
                      .join();

              final var processInstanceKey = processInstanceEvent.getProcessInstanceKey();
              final var partitionId = Protocol.decodePartitionId(processInstanceKey);
              partitionIds.remove(Integer.valueOf(partitionId));
              return partitionIds;
            },
            List::isEmpty);
  }

  protected void restartLeader(final int partitionId) {
    final var nodeId = clusteringRule.getLeaderForPartition(partitionId).getNodeId();
    clusteringRule.restartBroker(nodeId);
    LOG.info("Restarted broker {} which was the leader of partition {}", nodeId, partitionId);
  }

  private void deploy(final BpmnModelInstance process, final String s) {
    final DeploymentEvent deploymentEvent =
        clientRule.getClient().newDeployCommand().addProcessModel(process, s).send().join();

    clientRule.waitUntilDeploymentIsDone(deploymentEvent.getKey());
  }
}
