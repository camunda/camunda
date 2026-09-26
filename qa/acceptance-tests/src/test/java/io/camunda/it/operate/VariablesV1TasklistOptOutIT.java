/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.operate;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitUntilProcessInstanceHasVariable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.test.util.Strings;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.http.HttpStatus;

/**
 * Verifies that {@code camunda.tasklist.v1-variable-by-id-enabled=false} resolves the {@code GET
 * /v1/variables/{id}} ambiguous-mapping collision (camunda#59478) in favor of Operate's V1
 * controller, and that the extracted {@code VariableByKeyController} preserves the documented error
 * contract for a missing key.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class VariablesV1TasklistOptOutIT {

  @MultiDbTestApplication
  static final TestCamundaApplication STANDALONE_CAMUNDA =
      new TestCamundaApplication()
          .withProperty("camunda.tasklist.v1-variable-by-id-enabled", "false");

  private static final String PROCESS_ID = Strings.newRandomValidBpmnId();
  private static final String VARIABLE_NAME = "testVariableA";
  private static final int VARIABLE_VALUE = 42;

  private static long processInstanceKey;
  private static long variableKey;

  @BeforeAll
  public static void beforeAll(final CamundaClient adminClient) {
    deployProcessAndWaitForIt(
        adminClient,
        Bpmn.createExecutableProcess(PROCESS_ID).startEvent().userTask().endEvent().done(),
        "process.bpmn");
    processInstanceKey =
        startProcessInstance(adminClient, PROCESS_ID, Map.of(VARIABLE_NAME, VARIABLE_VALUE))
            .getProcessInstanceKey();
    waitUntilProcessInstanceHasVariable(
        adminClient, processInstanceKey, VARIABLE_NAME, String.valueOf(VARIABLE_VALUE));
    await()
        .untilAsserted(
            () ->
                assertThat(
                        adminClient
                            .newVariableSearchRequest()
                            .filter(f -> f.processInstanceKey(processInstanceKey))
                            .send()
                            .join()
                            .items())
                    .hasSize(1));
    variableKey =
        adminClient
            .newVariableSearchRequest()
            .filter(f -> f.processInstanceKey(processInstanceKey))
            .send()
            .join()
            .items()
            .getFirst()
            .getVariableKey();
  }

  @Test
  void shouldReturnOperateVariableWhenTasklistOptsOut() throws Exception {
    // when
    final HttpResponse<String> response;
    try (final var client = STANDALONE_CAMUNDA.newOperateClient()) {
      response = client.sendGetRequest("v1/variables/%s", variableKey);
    }

    // then
    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
    assertThat(response.body()).contains("\"key\":" + variableKey);
    assertThat(response.body()).contains(VARIABLE_NAME);
    assertThat(response.body()).contains("\"value\":\"" + VARIABLE_VALUE + "\"");
  }

  @Test
  void shouldReturn404WithErrorContractForMissingKey() throws Exception {
    // given
    final long missingKey = 9_999_999_999L;

    // when
    final HttpResponse<String> response;
    try (final var client = STANDALONE_CAMUNDA.newOperateClient()) {
      response = client.sendGetRequest("v1/variables/%s", missingKey);
    }

    // then
    assertThat(response.statusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
    assertThat(response.body()).contains("\"status\":404");
    assertThat(response.body()).contains("\"type\"");
    assertThat(response.body()).contains("\"instance\"");
    assertThat(response.body()).contains("\"message\"");
  }
}
