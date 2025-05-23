/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.api;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.impl.containers.CamundaContainer;
import io.camunda.process.test.impl.containers.ContainerFactory;
import io.camunda.process.test.impl.runtime.ContainerRuntimeDefaults;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// We use Testcontainers to start a "remote" Camunda container that is not managed by the Camunda
// process test extension.
@Testcontainers
public class RemoteCamundaProcessTestExtensionIT {

  // 1: Start the Camunda container
  @Order(0)
  @Container
  private static final CamundaContainer REMOTE_CAMUNDA_CONTAINER =
      new ContainerFactory()
          .createCamundaContainer(
              ContainerRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_NAME,
              ContainerRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_VERSION);

  // 2: Bind the extension to the Camunda container
  @Order(1)
  @RegisterExtension
  private static final BindCamundaProcessTestExtension BIND_EXTENSION_TO_REMOTE =
      new BindCamundaProcessTestExtension();

  // 3: Start the extension and connect to the Camunda container
  @Order(2)
  @RegisterExtension
  private static CamundaProcessTestExtension EXTENSION =
      new CamundaProcessTestExtension().withRuntimeMode(CamundaRuntimeMode.REMOTE);

  // to be injected
  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;

  @Test
  void shouldCreateProcessInstance() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask("task", t -> t.name("Task").zeebeJobType("task"))
            .endEvent("end")
            .done();

    client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();

    processTestContext.mockJobWorker("task").thenComplete();

    // when
    final ProcessInstanceEvent processInstance =
        client.newCreateInstanceCommand().bpmnProcessId("process").latestVersion().send().join();

    // then
    CamundaAssert.assertThat(processInstance)
        .isCompleted()
        .hasCompletedElementsInOrder("start", "task", "end");
  }

  private static class BindCamundaProcessTestExtension implements BeforeAllCallback {

    @Override
    public void beforeAll(final ExtensionContext context) {
      EXTENSION
          .withRemoteCamundaClientBuilderFactory(
              () ->
                  CamundaClient.newClientBuilder()
                      .usePlaintext()
                      .restAddress(REMOTE_CAMUNDA_CONTAINER.getRestApiAddress())
                      .grpcAddress(REMOTE_CAMUNDA_CONTAINER.getGrpcApiAddress()))
          .withRemoteCamundaMonitoringApiAddress(
              REMOTE_CAMUNDA_CONTAINER.getMonitoringApiAddress().toString());
    }
  }
}
