/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.io.InputStream;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
public class VariableNullValueSearchIT {

  private static CamundaClient camundaClient;

  @BeforeAll
  static void beforeAll() {
    final InputStream process =
        VariableNullValueSearchIT.class.getResourceAsStream("/process/bpm_variable_test.bpmn");
    deployProcessAndWaitForIt(
        camundaClient, Bpmn.readModelFromStream(process), "bpm_variable_test.bpmn");
  }

  @Test
  void shouldReturnJsonNullLiteralForProcessVariableValue() {
    // given
    final var variableName = "nullVar_" + UUID.randomUUID().toString().replace("-", "");
    final var processInstance =
        startProcessInstance(
            camundaClient, "bpmProcessVariable", "{\"%s\":null}".formatted(variableName));

    // when / then
    Awaitility.await("should index null variable value")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var searchResult =
                  camundaClient
                      .newVariableSearchRequest()
                      .filter(
                          f ->
                              f.name(variableName)
                                  .processInstanceKey(processInstance.getProcessInstanceKey()))
                      .send()
                      .join();
              assertThat(searchResult.items()).hasSize(1);
              assertThat(searchResult.items().getFirst().getValue()).isEqualTo("null");
            });
  }
}
