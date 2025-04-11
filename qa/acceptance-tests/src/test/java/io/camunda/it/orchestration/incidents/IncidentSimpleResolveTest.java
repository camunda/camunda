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
import static io.camunda.it.util.TestHelper.waitUntilIncidentIsResolvedOnElementInstance;
import static io.camunda.it.util.TestHelper.waitUntilIncidentIsResolvedOnProcessInstance;
import static io.camunda.it.util.TestHelper.waitUntilIncidentsAreResolved;
import static io.camunda.it.util.TestHelper.waitUntilProcessInstanceHasIncidents;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests that when incident is resolved all involved element instances and process instance are in
 * ACTIVE state.
 */
@MultiDbTest
public class IncidentSimpleResolveTest {

  private static CamundaClient camundaClient;

  private static final List<Process> DEPLOYED_PROCESSES = new ArrayList<>();
  private static long processInstanceKey;
  private static final String SERVICE_TASK_ID = "taskA";

  @BeforeAll
  static void beforeAll() {
    DEPLOYED_PROCESSES.addAll(
        deployResource(camundaClient, "process/service_tasks_v1.bpmn").getProcesses());

    waitForProcessesToBeDeployed(camundaClient, 1);

    processInstanceKey =
        startProcessInstance(camundaClient, "service_tasks_v1").getProcessInstanceKey();

    waitForProcessInstancesToStart(camundaClient, 1);
    final long jobKey =
        camundaClient
            .newActivateJobsCommand()
            .jobType(SERVICE_TASK_ID)
            .maxJobsToActivate(1)
            .send()
            .join()
            .getJobs()
            .getFirst()
            .getKey();
    camundaClient.newFailCommand(jobKey).retries(0).send().join();
    waitUntilProcessInstanceHasIncidents(camundaClient, 1);

    camundaClient.newUpdateRetriesCommand(jobKey).retries(1).send().join();
    final Incident incident =
        camundaClient.newIncidentSearchRequest().send().join().items().getFirst();
    camundaClient.newResolveIncidentCommand(incident.getIncidentKey()).send().join();

    waitUntilIncidentIsResolvedOnProcessInstance(camundaClient, 1);
    waitUntilIncidentIsResolvedOnElementInstance(camundaClient, 2);
    waitUntilIncidentsAreResolved(camundaClient, 1);
  }

  @AfterAll
  static void afterAll() {
    DEPLOYED_PROCESSES.clear();
  }

  @Test
  public void testElementInstanceWithoutIncident() {
    // when
    final ElementInstance elementInstance =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.elementId(SERVICE_TASK_ID))
            .page(p -> p.limit(100))
            .sort(s -> s.elementId().asc())
            .send()
            .join()
            .items()
            .getFirst();

    // then
    assertThat(elementInstance).isNotNull();
    assertThat(elementInstance.getState()).isEqualTo(ElementInstanceState.ACTIVE);
    assertThat(elementInstance.getIncident()).isEqualTo(false);
  }

  @Test
  public void testProcessInstanceWithoutIncident() {
    // when
    final ProcessInstance processInstance =
        camundaClient.newProcessInstanceGetRequest(processInstanceKey).send().join();

    // then
    assertThat(processInstance).isNotNull();
    assertThat(processInstance.getState()).isEqualTo(ProcessInstanceState.ACTIVE);
    assertThat(processInstance.getHasIncident()).isFalse();
  }

  @Test
  public void testNoIncidents() {
    // when
    final List<Incident> incidents =
        camundaClient
            .newIncidentSearchRequest()
            .filter(fn -> fn.state(IncidentState.ACTIVE))
            .send()
            .join()
            .items();

    // then
    assertThat(incidents).isEmpty();
  }
}
