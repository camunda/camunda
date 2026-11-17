/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Shared steps for tests that provision a physical tenant by changing every broker's static
 * configuration and restarting the cluster.
 */
final class PhysicalTenantProvisioningSupport {

  private PhysicalTenantProvisioningSupport() {}

  /** Stops every broker, applies {@code brokerConfigurator} to each, and starts them again. */
  static void restartCluster(
      final TestCluster cluster, final Consumer<TestStandaloneBroker> brokerConfigurator) {
    cluster.shutdown();
    cluster.brokers().values().forEach(brokerConfigurator);
    cluster.start().awaitCompleteTopology();
  }

  /**
   * Asserts that the physical tenant {@code client} is scoped to is usable end-to-end: a process
   * can be deployed to it and instantiated. The deployment is retried, since a newly-provisioned
   * tenant may briefly reject requests while its partitions settle.
   */
  static void assertProcessCanBeDeployedAndStarted(
      final CamundaClient client, final String processId) {
    final var process = Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();

    await("deployment of '%s' to the physical tenant succeeds".formatted(processId))
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        client
                            .newDeployResourceCommand()
                            .addProcessModel(process, processId + ".bpmn")
                            .send()
                            .join()
                            .getProcesses())
                    .isNotEmpty());

    final long processInstanceKey =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .send()
            .join()
            .getProcessInstanceKey();
    assertThat(processInstanceKey).isPositive();
  }
}
