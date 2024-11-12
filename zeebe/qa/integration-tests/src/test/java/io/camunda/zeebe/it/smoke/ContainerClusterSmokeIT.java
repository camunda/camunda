/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.zeebe.containers.cluster.ZeebeCluster;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
final class ContainerClusterSmokeIT {

  @Container
  private final ZeebeCluster cluster =
      ZeebeCluster.builder()
          .withBrokersCount(1)
          .withGatewaysCount(1)
          .withPartitionsCount(1)
          .withEmbeddedGateway(false)
          .withImage(ZeebeTestContainerDefaults.defaultTestImage())
          .build();

  /** A smoke test which checks that a gateway of a cluster can be accessed. */
  @ContainerSmokeTest
  void connectSmokeTest() {
    // given
    try (final var client = createZeebeClient()) {
      // when
      final var topology = client.newTopologyRequest().send();

      // then
      final var result = topology.join(5L, TimeUnit.SECONDS);
      assertThat(result.getBrokers()).as("There is one connected broker").hasSize(1);
    }
  }

  @ContainerSmokeTest
  void deployModelAndStartInstance() {
    // given
    final BpmnModelInstance processModel =
        Bpmn.createExecutableProcess("smoke").startEvent().endEvent().done();
    try (final var client = createZeebeClient()) {
      // when
      final DeploymentEvent deploymentEvent =
          client
              .newDeployResourceCommand()
              .addProcessModel(processModel, "smoke.bpmn")
              .send()
              .join();
      final ProcessInstanceResult processInstanceResult =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId("smoke")
              .latestVersion()
              .withResult()
              .send()
              .join();

      // then
      assertThat(processInstanceResult.getProcessDefinitionKey())
          .isEqualTo(deploymentEvent.getProcesses().get(0).getProcessDefinitionKey());
    }
  }

  private ZeebeClient createZeebeClient() {
    // increased request timeout as container tests might be less responsive when emulation is
    // involved e.g. emulation of ARM64
    return cluster.newClientBuilder().defaultRequestTimeout(Duration.ofMinutes(1)).build();
  }
}
