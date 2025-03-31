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
import static io.camunda.it.util.TestHelper.waitUntilIncidentsAreResolved;
import static io.camunda.it.util.TestHelper.waitUntilProcessInstanceHasIncidents;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.search.enums.FlowNodeInstanceState;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.FlowNodeInstance;
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
 * Tests that when one of several incidents is resolved all involved flow node instances and process
 * instances in the call stack stay in incident state.
 */
@MultiDbTest
public class IncidentPartialResolveCallActivityTest {

  private static CamundaClient camundaClient;
  private static final String CALLED_PROCESS_ID = "calledProcess";
  private static final String SERVICE_TASK_ID = "serviceTask";
  private static final String SERVICE_TASK_1_ID = "serviceTask1";
  private static final String SERVICE_TASK_2_ID = "serviceTask2";
  private static final String PARENT_PROCESS_ID = "parentProcess";
  private static final String CALL_ACTIVITY_ID = "callActivity";
  private static final String ERROR_MSG = "Error in called process task";
  private static final List<Process> DEPLOYED_PROCESSES = new ArrayList<>();
  private static Process parentProcess;
  private static Process childProcess;

  /**
   * Data for the test: 1. parentProcess instance with call activity 2. calledProcess instance with
   * one resolved and one active incident
   */
  @BeforeAll
  static void beforeAll() {
    final BpmnModelInstance testProcess =
        Bpmn.createExecutableProcess(PARENT_PROCESS_ID)
            .startEvent()
            .parallelGateway("parallel")
            .serviceTask(SERVICE_TASK_ID)
            .zeebeJobType(SERVICE_TASK_ID)
            .moveToNode("parallel")
            .callActivity(CALL_ACTIVITY_ID)
            .zeebeProcessId(CALLED_PROCESS_ID)
            .done();
    parentProcess =
        deployResource(camundaClient, testProcess, "testProcess.bpmn").getProcesses().getFirst();
    DEPLOYED_PROCESSES.add(parentProcess);

    final BpmnModelInstance testProcess1 =
        Bpmn.createExecutableProcess(CALLED_PROCESS_ID)
            .startEvent()
            .parallelGateway("parallel")
            .serviceTask(SERVICE_TASK_1_ID)
            .zeebeJobType(SERVICE_TASK_1_ID)
            .moveToNode("parallel")
            .serviceTask(SERVICE_TASK_2_ID)
            .zeebeJobType(SERVICE_TASK_2_ID)
            .done();
    childProcess =
        deployResource(camundaClient, testProcess1, "calledProcess.bpmn").getProcesses().getFirst();
    DEPLOYED_PROCESSES.add(childProcess);

    waitForProcessesToBeDeployed(camundaClient, 2);

    startProcessInstance(camundaClient, PARENT_PROCESS_ID).getProcessInstanceKey();
    waitForProcessInstancesToStart(camundaClient, 2);

    final long[] serviceTaskKey = new long[1];
    // fail 2 service tasks
    List.of(SERVICE_TASK_1_ID, SERVICE_TASK_2_ID)
        .forEach(
            taskId -> {
              serviceTaskKey[0] =
                  camundaClient
                      .newActivateJobsCommand()
                      .jobType(taskId)
                      .maxJobsToActivate(1)
                      .send()
                      .join()
                      .getJobs()
                      .getFirst()
                      .getKey();
              camundaClient
                  .newFailCommand(serviceTaskKey[0])
                  .retries(0)
                  .errorMessage(ERROR_MSG)
                  .send()
                  .join();
            });
    waitUntilProcessInstanceHasIncidents(camundaClient, 2);
    waitUntilIncidentsAreActive(camundaClient, 2);

    // resolve incident in the first service task
    camundaClient.newUpdateRetriesCommand(serviceTaskKey[0]).retries(1).send().join();
    final Incident incident =
        camundaClient
            .newIncidentSearchRequest()
            .filter(f -> f.jobKey(serviceTaskKey[0]))
            .send()
            .join()
            .items()
            .getFirst();
    camundaClient.newResolveIncidentCommand(incident.getIncidentKey()).send().join();

    waitUntilIncidentsAreResolved(camundaClient, 1);
  }

  @AfterAll
  static void afterAll() {
    DEPLOYED_PROCESSES.clear();
  }

  @Test
  public void testOneIncident() {
    // when
    final List<Incident> incidents =
        camundaClient
            .newIncidentSearchRequest()
            .filter(fn -> fn.state(IncidentState.ACTIVE))
            .send()
            .join()
            .items();

    // then
    assertThat(incidents).hasSize(1);
  }

  @Test
  public void testParentFlowNodeInstanceInIncidentState() {
    // when
    final FlowNodeInstance flowNodeInstance =
        camundaClient
            .newFlownodeInstanceSearchRequest()
            .filter(f -> f.flowNodeId(CALL_ACTIVITY_ID))
            .page(p -> p.limit(100))
            .send()
            .join()
            .items()
            .getFirst();

    // then
    assertThat(flowNodeInstance).isNotNull();
    assertThat(flowNodeInstance.getState()).isEqualTo(FlowNodeInstanceState.ACTIVE);
    assertThat(flowNodeInstance.getIncident()).isEqualTo(true);
  }

  @Test
  public void testParentProcessInstanceInIncidentState() {
    // when
    final ProcessInstance processInstance =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.processDefinitionKey(parentProcess.getProcessDefinitionKey()))
            .send()
            .join()
            .items()
            .getFirst();

    // then
    assertThat(processInstance).isNotNull();
    assertThat(processInstance.getState()).isEqualTo(ProcessInstanceState.ACTIVE);
    assertThat(processInstance.getHasIncident()).isTrue();
  }

  @Test
  public void testChildFlowNodeInstanceInIncidentState() {
    // when
    final FlowNodeInstance flowNodeInstance =
        camundaClient
            .newFlownodeInstanceSearchRequest()
            .filter(f -> f.flowNodeId(SERVICE_TASK_1_ID))
            .page(p -> p.limit(100))
            .send()
            .join()
            .items()
            .getFirst();

    // then
    assertThat(flowNodeInstance).isNotNull();
    assertThat(flowNodeInstance.getState()).isEqualTo(FlowNodeInstanceState.ACTIVE);
    assertThat(flowNodeInstance.getIncident()).isEqualTo(true);
  }

  @Test
  public void testChildProcessInstanceInIncidentState() {
    // when
    final ProcessInstance processInstance =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.processDefinitionKey(childProcess.getProcessDefinitionKey()))
            .send()
            .join()
            .items()
            .getFirst();

    // then
    assertThat(processInstance).isNotNull();
    assertThat(processInstance.getState()).isEqualTo(ProcessInstanceState.ACTIVE);
    assertThat(processInstance.getHasIncident()).isTrue();
  }
}
