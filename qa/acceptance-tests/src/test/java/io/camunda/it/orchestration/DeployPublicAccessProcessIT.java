/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.orchestration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import org.junit.jupiter.api.Test;

@MultiDbTest
class DeployPublicAccessProcessIT {

  private static CamundaClient camundaClient;

  @Test
  void shouldRejectDeploymentOfProcessWithPublicAccessForm() {
    // given / when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newDeployResourceCommand()
                    .addResourceFromClasspath("process/process_with_public_access_form.bpmn")
                    .execute())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Start event forms with public access enabled are not supported");
  }

  @Test
  void shouldRejectDeploymentOfProcessWithPublicAccessFormBuiltWithFormId() {
    // given
    final var processDefinition =
        Bpmn.createExecutableProcess("process_public_access_form_id")
            .startEvent()
            .zeebeFormId("test-form-id")
            .zeebeProperty("publicAccess", "true")
            .endEvent()
            .done();

    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newDeployResourceCommand()
                    .addProcessModel(processDefinition, "process.bpmn")
                    .execute())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Start event forms with public access enabled are not supported");
  }

  @Test
  void shouldRejectDeploymentOfProcessWithPublicAccessFormBuiltWithFormKey() {
    // given
    final var processDefinition =
        Bpmn.createExecutableProcess("process_public_access_form_key")
            .startEvent()
            .zeebeFormKey("test-form-key")
            .zeebeProperty("publicAccess", "true")
            .endEvent()
            .done();

    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newDeployResourceCommand()
                    .addProcessModel(processDefinition, "process.bpmn")
                    .execute())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Start event forms with public access enabled are not supported");
  }

  @Test
  void shouldAcceptDeploymentOfProcessWithPublicAccessSetToFalse() {
    // given
    final var processDefinition =
        Bpmn.createExecutableProcess("process_public_access_false")
            .startEvent()
            .zeebeFormId("test-form-id")
            .zeebeProperty("publicAccess", "false")
            .endEvent()
            .done();

    // when / then - should not throw
    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(processDefinition, "process.bpmn")
        .execute();
  }

  @Test
  void shouldAcceptDeploymentOfProcessWithoutPublicAccessProperty() {
    // given
    final var processDefinition =
        Bpmn.createExecutableProcess("process_no_public_access")
            .startEvent()
            .zeebeFormId("test-form-id")
            .endEvent()
            .done();

    // when / then - should not throw
    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(processDefinition, "process.bpmn")
        .execute();
  }
}
