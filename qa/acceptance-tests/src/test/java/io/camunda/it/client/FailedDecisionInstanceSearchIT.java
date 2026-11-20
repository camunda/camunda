/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.DecisionInstanceState;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

@MultiDbTest
class FailedDecisionInstanceSearchIT {

  private static CamundaClient camundaClient;

  @Test
  void shouldSearchFailedDecisionInstance() {
    // given - the decision fails because the input variables are missing
    deployResource(camundaClient, "decisions/drg-force-user-with-assertions.dmn");
    deployResource(
        camundaClient,
        Bpmn.createExecutableProcess("failingDecisionProcess")
            .startEvent()
            .businessRuleTask(
                "failingTask",
                b -> b.zeebeCalledDecisionId("force_user").zeebeResultVariable("result"))
            .endEvent()
            .done(),
        "failingDecisionProcess.bpmn");

    // when
    final long processInstanceKey =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId("failingDecisionProcess")
            .latestVersion()
            .execute()
            .getProcessInstanceKey();

    // then
    Awaitility.await("should export the failed decision instance")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var incidents =
                  camundaClient
                      .newIncidentSearchRequest()
                      .filter(f -> f.processInstanceKey(processInstanceKey))
                      .send()
                      .join();
              assertThat(incidents.items()).hasSize(1);

              final var failed =
                  camundaClient
                      .newDecisionInstanceSearchRequest()
                      .filter(
                          f ->
                              f.processInstanceKey(processInstanceKey)
                                  .state(DecisionInstanceState.FAILED))
                      .send()
                      .join();
              assertThat(failed.items()).hasSize(1);
            });
  }
}
