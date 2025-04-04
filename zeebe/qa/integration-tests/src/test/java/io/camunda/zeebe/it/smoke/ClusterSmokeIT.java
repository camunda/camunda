/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceResult;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;

@ZeebeIntegration
final class ClusterSmokeIT {

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(3)
          .withReplicationFactor(3)
          .withGatewaysCount(1)
          .build();

  @AutoClose private CamundaClient client;

  @BeforeEach
  void beforeEach() {
    client = cluster.newClientBuilder().build();
  }

  /**
   * A simple smoke test to ensure the cluster can perform basic functionality:
   *
   * <ul>
   *   <li>Members can find each other to form a complete and healthy topology
   *   <li>The gateway can find the external brokers and route requests/commands
   *   <li>The gateway can route actuator/management requests
   * </ul>
   */
  @SmokeTest
  void smokeTest() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var process = Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();

    // when
    final var result = executeProcessInstance(processId, process);

    // then
    assertThat(result.getBpmnProcessId()).isEqualTo(processId);
    Awaitility.await("until client topology view is healthy")
        .untilAsserted(
            () ->
                TopologyAssert.assertThat(client.newTopologyRequest().send().join())
                    .isComplete(3, 1, 3));
    Awaitility.await("until cluster topology is active")
        .untilAsserted(
            () ->
                ClusterActuatorAssert.assertThat(cluster)
                    .hasActiveBroker(0)
                    .hasActiveBroker(1)
                    .hasActiveBroker(2));
  }

  private ProcessInstanceResult executeProcessInstance(
      final String processId, final BpmnModelInstance process) {
    client.newDeployResourceCommand().addProcessModel(process, processId + ".bpmn").send().join();
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .withResult()
        .send()
        .join();
  }
}
