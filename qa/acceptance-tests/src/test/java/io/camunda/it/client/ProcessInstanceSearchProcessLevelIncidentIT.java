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
import static io.camunda.it.util.TestHelper.waitUntilIncidentsAreActive;
import static io.camunda.it.util.TestHelper.waitUntilProcessInstanceHasIncidents;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Reproduces <a href="https://github.com/camunda/camunda/issues/44398">#44398</a>: searching
 * process instances by incident error message must also find process instances whose incident is
 * raised on the process instance level (e.g. a failed execution listener on the process start
 * event) rather than on a specific element.
 */
@MultiDbTest
class ProcessInstanceSearchProcessLevelIncidentIT {

  private static final String PROCESS_ID = "process_level_incident_process";
  private static final String START_EL_JOB_TYPE = "process-start-execution-listener";
  private static final String ERROR_MESSAGE = "Process-level incident error message";

  private static CamundaClient camundaClient;

  @BeforeAll
  static void beforeAll() {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .zeebeStartExecutionListener(START_EL_JOB_TYPE)
            .startEvent()
            .endEvent()
            .done();
    deployResource(camundaClient, process, PROCESS_ID + ".bpmn");
    waitForProcessesToBeDeployed(camundaClient, 1);

    startProcessInstance(camundaClient, PROCESS_ID);
    waitForProcessInstancesToStart(camundaClient, 1);

    // fail the start execution listener job to raise a process-level incident
    // (elementInstanceKey == processInstanceKey)
    final long jobKey =
        await("should activate the start execution listener job")
            .atMost(TIMEOUT_DATA_AVAILABILITY)
            .until(
                () ->
                    camundaClient
                        .newActivateJobsCommand()
                        .jobType(START_EL_JOB_TYPE)
                        .maxJobsToActivate(1)
                        .send()
                        .join()
                        .getJobs(),
                jobs -> !jobs.isEmpty())
            .getFirst()
            .getKey();
    camundaClient.newFailCommand(jobKey).retries(0).errorMessage(ERROR_MESSAGE).send().join();

    waitUntilProcessInstanceHasIncidents(camundaClient, 1);
    waitUntilIncidentsAreActive(camundaClient, 1);
    // ensure the incident is exported with the expected error message before asserting on the
    // process instance search, so a failure below cannot be an export race
    await("should export the process-level incident with the error message")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var incidents = camundaClient.newIncidentSearchRequest().send().join().items();
              assertThat(incidents).hasSize(1);
              assertThat(incidents.getFirst().getErrorMessage()).isEqualTo(ERROR_MESSAGE);
            });
  }

  @Test
  void shouldFindProcessInstanceByProcessLevelIncidentErrorMessage() {
    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(b -> b.errorMessage(f -> f.eq(ERROR_MESSAGE)))
            .send()
            .join();

    // then
    assertThat(result.items())
        .describedAs(
            "the process instance should be found by the error message of its process-level incident")
        .hasSize(1);
    assertThat(result.items().getFirst().getProcessDefinitionId()).isEqualTo(PROCESS_ID);
  }

  @Test
  void shouldFindProcessInstanceByErrorMessageExistsForProcessLevelIncident() {
    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(b -> b.errorMessage(f -> f.exists(true)))
            .send()
            .join();

    // then
    assertThat(result.items())
        .describedAs(
            "the process instance should match the errorMessage exists filter for its process-level incident")
        .hasSize(1);
    assertThat(result.items().getFirst().getProcessDefinitionId()).isEqualTo(PROCESS_ID);
  }
}
