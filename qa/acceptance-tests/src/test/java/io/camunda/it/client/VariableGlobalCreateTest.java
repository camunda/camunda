/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.Variable;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.io.InputStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
class VariableGlobalCreateTest {

  private static CamundaClient camundaClient;

  private static Variable variable;

  @BeforeAll
  static void beforeAll() {
    deployProcessFromResourcePath("/process/bpm_variable_test.bpmn", "bpm_variable_test.bpmn");

    startProcessInstance("bpmProcessVariable");

    waitForTasksBeingExported();
  }

  @Test
  void shouldCreateGlobalVariables() {
    final var result =
        camundaClient.newGlobalVariableCreationRequest().variable("MY_VAR", "Hello").send().join();
  }

  private static void deployProcessFromResourcePath(
      final String resource, final String resourceName) {
    final InputStream process = UserTaskSearchTest.class.getResourceAsStream(resource);

    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(Bpmn.readModelFromStream(process), resourceName)
        .send()
        .join();
  }

  private static void waitForTasksBeingExported() {
    Awaitility.await("should receive data from ES")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = camundaClient.newUserTaskSearchRequest().send().join();
              assertThat(result.items().size()).isEqualTo(2);

              final var resultVariable = camundaClient.newVariableSearchRequest().send().join();
              assertThat(resultVariable.items().size()).isEqualTo(5);
            });
  }

  private static void startProcessInstance(final String processId) {
    camundaClient.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();
  }
}
