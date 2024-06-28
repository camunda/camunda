/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.cluster;


import static org.assertj.core.api.Assertions.assertThat;


import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class StandaloneCamundaTest {

  @TestZeebe final TestStandaloneCamunda testStandaloneCamunda = new TestStandaloneCamunda();

  @Test
  public void shouldCreateAndRetrieveInstance() {
    // givne
    final var zeebeClient = testStandaloneCamunda.newClientBuilder().build();

    // when
    zeebeClient
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("test")
                .zeebeJobType("type")
                .endEvent()
                .done(),
            "simple.bpmn")
        .send()
        .join();

    final var processInstanceEvent =
        zeebeClient
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();


  }
}
