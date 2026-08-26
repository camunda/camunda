/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.orchestration.incidents;

import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.it.util.TestHelper.waitUntilIncidentsAreActive;
import static io.camunda.it.util.TestHelper.waitUntilProcessInstanceHasIncidents;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.qa.util.multidb.MultiDbTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * An incident can be raised at the process level, without any element instance to attach to, for
 * example by a process level execution listener. Such an incident must still be reachable when
 * searching process instances by error message.
 */
@MultiDbTest
public class ProcessLevelIncidentSearchTest {

  private static final String PROCESS_ID = "process_level_incident";
  private static final String LISTENER_JOB_TYPE = "processLevelListener";

  private static CamundaClient camundaClient;
  private static long processInstanceKey;
  private static String errorMessage;

  @BeforeAll
  static void beforeAll() {
    deployResource(camundaClient, "process/process_level_incident.bpmn");
    waitForProcessesToBeDeployed(camundaClient, 1);

    processInstanceKey = startProcessInstance(camundaClient, PROCESS_ID).getProcessInstanceKey();
    waitForProcessInstancesToStart(camundaClient, 1);

    final var job =
        camundaClient
            .newActivateJobsCommand()
            .jobType(LISTENER_JOB_TYPE)
            .maxJobsToActivate(1)
            .send()
            .join()
            .getJobs()
            .getFirst();
    errorMessage = "process level listener failed for " + processInstanceKey;
    camundaClient.newFailCommand(job.getKey()).retries(0).errorMessage(errorMessage).send().join();

    waitUntilIncidentsAreActive(camundaClient, 1);
    waitUntilProcessInstanceHasIncidents(camundaClient, 1);
  }

  @Test
  void shouldFindProcessInstanceByProcessLevelIncidentErrorMessage() {
    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.errorMessage(m -> m.eq(errorMessage)))
            .send()
            .join();

    // then
    assertThat(result.items())
        .extracting(ProcessInstance::getProcessInstanceKey)
        .containsExactly(processInstanceKey);
  }

  @Test
  void shouldFindProcessInstanceByExistingProcessLevelIncidentErrorMessage() {
    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.errorMessage(m -> m.exists(true)))
            .send()
            .join();

    // then
    assertThat(result.items())
        .extracting(ProcessInstance::getProcessInstanceKey)
        .containsExactly(processInstanceKey);
  }

  @Test
  void shouldNotFindProcessInstanceByAnotherErrorMessage() {
    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.errorMessage(m -> m.eq("some other error message")))
            .send()
            .join();

    // then
    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldNotFindProcessInstanceWithoutAnyErrorMessage() {
    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.errorMessage(m -> m.exists(false)))
            .send()
            .join();

    // then
    assertThat(result.items()).isEmpty();
  }
}
