/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport;

import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllProcessInstancesRequest;
import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static io.camunda.operate.webapp.rest.ProcessInstanceRestService.PROCESS_INSTANCE_URL;
import static io.camunda.operate.webapp.rest.dto.listview.ProcessInstanceStateDto.INCIDENT;
import static io.camunda.webapps.schema.entities.listview.ProcessInstanceState.ACTIVE;
import static io.camunda.webapps.schema.entities.operate.ErrorType.JOB_NO_RETRIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.util.ZeebeTestUtil;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.reader.IncidentReader;
import io.camunda.operate.webapp.reader.ListViewReader;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceQueryDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import io.camunda.operate.webapp.rest.dto.listview.ProcessInstanceStateDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeInstanceMetadataDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataDto;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.operate.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.operate.FlowNodeState;
import io.camunda.webapps.schema.entities.operate.IncidentEntity;
import io.camunda.webapps.schema.entities.operate.IncidentState;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Predicate;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public class BasicZeebeImportIT extends OperateZeebeAbstractIT {

  @Autowired private ProcessInstanceReader processInstanceReader;

  @Autowired private PartitionHolder partitionHolder;

  @Autowired private ListViewReader listViewReader;

  @Autowired private IncidentReader incidentReader;

  protected void processImportTypeAndWait(
      final ImportValueType importValueType,
      final Predicate<Object[]> waitTill,
      final Object... arguments) {
    searchTestRule.processRecordsWithTypeAndWait(importValueType, waitTill, arguments);
  }

  @Test
  public void testIncidentCreatesProcessInstance() {
    // having
    final String activityId = "taskA";
    final String processId = "demoProcess";
    final Long processDefinitionKey =
        tester
            .deployProcess("demoProcess_v_1.bpmn")
            .waitUntil()
            .processIsDeployed()
            .getProcessDefinitionKey();
    final Long processInstanceKey =
        tester.startProcessInstance(processId, "{\"a\": \"b\"}").getProcessInstanceKey();
    // create an incident
    tester.failTask(activityId, "Some error");

    // when
    // 1st load incident
    processImportTypeAndWait(
        ImportValueType.INCIDENT, incidentsArePresentCheck, processInstanceKey, 1);

    // and then process instance events
    processImportTypeAndWait(
        ImportValueType.PROCESS_INSTANCE, processInstanceIsCreatedCheck, processInstanceKey);

    tester.waitUntil().incidentIsActive();

    // then
    final ProcessInstanceForListViewEntity processInstanceEntity =
        processInstanceReader.getProcessInstanceByKey(processInstanceKey);
    assertProcessInstanceListViewEntityWithIncident(
        processInstanceEntity, "Demo process", processDefinitionKey, processInstanceKey);
    // and
    final List<IncidentEntity> allIncidents =
        incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey);
    assertThat(allIncidents).hasSize(1);
    assertIncidentEntity(
        allIncidents.get(0), activityId, processDefinitionKey, processId, IncidentState.ACTIVE);

    // and
    final ListViewProcessInstanceDto pi = getSingleProcessInstanceForListView();
    assertListViewProcessInstanceDto(pi, processDefinitionKey, processInstanceKey);

    // and
    final List<FlowNodeInstanceEntity> flowNodeInstances = getFlowNodeInstances(processInstanceKey);
    assertActivityInstanceTreeDto(flowNodeInstances, 2, activityId);
  }

  @Test
  public void testIncidentRecreated() {
    // having
    final String processId = "process";
    final String taskId = "task";
    final String errorMessage = "Some error";
    final String errorMessage2 = "Some error 2";
    final Long processDefinitionKey =
        tester.deployProcess("single-task.bpmn").getProcessDefinitionKey();
    final Long processInstanceKey =
        tester
            .startProcessInstance(processId, null)
            .and()
            .failTask(taskId, errorMessage)
            .waitUntil()
            .incidentIsActive()
            .and()
            .resolveIncident()
            .waitUntil()
            .flowNodeIsActive(taskId)
            .failTask(taskId, errorMessage2)
            .getProcessInstanceKey();

    // when
    // 1st load process instance events
    processImportTypeAndWait(
        ImportValueType.PROCESS_INSTANCE,
        flowNodeIsInIncidentStateCheck,
        processInstanceKey,
        taskId);
    // then load incidents
    processImportTypeAndWait(
        ImportValueType.INCIDENT,
        incidentWithErrorMessageIsActiveCheck,
        processInstanceKey,
        errorMessage2);

    // then
    final ProcessInstanceForListViewEntity processInstanceEntity =
        processInstanceReader.getProcessInstanceByKey(processInstanceKey);
    assertProcessInstanceListViewEntityWithIncident(
        processInstanceEntity, "process", processDefinitionKey, processInstanceKey);
    // and
    final List<IncidentEntity> allIncidents =
        incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey);
    assertThat(allIncidents).hasSize(1);
    assertIncidentEntity(
        allIncidents.get(0), taskId, processDefinitionKey, processId, IncidentState.ACTIVE);

    // and
    final ListViewProcessInstanceDto pi = getSingleProcessInstanceForListView();
    assertThat(pi.getState()).isEqualTo(INCIDENT);

    // and
    final List<FlowNodeInstanceEntity> flowNodeInstances = getFlowNodeInstances(processInstanceKey);
    assertThat(flowNodeInstances).hasSize(2);
    assertThat(flowNodeInstances.get(1).getState()).isEqualTo(FlowNodeState.ACTIVE);
    assertThat(flowNodeInstances.get(1).isIncident()).isTrue();
  }

  @Test
  public void testEarlierEventsAreIgnored() throws Exception {
    // having
    final String activityId = "taskA";
    final String processId = "demoProcess";

    final Long processDefinitionKey =
        tester
            .deployProcess("demoProcess_v_1.bpmn")
            .waitUntil()
            .processIsDeployed()
            .getProcessDefinitionKey();
    final Long processInstanceKey =
        tester.startProcessInstance(processId, "{\"a\": \"b\"}").getProcessInstanceKey();

    // create an incident
    final String incidentError = "Some error";
    // create an incident
    tester.failTask(activityId, incidentError);

    // when
    // 1st load incident
    processImportTypeAndWait(
        ImportValueType.INCIDENT, incidentsArePresentCheck, processInstanceKey, 1);

    // and then process instance events
    processImportTypeAndWait(
        ImportValueType.PROCESS_INSTANCE, processInstanceIsCreatedCheck, processInstanceKey);
    processImportTypeAndWait(
        ImportValueType.JOB, processInstanceIsCreatedCheck, processInstanceKey);

    tester.waitUntil().incidentIsActive();

    // when
    // get flow node instance tree
    final String processInstanceId = String.valueOf(processInstanceKey);
    final FlowNodeInstanceQueryDto request =
        new FlowNodeInstanceQueryDto(processInstanceId, processInstanceId);
    final List<FlowNodeInstanceDto> instances = tester.getFlowNodeInstanceOneListFromRest(request);

    final FlowNodeMetadataDto flowNodeMetadata =
        tester.getFlowNodeMetadataFromRest(
            String.valueOf(processInstanceKey),
            null,
            null,
            instances.get(instances.size() - 1).getId());
    final FlowNodeInstanceMetadataDto flowNodeInstanceMetadata =
        (FlowNodeInstanceMetadataDto) flowNodeMetadata.getInstanceMetadata();
    assertThat(flowNodeMetadata.getIncident()).isNotNull();
    assertThat(flowNodeMetadata.getIncident().getErrorMessage()).isEqualTo(incidentError);
    assertThat(flowNodeMetadata.getIncident().getErrorType().getId())
        .isEqualTo(JOB_NO_RETRIES.name());
  }

  private void assertActivityInstanceTreeDto(
      final List<FlowNodeInstanceEntity> tree, final int childrenCount, final String activityId) {
    assertThat(tree).hasSize(childrenCount);
    assertStartActivityCompleted(tree.get(0));
    assertFlowNodeIsInIncidentState(tree.get(1), activityId);
  }

  private void assertListViewProcessInstanceDto(
      final ListViewProcessInstanceDto pi,
      final Long processDefinitionKey,
      final Long processInstanceKey) {
    assertThat(pi.getState()).isEqualTo(INCIDENT);
    assertThat(pi.getProcessId()).isEqualTo(processDefinitionKey.toString());
    assertThat(pi.getProcessName()).isEqualTo("Demo process");
    assertThat(pi.getProcessVersion()).isEqualTo(1);
    assertThat(pi.getId()).isEqualTo(processInstanceKey.toString());
    assertThat(pi.getEndDate()).isNull();
    assertThat(pi.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(pi.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());
  }

  private void assertIncidentEntity(
      final IncidentEntity incidentEntity,
      final String activityId,
      final Long processDefinitionKey,
      final String bpmnProcessId,
      final IncidentState state) {
    assertThat(incidentEntity.getFlowNodeId()).isEqualTo(activityId);
    assertThat(incidentEntity.getFlowNodeInstanceKey()).isNotNull();
    assertThat(incidentEntity.getErrorMessage()).isNotEmpty();
    assertThat(incidentEntity.getErrorType()).isNotNull();
    assertThat(incidentEntity.getState()).isEqualTo(state);
    assertThat(incidentEntity.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(incidentEntity.getBpmnProcessId()).isEqualTo(bpmnProcessId);
  }

  private void assertProcessInstanceListViewEntityWithIncident(
      final ProcessInstanceForListViewEntity processInstanceEntity,
      final String processName,
      final Long processDefinitionKey,
      final Long processInstanceKey) {
    assertThat(processInstanceEntity.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(processInstanceEntity.getProcessName()).isEqualTo(processName);
    assertThat(processInstanceEntity.getProcessVersion()).isEqualTo(1);
    assertThat(processInstanceEntity.getId()).isEqualTo(processInstanceKey.toString());
    assertThat(processInstanceEntity.getKey()).isEqualTo(processInstanceKey);
    assertThat(processInstanceEntity.isIncident()).isEqualTo(true);
    assertThat(processInstanceEntity.getState()).isEqualTo(ACTIVE);
    assertThat(processInstanceEntity.getEndDate()).isNull();
    assertThat(processInstanceEntity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(processInstanceEntity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());
  }

  protected ListViewProcessInstanceDto getSingleProcessInstanceForListView() {
    final ListViewRequestDto request = createGetAllProcessInstancesRequest();
    request.setPageSize(100);
    final ListViewResponseDto listViewResponse = listViewReader.queryProcessInstances(request);
    assertThat(listViewResponse.getTotalCount()).isEqualTo(1);
    assertThat(listViewResponse.getProcessInstances()).hasSize(1);
    return listViewResponse.getProcessInstances().get(0);
  }

  protected List<FlowNodeInstanceEntity> getFlowNodeInstances(final Long processInstanceKey) {
    return tester.getAllFlowNodeInstances(processInstanceKey);
  }

  @Test
  public void testOnlyIncidentIsLoaded() throws Exception {
    // having
    final String activityId = "taskA";
    final String processId = "demoProcess";
    deployProcess("demoProcess_v_1.bpmn");
    final Long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(camundaClient, processId, "{\"a\": \"b\"}");
    // create an incident
    ZeebeTestUtil.failTask(getClient(), activityId, getWorkerName(), 3, "Some error");

    // when
    // load only incidents
    processImportTypeAndWait(
        ImportValueType.INCIDENT, incidentsArePresentCheck, processInstanceKey, 1);

    assertListViewResponse();
    // if nothing is returned in list view - there is no way to access the process instance, no need
    // to check other queries

  }

  protected void assertListViewResponse() throws Exception {
    final ListViewRequestDto listViewRequest = createGetAllProcessInstancesRequest();
    listViewRequest.setPageSize(100);
    final MockHttpServletRequestBuilder request =
        post(query())
            .content(mockMvcTestRule.json(listViewRequest))
            .contentType(mockMvcTestRule.getContentType());

    final MvcResult mvcResult =
        mockMvc
            .perform(request)
            .andExpect(status().isOk())
            .andExpect(content().contentType(mockMvcTestRule.getContentType()))
            .andReturn();

    // check that nothing is returned
    final ListViewResponseDto listViewResponse =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {});
    assertThat(listViewResponse.getTotalCount()).isEqualTo(0);
    assertThat(listViewResponse.getProcessInstances()).hasSize(0);
  }

  @Test
  public void testIncidentDeletedAfterActivityCompleted() {
    // having
    final String activityId = "taskA";
    final String processId = "demoProcess";
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .serviceTask(activityId)
            .zeebeJobType(activityId)
            .endEvent()
            .done();
    deployProcess(modelInstance, "demoProcess_v_1.bpmn");
    final Long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(camundaClient, processId, "{\"a\": \"b\"}");

    // create an incident
    final Long jobKey =
        ZeebeTestUtil.failTask(getClient(), activityId, getWorkerName(), 3, "Some error");
    searchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, processInstanceKey);
    final long incidentKey = getOnlyIncidentKey(processInstanceKey);

    // when update retries
    ZeebeTestUtil.resolveIncident(camundaClient, jobKey, incidentKey);
    ZeebeTestUtil.completeTask(getClient(), activityId, getWorkerName(), "{}");

    processImportTypeAndWait(
        ImportValueType.PROCESS_INSTANCE,
        processInstancesAreFinishedCheck,
        List.of(processInstanceKey));
    processImportTypeAndWait(
        ImportValueType.INCIDENT, noActivitiesHaveIncident, processInstanceKey);

    // then
    final List<IncidentEntity> allIncidents =
        incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey);
    assertThat(allIncidents).hasSize(0);

    // assert list view data
    final ListViewProcessInstanceDto pi = getSingleProcessInstanceForListView();
    assertThat(pi.getId()).isEqualTo(processInstanceKey.toString());
    assertThat(pi.getState()).isEqualTo(ProcessInstanceStateDto.COMPLETED);
    assertThat(pi.getEndDate()).isNotNull();

    // assert flow node instances
    final List<FlowNodeInstanceEntity> flowNodeInstances = getFlowNodeInstances(processInstanceKey);
    assertThat(flowNodeInstances.size()).isGreaterThanOrEqualTo(2);
    assertStartActivityCompleted(flowNodeInstances.get(0));
    assertFlowNodeIsCompleted(flowNodeInstances.get(1), "taskA");
  }

  protected long getOnlyIncidentKey(final long processInstanceKey) {
    final List<IncidentEntity> incidents =
        incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey);
    assertThat(incidents).hasSize(1);
    return incidents.get(0).getKey();
  }

  @Test
  public void testIncidentDeletedAfterActivityTerminated() {
    // having
    final String activityId = "taskA";
    final String processId = "demoProcess";
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .serviceTask(activityId)
            .zeebeJobType(activityId)
            .endEvent()
            .done();
    deployProcess(modelInstance, "demoProcess_v_1.bpmn");
    final Long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(camundaClient, processId, "{\"a\": \"b\"}");

    // create an incident
    final Long jobKey =
        ZeebeTestUtil.failTask(getClient(), activityId, getWorkerName(), 3, "Some error");
    searchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, processInstanceKey);
    final long incidentKey = getOnlyIncidentKey(processInstanceKey);

    // when update retries
    ZeebeTestUtil.resolveIncident(camundaClient, jobKey, incidentKey);

    ZeebeTestUtil.cancelProcessInstance(getClient(), processInstanceKey);

    processImportTypeAndWait(
        ImportValueType.PROCESS_INSTANCE, processInstanceIsCanceledCheck, processInstanceKey);
    processImportTypeAndWait(
        ImportValueType.INCIDENT, noActivitiesHaveIncident, processInstanceKey);
    // then
    final List<IncidentEntity> allIncidents =
        incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey);
    assertThat(allIncidents).hasSize(0);

    // assert list view data
    final ListViewProcessInstanceDto pi = getSingleProcessInstanceForListView();
    assertThat(pi.getId()).isEqualTo(processInstanceKey.toString());
    assertThat(pi.getState()).isEqualTo(ProcessInstanceStateDto.CANCELED);
    assertThat(pi.getEndDate()).isNotNull();

    // assert flow node instances
    final List<FlowNodeInstanceEntity> flowNodeInstances = getFlowNodeInstances(processInstanceKey);
    assertThat(flowNodeInstances.size()).isGreaterThanOrEqualTo(2);
    final FlowNodeInstanceEntity flowNodeInstance = flowNodeInstances.get(1);
    assertThat(flowNodeInstance.getFlowNodeId()).isEqualTo(activityId);
    assertThat(flowNodeInstance.getState()).isEqualTo(FlowNodeState.TERMINATED);
    assertThat(flowNodeInstance.getEndDate()).isNotNull();
  }

  @Test
  public void testIncidentMetadataForCallActivity() throws Exception {
    // having process with call activity
    final String parentProcessId = "parentProcess";
    final String callActivity1Id = "callActivity1";
    final String calledProcess1Id = "calledProcess";
    final String callActivity2Id = "callActivity2";
    final String calledProcess2Id = "process";
    final String errorMsg = "Some error";
    final BpmnModelInstance testProcess =
        Bpmn.createExecutableProcess(parentProcessId)
            .startEvent()
            .callActivity(callActivity1Id)
            .zeebeProcessId(calledProcess1Id)
            .done();
    final BpmnModelInstance testProcess2 =
        Bpmn.createExecutableProcess(calledProcess1Id)
            .startEvent()
            .callActivity(callActivity2Id)
            .zeebeProcessId(calledProcess2Id)
            .done();
    tester.deployProcess("single-task.bpmn").getProcessDefinitionKey();

    final long parentProcessInstanceKey =
        tester
            .deployProcess(testProcess, "testProcess.bpmn")
            .deployProcess(testProcess2, "testProcess2.bpmn")
            .startProcessInstance(parentProcessId, null)
            .getProcessInstanceKey();

    sleepFor(2000L);

    // create an incident
    ZeebeTestUtil.failTask(getClient(), "task", getWorkerName(), 3, errorMsg);

    // when
    // 1st load incident
    processImportTypeAndWait(ImportValueType.INCIDENT, incidentsInAnyInstanceArePresentCheck, 1);

    // and then process instance events
    processImportTypeAndWait(
        ImportValueType.PROCESS_INSTANCE, processInstanceIsCreatedCheck, parentProcessInstanceKey);

    tester.waitUntil().incidentIsActive();

    // when
    // get metadata by flowNodeId from parent process instance
    final String processInstanceId = String.valueOf(parentProcessInstanceKey);
    final FlowNodeMetadataDto flowNodeMetadata =
        tester.getFlowNodeMetadataFromRest(processInstanceId, callActivity1Id, null, null);

    // then one incident is returned
    assertThat(flowNodeMetadata.getIncidentCount()).isEqualTo(1);
    assertThat(flowNodeMetadata.getIncident().getErrorMessage()).isEqualTo(errorMsg);
  }

  @Test
  public void testPartitionIds() {
    final List<Integer> operatePartitions = partitionHolder.getPartitionIds();
    final int zeebePartitionsCount =
        camundaClient.newTopologyRequest().send().join().getPartitionsCount();
    assertThat(operatePartitions).hasSize(zeebePartitionsCount);
    assertThat(operatePartitions).allMatch(id -> id <= zeebePartitionsCount && id >= 1);
  }

  private void assertStartActivityCompleted(final FlowNodeInstanceEntity activity) {
    assertFlowNodeIsCompleted(activity, "start");
  }

  private void assertFlowNodeIsInIncidentState(
      final FlowNodeInstanceEntity activity, final String activityId) {
    assertThat(activity.getFlowNodeId()).isEqualTo(activityId);
    assertThat(activity.getProcessDefinitionKey()).isNotNull();
    assertThat(activity.getBpmnProcessId()).isNotNull();
    assertThat(activity.getState()).isEqualTo(FlowNodeState.ACTIVE);
    assertThat(activity.getIncidentKey()).isNotNull();
    assertThat(activity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(activity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());
  }

  private void assertFlowNodeIsCompleted(
      final FlowNodeInstanceEntity activity, final String activityId) {
    assertThat(activity.getFlowNodeId()).isEqualTo(activityId);
    assertThat(activity.getProcessDefinitionKey()).isNotNull();
    assertThat(activity.getBpmnProcessId()).isNotNull();
    assertThat(activity.getState()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(activity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(activity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());
    assertThat(activity.getEndDate()).isAfterOrEqualTo(activity.getStartDate());
    assertThat(activity.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());
  }

  private String query() {
    return PROCESS_INSTANCE_URL;
  }
}
