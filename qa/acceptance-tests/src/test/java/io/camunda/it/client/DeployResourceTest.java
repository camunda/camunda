/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.Process;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.Test;

@MultiDbTest
public final class DeployResourceTest {

  private static CamundaClient client;

  @Test
  public void shouldDeployMultipleProcessModels() {
    // given
    final String processId = "process";
    final String resourceName = processId + ".bpmn";

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("test"))
            .endEvent()
            .done();

    // when
    final DeploymentEvent result =
        client
            .newDeployResourceCommand()
            .addProcessModel(process, resourceName)
            .addProcessModel(process, resourceName)
            .addProcessModel(process, resourceName)
            .addProcessModel(process, resourceName)
            .addProcessModel(process, resourceName)
            .addProcessModel(process, resourceName)
            .addProcessModel(process, resourceName)
            .addProcessModel(process, resourceName)
            .addProcessModel(process, resourceName)
            .addProcessModel(process, resourceName)
            .addProcessModel(process, resourceName)
            .send()
            .join();

    // then
    assertThat(result.getKey()).isGreaterThan(0);
    assertThat(result.getProcesses()).hasSize(1);

    final Process deployedProcess = result.getProcesses().getFirst();
    assertThat(deployedProcess.getBpmnProcessId()).isEqualTo(processId);
    assertThat(deployedProcess.getVersion()).isEqualTo(1);
    assertThat(deployedProcess.getProcessDefinitionKey()).isGreaterThan(0);
    assertThat(deployedProcess.getResourceName()).isEqualTo(resourceName);
  }
}
