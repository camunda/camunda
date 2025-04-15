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
import static io.camunda.it.util.TestHelper.waitUntilElementInstanceHasIncidents;
import static io.camunda.it.util.TestHelper.waitUntilIncidentsAreActive;
import static io.camunda.it.util.TestHelper.waitUntilProcessInstanceHasIncidents;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests the incident propagation from a called process to the parent process, implemented in {@link
 * io.camunda.exporter.tasks.incident.IncidentUpdateTask}
 */
@MultiDbTest
class IncidentPropagationTest {

  private static final String CALL_ACTIVITY_2_ID = "callActivity2";
  private static final String CALLED_PROCESS_ID = "calledProcess";
  private static final String LAST_CALLED_TASK_ID = "taskA";
  private static final String SERVICE_TASK_ID = "serviceTask";
  private static final String PARENT_PROCESS_ID = "parentProcess";
  private static final String CALL_ACTIVITY_1_ID = "callActivity1";
  private static final String ERROR_MSG_1 = "Error in called process task";
  private static final String ERROR_MSG_2 = "Error in last called process task";
  private static final List<Process> DEPLOYED_PROCESSES = new ArrayList<>();
  private static CamundaClient camundaClient;

  /** parentProcess -> calledProcess (has incident) -> process (has incident) */
  @BeforeAll
  static void beforeAll() {
    final String calledProcess2Id = "service_tasks_v1";
    final BpmnModelInstance testProcess =
        Bpmn.createExecutableProcess(PARENT_PROCESS_ID)
            .startEvent()
            .callActivity(CALL_ACTIVITY_1_ID)
            .zeebeProcessId(CALLED_PROCESS_ID)
            .done();
    DEPLOYED_PROCESSES.addAll(
        deployResource(camundaClient, testProcess, "testProcess.bpmn").getProcesses());
    final BpmnModelInstance testProcess2 =
        Bpmn.createExecutableProcess(CALLED_PROCESS_ID)
            .startEvent()
            .parallelGateway("parallel")
            .serviceTask(SERVICE_TASK_ID)
            .zeebeJobType(SERVICE_TASK_ID)
            .moveToNode("parallel")
            .callActivity(CALL_ACTIVITY_2_ID)
            .zeebeProcessId(calledProcess2Id)
            .done();
    DEPLOYED_PROCESSES.addAll(
        deployResource(camundaClient, testProcess2, "testProcess2.bpmn").getProcesses());
    DEPLOYED_PROCESSES.addAll(
        deployResource(camundaClient, "process/service_tasks_v1.bpmn").getProcesses());

    waitForProcessesToBeDeployed(camundaClient, 3);

    startProcessInstance(camundaClient, PARENT_PROCESS_ID);

    waitForProcessInstancesToStart(camundaClient, 3);

    // fail task in last called process
    long jobKey =
        camundaClient
            .newActivateJobsCommand()
            .jobType(LAST_CALLED_TASK_ID)
            .maxJobsToActivate(1)
            .send()
            .join()
            .getJobs()
            .getFirst()
            .getKey();
    camundaClient.newFailCommand(jobKey).retries(0).errorMessage(ERROR_MSG_2).send().join();
    // fail task in the second process
    jobKey =
        camundaClient
            .newActivateJobsCommand()
            .jobType(SERVICE_TASK_ID)
            .maxJobsToActivate(1)
            .send()
            .join()
            .getJobs()
            .getFirst()
            .getKey();
    camundaClient.newFailCommand(jobKey).retries(0).errorMessage(ERROR_MSG_1).send().join();
    waitUntilProcessInstanceHasIncidents(camundaClient, 3);
    waitUntilElementInstanceHasIncidents(camundaClient, 4);
    waitUntilIncidentsAreActive(camundaClient, 2);
  }

  @AfterAll
  static void afterAll() {
    DEPLOYED_PROCESSES.clear();
  }

  @Test
  void testIncidentsAreActive() {
    // incidents are updated by background task, PENDING state is changed on ACTIVE
    final List<Incident> incidents = camundaClient.newIncidentSearchRequest().send().join().items();
    assertThat(incidents).hasSize(2);
    incidents.forEach(
        incident -> {
          assertThat(incident.getState()).isEqualTo(IncidentState.ACTIVE);
        });
    assertThat(incidents).extracting(Incident::getErrorMessage).contains(ERROR_MSG_1, ERROR_MSG_2);
  }

  @Test
  void testElementInstanceInIncidentState() {
    final ElementInstance elementInstance1 =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.elementId(SERVICE_TASK_ID))
            .send()
            .join()
            .items()
            .getFirst();
    assertThat(elementInstance1).isNotNull();
    assertThat(elementInstance1.getIncident()).isTrue();

    final ElementInstance elementInstance2 =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.elementId(LAST_CALLED_TASK_ID))
            .send()
            .join()
            .items()
            .getFirst();
    assertThat(elementInstance2).isNotNull();
    assertThat(elementInstance2.getIncident()).isTrue();
  }

  @Test
  void testSecondProcessInstanceHasIncident() {
    final ProcessInstance processInstance =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.processDefinitionId(CALLED_PROCESS_ID))
            .send()
            .join()
            .items()
            .getFirst();
    assertThat(processInstance).isNotNull();
    assertThat(processInstance.getHasIncident()).isTrue();
  }

  @Test
  void testSecondCallActivityHasIncident() {
    final ElementInstance elementInstance =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.elementId(CALL_ACTIVITY_2_ID))
            .send()
            .join()
            .items()
            .getFirst();
    assertThat(elementInstance).isNotNull();
    assertThat(elementInstance.getIncident()).isTrue();
  }

  @Test
  void testFirstProcessInstanceHasIncident() {
    final ProcessInstance processInstance =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.processDefinitionId(PARENT_PROCESS_ID))
            .send()
            .join()
            .items()
            .getFirst();
    assertThat(processInstance).isNotNull();
    assertThat(processInstance.getHasIncident()).isTrue();
  }

  @Test
  void testFirstCallActivityHasIncident() {
    final ElementInstance elementInstance =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.elementId(CALL_ACTIVITY_1_ID))
            .send()
            .join()
            .items()
            .getFirst();
    assertThat(elementInstance).isNotNull();
    assertThat(elementInstance.getIncident()).isTrue();
  }
}
