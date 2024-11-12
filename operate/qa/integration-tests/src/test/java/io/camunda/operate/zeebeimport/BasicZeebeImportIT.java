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
import static io.camunda.webapps.schema.entities.operate.ErrorType.JOB_NO_RETRIES;
import static io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceState.ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.util.PayloadUtil;
import io.camunda.operate.util.ZeebeTestUtil;
import io.camunda.operate.util.searchrepository.TestSearchRepository;
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
import io.camunda.webapps.schema.descriptors.operate.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.entities.operate.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.operate.FlowNodeState;
import io.camunda.webapps.schema.entities.operate.IncidentEntity;
import io.camunda.webapps.schema.entities.operate.IncidentState;
import io.camunda.webapps.schema.entities.operate.dmn.DecisionInstanceEntity;
import io.camunda.webapps.schema.entities.operate.dmn.DecisionInstanceState;
import io.camunda.webapps.schema.entities.operate.dmn.DecisionType;
import io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceForListViewEntity;
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

  @Autowired private TestSearchRepository testSearchRepository;

  @Autowired private DecisionInstanceTemplate decisionInstanceTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private PayloadUtil payloadUtil;

  @Test
  public void testProcessNameAndVersionAreLoaded() {
    // having
    final String processId = "demoProcess";
    final Long processDefinitionKey =
        ZeebeTestUtil.deployProcess(zeebeClient, null, "demoProcess_v_1.bpmn");
    final long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"a\": \"b\"}");

    // when
    // 1st load process instance index, then deployment
    processImportTypeAndWait(
        ImportValueType.PROCESS_INSTANCE, processInstanceIsCreatedCheck, processInstanceKey);
    processImportTypeAndWait(ImportValueType.PROCESS, processIsDeployedCheck, processDefinitionKey);

    // then
    final ProcessInstanceForListViewEntity processInstanceEntity =
        processInstanceReader.getProcessInstanceByKey(processInstanceKey);
    assertThat(processInstanceEntity.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(processInstanceEntity.getProcessName()).isNotNull();
    assertThat(processInstanceEntity.getProcessVersion()).isEqualTo(1);
  }

  protected void processImportTypeAndWait(
      final ImportValueType importValueType,
      final Predicate<Object[]> waitTill,
      final Object... arguments) {
    searchTestRule.processRecordsWithTypeAndWait(importValueType, waitTill, arguments);
  }

  @Test
  public void testCreateProcessInstanceWithEmptyProcessName() {
    // given a process with empty name
    final String processId = "emptyNameProcess";
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("taskA")
            .zeebeJobType("taskA")
            .endEvent()
            .done();

    final Long processDefinitionKey = deployProcess(model, "emptyNameProcess.bpmn");

    final long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    searchTestRule.processAllRecordsAndWait(processInstanceIsCreatedCheck, processInstanceKey);
    searchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "taskA");

    // then it should returns the processId instead of an empty name
    final ProcessInstanceForListViewEntity processInstanceEntity =
        processInstanceReader.getProcessInstanceByKey(processInstanceKey);
    assertThat(processInstanceEntity.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(processInstanceEntity.getBpmnProcessId()).isEqualTo(processId);
    assertThat(processInstanceEntity.getProcessName()).isEqualTo(processId);
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
        ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"a\": \"b\"}");
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
        ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"a\": \"b\"}");

    // create an incident
    final Long jobKey =
        ZeebeTestUtil.failTask(getClient(), activityId, getWorkerName(), 3, "Some error");
    searchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, processInstanceKey);
    final long incidentKey = getOnlyIncidentKey(processInstanceKey);

    // when update retries
    ZeebeTestUtil.resolveIncident(zeebeClient, jobKey, incidentKey);
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
        ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"a\": \"b\"}");

    // create an incident
    final Long jobKey =
        ZeebeTestUtil.failTask(getClient(), activityId, getWorkerName(), 3, "Some error");
    searchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, processInstanceKey);
    final long incidentKey = getOnlyIncidentKey(processInstanceKey);

    // when update retries
    ZeebeTestUtil.resolveIncident(zeebeClient, jobKey, incidentKey);

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
        zeebeClient.newTopologyRequest().send().join().getPartitionsCount();
    assertThat(operatePartitions).hasSize(zeebePartitionsCount);
    assertThat(operatePartitions).allMatch(id -> id <= zeebePartitionsCount && id >= 1);
  }

  @Test
  public void testDecisionInstanceEvaluatedWithBigInputAndOutput() throws Exception {
    // given
    final String bpmnProcessId = "process";
    final String demoDecisionId2 = "decision";

    final String elementId = "task";
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess(bpmnProcessId)
            .startEvent()
            .businessRuleTask(
                elementId,
                task -> task.zeebeCalledDecisionId(demoDecisionId2).zeebeResultVariable("result"))
            .done();

    final String bigJSONVariablePayload = payloadUtil.readStringFromClasspath("/large-payload.txt");
    final String payload = "{\"value\": \"" + bigJSONVariablePayload + "\"}";
    tester
        .deployProcess(instance, "test.bpmn")
        .deployDecision("largeInputOutput.dmn")
        .waitUntil()
        .processIsDeployed()
        .and()
        .decisionsAreDeployed(1)
        // when
        .startProcessInstance(bpmnProcessId, payload)
        .waitUntil()
        .decisionInstancesAreCreated(1);

    // then
    final List<DecisionInstanceEntity> decisionEntities =
        testSearchRepository.searchAll(
            decisionInstanceTemplate.getAlias(), DecisionInstanceEntity.class);

    assertThat(decisionEntities).hasSize(1);
    assertThat(decisionEntities.get(0).getEvaluatedInputs()).hasSize(1);
    assertThat(decisionEntities.get(0).getEvaluatedInputs().get(0).getValue())
        .contains(bigJSONVariablePayload);
    assertThat(decisionEntities.get(0).getEvaluatedOutputs()).hasSize(1);
    assertThat(decisionEntities.get(0).getEvaluatedOutputs().get(0).getValue())
        .contains(bigJSONVariablePayload);
  }

  @Test
  public void testDecisionInstanceFailed() throws Exception {
    // given
    final String bpmnProcessId = "process";
    final String demoDecisionId1 = "invoiceClassification";
    final String demoDecisionId2 = "invoiceAssignApprover";
    final String decision1Name = "Invoice Classification";
    final String decision2Name = "Assign Approver Group";

    final String elementId = "task";
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess(bpmnProcessId)
            .startEvent()
            .businessRuleTask(
                elementId,
                task ->
                    task.zeebeCalledDecisionId(demoDecisionId2)
                        .zeebeResultVariable("approverGroups"))
            .done();

    tester
        .deployProcess(instance, "test.bpmn")
        .deployDecision("invoiceBusinessDecisions_v_1.dmn")
        .waitUntil()
        .processIsDeployed()
        .and()
        .decisionsAreDeployed(2)
        // when
        .startProcessInstance(bpmnProcessId)
        .waitUntil()
        .decisionInstancesAreCreated(1);

    // then
    final List<DecisionInstanceEntity> decisionEntities =
        testSearchRepository.searchAll(
            decisionInstanceTemplate.getAlias(), DecisionInstanceEntity.class);

    assertThat(decisionEntities).hasSize(1);
    final DecisionInstanceEntity entity = decisionEntities.get(0);
    assertThat(entity.getId()).isNotNull();
    assertThat(entity.getKey()).isNotNull();
    assertThat(entity.getExecutionIndex()).isNotNull();
    assertThat(entity.getId())
        .isEqualTo(String.format("%s-%s", entity.getKey(), entity.getExecutionIndex()));
    assertThat(entity.getDecisionId()).isEqualTo(demoDecisionId1);
    assertThat(entity.getDecisionName()).isEqualTo(decision1Name);
    assertThat(entity.getDecisionVersion()).isEqualTo(1);
    assertThat(entity.getState()).isEqualTo(DecisionInstanceState.FAILED);
    assertThat(entity.getDecisionDefinitionId()).isNotNull();
    assertThat(entity.getDecisionRequirementsId()).isNotNull();
    assertThat(entity.getDecisionRequirementsKey()).isNotNull();
    assertThat(entity.getElementId()).isEqualTo(elementId);
    assertThat(entity.getElementInstanceKey()).isNotNull();
    assertThat(entity.getEvaluationFailure()).isNotNull();
    assertThat(entity.getEvaluationFailure())
        .containsIgnoringCase("no variable found for name 'amount'");
    assertThat(entity.getEvaluationDate()).isNotNull();
    assertThat(entity.getPosition()).isNotNull();
    assertThat(entity.getProcessDefinitionKey()).isNotNull();
    assertThat(entity.getProcessInstanceKey()).isNotNull();
    assertThat(entity.getBpmnProcessId()).isNotNull();
    assertThat(entity.getResult()).isEqualTo("null");
    assertThat(entity.getEvaluatedOutputs()).isEmpty();
    assertThat(entity.getEvaluatedInputs()).isEmpty();
    assertThat(entity.getRootDecisionDefinitionId()).isNotNull();
    assertThat(entity.getDecisionType()).isEqualTo(DecisionType.DECISION_TABLE);
    assertThat(entity.getRootDecisionName()).isEqualTo(decision2Name);
    assertThat(entity.getRootDecisionDefinitionId()).isNotNull();
    assertThat(entity.getRootDecisionDefinitionId()).isNotEqualTo(entity.getDecisionDefinitionId());
  }

  @Test
  public void testDecisionInstanceEvaluated() throws Exception {
    // given
    final String bpmnProcessId = "process";
    final String demoDecisionId1 = "invoiceClassification";
    final String demoDecisionId2 = "invoiceAssignApprover";
    final String decision1Name = "Invoice Classification";
    final String decision2Name = "Assign Approver Group";

    final String elementId = "task";
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess(bpmnProcessId)
            .startEvent()
            .businessRuleTask(
                elementId,
                task ->
                    task.zeebeCalledDecisionId(demoDecisionId2)
                        .zeebeResultVariable("approverGroups"))
            .done();

    tester
        .deployProcess(instance, "test.bpmn")
        .deployDecision("invoiceBusinessDecisions_v_1.dmn")
        .waitUntil()
        .processIsDeployed()
        .and()
        .decisionsAreDeployed(2)
        // when
        .startProcessInstance(bpmnProcessId, "{\"amount\": 100, \"invoiceCategory\": \"Misc\"}")
        .waitUntil()
        .decisionInstancesAreCreated(2);

    // then
    final List<DecisionInstanceEntity> decisionEntities =
        testSearchRepository.searchAll(
            decisionInstanceTemplate.getAlias(), DecisionInstanceEntity.class);

    assertThat(decisionEntities).hasSize(2);
    decisionEntities.forEach(
        entity -> {
          assertThat(entity.getId()).isNotNull();
          assertThat(entity.getKey()).isNotNull();
          assertThat(entity.getExecutionIndex()).isNotNull();
          assertThat(entity.getId())
              .isEqualTo(String.format("%s-%s", entity.getKey(), entity.getExecutionIndex()));
          if (entity.getExecutionIndex().equals(1)) {
            assertThat(entity.getDecisionId()).isEqualTo(demoDecisionId1);
            assertThat(entity.getDecisionName()).isEqualTo(decision1Name);
          } else {
            assertThat(entity.getDecisionId()).isEqualTo(demoDecisionId2);
            assertThat(entity.getDecisionName()).isEqualTo(decision2Name);
          }
          assertThat(entity.getDecisionVersion()).isEqualTo(1);
          assertThat(entity.getState()).isEqualTo(DecisionInstanceState.EVALUATED);
          assertThat(entity.getDecisionDefinitionId()).isNotNull();
          assertThat(entity.getDecisionRequirementsId()).isNotNull();
          assertThat(entity.getDecisionRequirementsKey()).isNotNull();
          assertThat(entity.getElementId()).isEqualTo(elementId);
          assertThat(entity.getElementInstanceKey()).isNotNull();
          assertThat(entity.getEvaluationFailure()).isNull();
          assertThat(entity.getEvaluationDate()).isNotNull();
          assertThat(entity.getPosition()).isNotNull();
          assertThat(entity.getProcessDefinitionKey()).isNotNull();
          assertThat(entity.getProcessInstanceKey()).isNotNull();
          assertThat(entity.getBpmnProcessId()).isNotNull();
          assertThat(entity.getResult()).isNotEmpty();
          assertThat(entity.getEvaluatedOutputs()).isNotEmpty();
          assertThat(entity.getEvaluatedInputs()).isNotEmpty();
          assertThat(entity.getRootDecisionDefinitionId()).isNotNull();
          assertThat(entity.getDecisionType()).isEqualTo(DecisionType.DECISION_TABLE);
          // root decision is one and the same for both instances
          assertThat(entity.getRootDecisionName()).isEqualTo(decision2Name);
          assertThat(entity.getRootDecisionDefinitionId()).isNotNull();
        });
    assertThat(decisionEntities.get(0).getResult())
        .isNotEqualTo(decisionEntities.get(1).getResult());
  }

  @Test
  public void testDecisionInstanceLiteralExpressionImported() throws Exception {
    // given
    final String bpmnProcessId = "process";
    final String demoDecisionId = "literalExpression";
    final String decisionName = "Convert amount to string";

    final String elementId = "task";
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess(bpmnProcessId)
            .startEvent()
            .businessRuleTask(
                elementId,
                task -> task.zeebeCalledDecisionId(demoDecisionId).zeebeResultVariable("amountStr"))
            .done();

    tester
        .deployProcess(instance, "test.bpmn")
        .deployDecision("literalExpression.dmn")
        .waitUntil()
        .processIsDeployed()
        .and()
        .decisionsAreDeployed(1)
        // when
        .startProcessInstance(bpmnProcessId, "{\"amount\": 100, \"invoiceCategory\": \"Misc\"}")
        .waitUntil()
        .decisionInstancesAreCreated(1);

    // then
    final List<DecisionInstanceEntity> decisionEntities =
        testSearchRepository.searchAll(
            decisionInstanceTemplate.getAlias(), DecisionInstanceEntity.class);

    assertThat(decisionEntities).hasSize(1);
    final DecisionInstanceEntity entity = decisionEntities.get(0);
    assertThat(entity.getId()).isNotNull();
    assertThat(entity.getKey()).isNotNull();
    assertThat(entity.getExecutionIndex()).isNotNull();
    assertThat(entity.getId())
        .isEqualTo(String.format("%s-%s", entity.getKey(), entity.getExecutionIndex()));
    assertThat(entity.getDecisionId()).isEqualTo(demoDecisionId);
    assertThat(entity.getDecisionName()).isEqualTo(decisionName);
    assertThat(entity.getDecisionVersion()).isEqualTo(1);
    assertThat(entity.getState()).isEqualTo(DecisionInstanceState.EVALUATED);
    assertThat(entity.getDecisionDefinitionId()).isNotNull();
    assertThat(entity.getDecisionRequirementsId()).isNotNull();
    assertThat(entity.getDecisionRequirementsKey()).isNotNull();
    assertThat(entity.getElementId()).isEqualTo(elementId);
    assertThat(entity.getElementInstanceKey()).isNotNull();
    assertThat(entity.getEvaluationFailure()).isNull();
    assertThat(entity.getEvaluationDate()).isNotNull();
    assertThat(entity.getPosition()).isNotNull();
    assertThat(entity.getProcessDefinitionKey()).isNotNull();
    assertThat(entity.getProcessInstanceKey()).isNotNull();
    assertThat(entity.getBpmnProcessId()).isNotNull();
    assertThat(entity.getResult()).isNotEmpty();
    assertThat(entity.getEvaluatedOutputs()).isEmpty();
    assertThat(entity.getEvaluatedInputs()).isEmpty();
    assertThat(entity.getRootDecisionDefinitionId()).isNotNull();
    assertThat(entity.getDecisionType()).isEqualTo(DecisionType.LITERAL_EXPRESSION);
    assertThat(entity.getRootDecisionName()).isEqualTo(decisionName);
    assertThat(entity.getRootDecisionDefinitionId()).isNotNull();
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
