/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it;

import static io.camunda.it.util.TestHelper.waitForProcessInstance;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.model.bpmn.Bpmn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms.*$")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class StandaloneCamundaTest {

  @MultiDbTestApplication
  private static final TestCamundaApplication CAMUNDA_APPLICATION =
      new TestCamundaApplication()
          .withAuthenticationMethod(AuthenticationMethod.BASIC)
          .withUnauthenticatedAccess();

  private static CamundaClient camundaClient;

  @Test
  public void shouldCreateAndRetrieveInstance() {
    // given

    // when
    camundaClient
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
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

    // then
    waitForProcessInstance(
        camundaClient,
        f -> f.processInstanceKey(processInstanceEvent.getProcessInstanceKey()),
        f -> assertThat(f).hasSize(1));

    final ProcessInstance processInstance =
        camundaClient
            .newProcessInstanceGetRequest(processInstanceEvent.getProcessInstanceKey())
            .send()
            .join();

    assertThat(processInstance.getProcessInstanceKey())
        .withFailMessage("Expect to read the expected process instance from ES")
        .isEqualTo(processInstanceEvent.getProcessInstanceKey());
  }
}
