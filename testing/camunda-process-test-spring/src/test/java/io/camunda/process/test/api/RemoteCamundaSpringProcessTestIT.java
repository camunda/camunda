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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
    classes = {RemoteCamundaSpringProcessTestIT.class},
    properties = {"io.camunda.process.test.runtime-mode=remote"})
@CamundaSpringProcessTest
public class RemoteCamundaSpringProcessTestIT {

  @Order(0)
  @Container
  private static final CamundaContainer REMOTE_CAMUNDA_CONTAINER =
      new ContainerFactory()
          .createCamundaContainer(
              ContainerRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_NAME,
              ContainerRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_VERSION);

  @Autowired private CamundaClient client;
  @Autowired private CamundaProcessTestContext processTestContext;

  @DynamicPropertySource
  static void registerTestProperties(final DynamicPropertyRegistry registry) {
    registry.add(
        "io.camunda.process.test.remote.client.restAddress",
        REMOTE_CAMUNDA_CONTAINER::getRestApiAddress);
    registry.add(
        "io.camunda.process.test.remote.client.grpcAddress",
        REMOTE_CAMUNDA_CONTAINER::getGrpcApiAddress);
    registry.add(
        "io.camunda.process.test.remote.camunda-monitoring-api-address",
        REMOTE_CAMUNDA_CONTAINER::getMonitoringApiAddress);
  }

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
}
