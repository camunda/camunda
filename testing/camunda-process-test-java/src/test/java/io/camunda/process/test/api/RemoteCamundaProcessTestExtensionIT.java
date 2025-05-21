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
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class RemoteCamundaProcessTestExtensionIT {

  // start Camunda in Testcontainer

  // bind extension to "remote" Camunda
  @RegisterExtension
  private static CamundaProcessTestExtension EXTENSION =
      new CamundaProcessTestExtension()
          .withRuntimeMode(CamundaRuntimeMode.REMOTE)
          .withCamundaClient(
              () ->
                  CamundaClient.newClientBuilder()
                      .usePlaintext()
                      .restAddress(URI.create("http://localhost:8080"))
                      .grpcAddress(URI.create("http://localhost:26500")))
      // .withRemoteCamundaMonitoringApiAddress("http://localhost:9600")
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
