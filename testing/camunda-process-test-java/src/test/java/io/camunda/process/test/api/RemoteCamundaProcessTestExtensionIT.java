/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.api;

import static io.camunda.process.test.api.assertions.ElementSelectors.byName;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.impl.containers.CamundaContainer;
import io.camunda.process.test.impl.containers.ContainerFactory;
import io.camunda.process.test.impl.runtime.ContainerRuntimeDefaults;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class RemoteCamundaProcessTestExtensionIT {

  // start Camunda in Testcontainer

  @Order(0)
  @Container
  private static final CamundaContainer camundaContainer =
      new ContainerFactory()
          .createCamundaContainer(
              ContainerRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_NAME,
              ContainerRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_VERSION);

  // bind extension to "remote" Camunda
  @Order(1)
  @RegisterExtension
  private static CamundaProcessTestExtension EXTENSION =
      new CamundaProcessTestExtension()
          .withRuntimeMode(CamundaRuntimeMode.REMOTE)
          .withCamundaClient(
              () ->
                  CamundaClient.newClientBuilder()
                      .usePlaintext()
                      .restAddress(camundaContainer.getRestApiAddress())
                      .grpcAddress(camundaContainer.getGrpcApiAddress()))
      //          .withRemoteCamundaMonitoringApiAddress(
      //              camundaContainer.getMonitoringApiAddress().toString())
      //          .withCamundaClient(
      //              () ->
      //                  CamundaClient.newClientBuilder()
      //                      .usePlaintext()
      //                      .restAddress(URI.create("http://localhost:8080"))
      //                      .grpcAddress(URI.create("http://localhost:26500")))
      //          .withRemoteCamundaMonitoringApiAddress("http://localhost:9600")
      ;

  // to be injected
  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;

  @Test
  void shouldCreateProcessInstance() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("test-process")
            .startEvent()
            .name("start")
            .zeebeOutputExpression("\"active\"", "status")
            .userTask()
            .name("task")
            .endEvent()
            .name("end")
            .zeebeOutputExpression("\"ok\"", "result")
            .done();

    client.newDeployResourceCommand().addProcessModel(process, "test-process.bpmn").send().join();

    // when
    final ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("test-process")
            .latestVersion()
            .send()
            .join();

    // then
    CamundaAssert.assertThat(processInstance)
        .isActive()
        .hasActiveElements(byName("task"))
        .hasVariable("status", "active");
  }
}
