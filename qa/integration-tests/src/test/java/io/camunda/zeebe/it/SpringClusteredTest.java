/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.qa.util.cluster.ManageTestCluster;
import io.camunda.zeebe.qa.util.cluster.ManageTestCluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.spring.TestSpringCluster;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ManageTestCluster
@AutoCloseResources
final class SpringClusteredTest {
  @TestCluster
  private final TestSpringCluster cluster =
      TestSpringCluster.builder()
          .withBrokersCount(3)
          .withPartitionsCount(1)
          .withReplicationFactor(3)
          .withEmbeddedGateway(true)
          .build();

  @AutoCloseResource private ZeebeClient client;

  @BeforeEach
  void beforeEach() {
    client = cluster.newClientBuilder().build();
  }

  @Test
  void shouldGetTopology() {
    // when
    final var topology = client.newTopologyRequest().send().join();

    // then
    TopologyAssert.assertThat(topology).isComplete(3, 1, 3);
  }

  @Test
  void shouldDeployProcess() {
    // given
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("job"))
            .endEvent()
            .done();

    // when
    final var processDefinitionKey =
        client
            .newDeployResourceCommand()
            .addProcessModel(process, "process.bpmn")
            .send()
            .join()
            .getProcesses()
            .get(0)
            .getProcessDefinitionKey();
    final var processInstance =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();
    client
        .newActivateJobsCommand()
        .jobType("job")
        .maxJobsToActivate(10)
        .send()
        .join()
        .getJobs()
        .forEach(job -> client.newCompleteCommand(job).send().join());

    // then - assert process is finished
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstance.getProcessInstanceKey())
                .limit(1))
        .hasSize(1);
  }
}
