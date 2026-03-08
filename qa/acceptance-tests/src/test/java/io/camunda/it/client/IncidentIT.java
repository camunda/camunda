/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.it.util.TestHelper.waitUntilIncidentsAreResolved;
import static io.camunda.it.util.TestHelper.waitUntilIncidentsConditionsAreMet;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.util.List;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class IncidentIT {

  private static CamundaClient camundaClient;

  @BeforeAll
  static void beforeAll() {
    Objects.requireNonNull(camundaClient);
    deployResource(camundaClient, "process/incident_process_v1.bpmn");
    waitForProcessesToBeDeployed(camundaClient, 1);
  }

  @Test
  void shouldKeepErrorMessageWhenResolvingIncident() {
    // given
    final var processInstanceKey =
        startProcessInstance(camundaClient, "incident_process_v1").getProcessInstanceKey();
    waitForProcessInstancesToStart(camundaClient, 1);
    waitUntilIncidentsConditionsAreMet(
        camundaClient,
        f -> f.state(IncidentState.ACTIVE).processInstanceKey(processInstanceKey),
        1,
        "should wait until incident for process instance is active");
    final var activeIncident =
        camundaClient
            .newIncidentSearchRequest()
            .filter(f -> f.state(IncidentState.ACTIVE).processInstanceKey(processInstanceKey))
            .page(p -> p.limit(1))
            .send()
            .join()
            .singleItem();

    // when
    Awaitility.await("Incident should be resolvable")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () ->
                camundaClient
                    .newResolveIncidentCommand(activeIncident.getIncidentKey())
                    .send()
                    .join());
    waitUntilIncidentsAreResolved(camundaClient, List.of(activeIncident.getIncidentKey()));
    final var resolvedIncident =
        camundaClient.newIncidentGetRequest(activeIncident.getIncidentKey()).send().join();

    // then
    assertThat(activeIncident.getErrorMessage()).isNotBlank();
    assertThat(resolvedIncident.getState()).isEqualTo(IncidentState.RESOLVED);
    assertThat(resolvedIncident.getErrorMessage()).isEqualTo(activeIncident.getErrorMessage());
  }
}
