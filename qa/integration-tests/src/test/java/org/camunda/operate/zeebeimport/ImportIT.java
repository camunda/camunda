/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.util.ThreadUtil.sleepFor;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import java.util.stream.Collectors;
import org.camunda.operate.entities.FlowNodeInstanceEntity;
import org.camunda.operate.entities.FlowNodeState;
import org.camunda.operate.entities.FlowNodeType;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import org.camunda.operate.entities.listview.ProcessInstanceState;
import org.camunda.operate.webapp.es.reader.FlowNodeInstanceReader;
import org.camunda.operate.webapp.es.reader.IncidentReader;
import org.camunda.operate.webapp.es.reader.ListViewReader;
import org.camunda.operate.webapp.es.reader.ProcessInstanceReader;
import org.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import org.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import org.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import org.camunda.operate.webapp.rest.dto.listview.VariablesQueryDto;
import org.camunda.operate.webapp.rest.dto.listview.ProcessInstanceStateDto;
import org.camunda.operate.webapp.rest.exception.NotFoundException;
import org.camunda.operate.util.ConversionUtils;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.TestUtil;
import org.camunda.operate.util.ZeebeTestUtil;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;

public class ImportIT extends OperateZeebeIntegrationTest {

  @Autowired
  private ProcessInstanceReader processInstanceReader;

  @Autowired
  private FlowNodeInstanceReader flowNodeInstanceReader;

  @Autowired
  private IncidentReader incidentReader;

  @Autowired
  private ListViewReader listViewReader;

  @Test
  public void testProcessInstanceCreated() {
    // having
    String processId = "demoProcess";
    final Long processDefinitionKey = deployProcess("demoProcess_v_1.bpmn");

    //when
    final Long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "taskA");

    //then
    final ProcessInstanceForListViewEntity processInstanceEntity = processInstanceReader.getProcessInstanceByKey(processInstanceKey);
    assertThat(processInstanceEntity.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(processInstanceEntity.getProcessName()).isEqualTo("Demo process");
    assertThat(processInstanceEntity.getProcessVersion()).isEqualTo(1);
    assertThat(processInstanceEntity.getId()).isEqualTo(processInstanceKey.toString());
    assertThat(processInstanceEntity.getKey()).isEqualTo(processInstanceKey);
    assertThat(processInstanceEntity.getState()).isEqualTo(ProcessInstanceState.ACTIVE);
    assertThat(processInstanceEntity.getEndDate()).isNull();
    assertThat(processInstanceEntity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(processInstanceEntity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());

    //assert list view data
    final ListViewProcessInstanceDto wi = getSingleProcessInstanceForListView();
    assertThat(wi.getProcessId()).isEqualTo(ConversionUtils.toStringOrNull(processDefinitionKey));
    assertThat(wi.getProcessName()).isEqualTo("Demo process");
    assertThat(wi.getProcessVersion()).isEqualTo(1);
    assertThat(wi.getId()).isEqualTo(processInstanceKey.toString());
    assertThat(wi.getState()).isEqualTo(ProcessInstanceStateDto.ACTIVE);
    assertThat(wi.getEndDate()).isNull();
    assertThat(wi.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(wi.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());

    //assert flow node instances
    final List<FlowNodeInstanceEntity> allFlowNodeInstances = tester
        .getAllFlowNodeInstances(processInstanceKey);
    assertThat(allFlowNodeInstances).hasSize(2);
    assertStartFlowNodeCompleted(allFlowNodeInstances.get(0));
    assertFlowNodeIsActive(allFlowNodeInstances.get(1), "taskA");
  }

  @Test
  public void testVariablesAreLoaded() {
    // having
    String processId = "demoProcess";
    deployProcess("demoProcess_v_1.bpmn");

    //when TC 1
    final Long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "taskA");
    elasticsearchTestRule.processAllRecordsAndWait(variableExistsCheck, processInstanceKey, processInstanceKey, "a");

    //then we can find the instance by 2 variable values: a = b, foo = b
    assertVariableExists(processInstanceKey, "a", "\"b\"");
    assertVariableExists(processInstanceKey, "foo", "\"b\"");
    assertVariableDoesNotExist(processInstanceKey, "a", "\"c\"");

    //when TC 2
    //update variable
    ZeebeTestUtil.updateVariables(zeebeClient, processInstanceKey, "{\"a\": \"c\"}");
    //elasticsearchTestRule.processAllEvents(2, ImportValueType.VARIABLE);
    elasticsearchTestRule.processAllRecordsAndWait(variableEqualsCheck, processInstanceKey,processInstanceKey,"a","\"c\"");
    //then we can find the instance by 2 variable values: foo = b
    assertVariableDoesNotExist(processInstanceKey, "a", "\"b\"");
    assertVariableExists(processInstanceKey, "foo", "\"b\"");
    assertVariableExists(processInstanceKey, "a", "\"c\"");
  }

  private void assertVariableExists(Long processInstanceKey, String name, String value) {
    ListViewProcessInstanceDto wi = getSingleProcessInstanceForListView(
      TestUtil.createGetAllProcessInstancesRequest(q -> {
        q.setVariable(new VariablesQueryDto(name, value));
      }));
    assertThat(wi.getId()).isEqualTo(processInstanceKey.toString());
  }

  private void assertVariableDoesNotExist(Long processInstanceKey, String name, String value) {
    final ListViewRequestDto request = TestUtil
        .createGetAllProcessInstancesRequest(q ->
            q.setVariable(new VariablesQueryDto(name, value)));
    request.setPageSize(100);
    final ListViewResponseDto listViewResponse = listViewReader.queryProcessInstances(
        request);
    assertThat(listViewResponse.getTotalCount()).isEqualTo(0);
    assertThat(listViewResponse.getProcessInstances()).hasSize(0);
  }

  private ListViewProcessInstanceDto getSingleProcessInstanceForListView(ListViewRequestDto request) {
    final ListViewResponseDto listViewResponse = listViewReader.queryProcessInstances(request);
    assertThat(listViewResponse.getTotalCount()).isEqualTo(1);
    assertThat(listViewResponse.getProcessInstances()).hasSize(1);
    return listViewResponse.getProcessInstances().get(0);
  }

  private ListViewProcessInstanceDto getSingleProcessInstanceForListView() {
    return getSingleProcessInstanceForListView(TestUtil.createGetAllProcessInstancesRequest());
  }

  @Test
  public void testProcessInstanceAndActivityCompleted() {
    // having
    String processId = "demoProcess";
    BpmnModelInstance process = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
        .serviceTask("task1").zeebeJobType("task1")
      .endEvent()
      .done();
    deployProcess(process, "demoProcess_v_1.bpmn");

    //when
    final long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, null);
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "task1");

    completeTask(processInstanceKey, "task1", null);

    elasticsearchTestRule.processAllRecordsAndWait(processInstanceIsCompletedCheck, processInstanceKey);

    //then
    final ProcessInstanceForListViewEntity processInstanceEntity = processInstanceReader.getProcessInstanceByKey(processInstanceKey);
    assertThat(processInstanceEntity.getState()).isEqualTo(ProcessInstanceState.COMPLETED);

    //assert list view data
    final ListViewProcessInstanceDto wi = getSingleProcessInstanceForListView();
    assertThat(wi.getState()).isEqualTo(ProcessInstanceStateDto.COMPLETED);

    //assert flow node instances
    final List<FlowNodeInstanceEntity> allFlowNodeInstances = tester
        .getAllFlowNodeInstances(processInstanceKey);
    assertThat(allFlowNodeInstances).hasSize(3);
    final FlowNodeInstanceEntity activity = allFlowNodeInstances.get(1);
    assertThat(activity.getState()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(activity.getEndDate()).isAfterOrEqualTo(testStartTime);
  }

  @Test
  public void testProcessInstanceStartTimeDoesNotChange() {
    // having
    String processId = "demoProcess";
    BpmnModelInstance process = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
        .serviceTask("task1").zeebeJobType("task1")
      .endEvent()
      .done();
    deployProcess(process, "demoProcess_v_1.bpmn");
    final long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, null);
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "task1");
    //remember start date
    final OffsetDateTime startDate = processInstanceReader.getProcessInstanceByKey(processInstanceKey).getStartDate();

    //when
    completeTask(processInstanceKey, "task1", null);
    elasticsearchTestRule.processAllRecordsAndWait(processInstanceIsCompletedCheck, processInstanceKey);

    //then
    final ProcessInstanceForListViewEntity processInstanceEntity = processInstanceReader.getProcessInstanceByKey(processInstanceKey);
    assertThat(processInstanceEntity.getState()).isEqualTo(ProcessInstanceState.COMPLETED);
    //assert start date did not change
    assertThat(processInstanceEntity.getStartDate()).isEqualTo(startDate);

    //assert list view data
    final ListViewProcessInstanceDto wi = getSingleProcessInstanceForListView();
    assertThat(wi.getStartDate()).isEqualTo(startDate);

  }

  @Test
  public void testIncidentDeleted() {
    // having
    String activityId = "taskA";

    String processId = "demoProcess";
    deployProcess("demoProcess_v_1.bpmn");
    final long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"a\": \"b\"}");

    //create an incident
    failTaskWithNoRetriesLeft(activityId, processInstanceKey, "Some error");

    //when update retries
    List<IncidentEntity> allIncidents = incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey);
    assertThat(allIncidents).hasSize(1);
    ZeebeTestUtil.resolveIncident(zeebeClient, allIncidents.get(0).getJobKey(), allIncidents.get(0).getKey());
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsResolvedCheck, processInstanceKey);

    //then
    allIncidents = incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey);
    assertThat(allIncidents).hasSize(0);

    //assert list view data
    final ListViewProcessInstanceDto wi = getSingleProcessInstanceForListView();
    assertThat(wi.getState()).isEqualTo(ProcessInstanceStateDto.ACTIVE);

    //assert flow node instances
    final List<FlowNodeInstanceEntity> allFlowNodeInstances = tester
        .getAllFlowNodeInstances(processInstanceKey);
    assertThat(allFlowNodeInstances).hasSize(2);
    final FlowNodeInstanceEntity activity = allFlowNodeInstances.get(1);
    assertThat(activity.getState()).isEqualTo(FlowNodeState.ACTIVE);
    assertThat(activity.getFlowNodeId()).isEqualTo(activityId);
  }

  @Test
  public void testProcessInstanceWithIncidentCreated() {
    // having
    String activityId = "taskA";
    final String errorMessage = "Error occurred when working on the job";

    String processId = "demoProcess";
    final Long processDefinitionKey = deployProcess("demoProcess_v_1.bpmn");
    final Long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"a\": \"b\"}");

    //when
    //create an incident
    failTaskWithNoRetriesLeft(activityId, processInstanceKey, errorMessage);

    //then
    final List<IncidentEntity> allIncidents = incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey);
    assertThat(allIncidents).hasSize(1);
    IncidentEntity incidentEntity = allIncidents.get(0);
    assertThat(incidentEntity.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(incidentEntity.getFlowNodeId()).isEqualTo(activityId);
    assertThat(incidentEntity.getFlowNodeInstanceKey()).isNotNull();
    assertThat(incidentEntity.getErrorMessage()).isEqualTo(errorMessage);
    assertThat(incidentEntity.getErrorType()).isNotNull();
    assertThat(incidentEntity.getState()).isEqualTo(IncidentState.ACTIVE);

    //assert list view data
    final ListViewProcessInstanceDto wi = getSingleProcessInstanceForListView();
    assertThat(wi.getState()).isEqualTo(ProcessInstanceStateDto.INCIDENT);

    //assert flow node instances
    final List<FlowNodeInstanceEntity> allFlowNodeInstances = tester
        .getAllFlowNodeInstances(processInstanceKey);
    assertThat(allFlowNodeInstances).hasSize(2);
    final FlowNodeInstanceEntity activity = allFlowNodeInstances.get(1);
    assertThat(activity.getState()).isEqualTo(FlowNodeState.ACTIVE);
    assertThat(activity.getIncidentKey()).isNotNull();
    assertThat(activity.getFlowNodeId()).isEqualTo(activityId);
  }

  @Test
  public void testProcessInstanceWithIncidentOnGateway() {
    // having
    String activityId = "xor";

    String processId = "demoProcess";
    BpmnModelInstance process = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
        .exclusiveGateway(activityId)
        .sequenceFlowId("s1").condition("=foo < 5")
          .serviceTask("task1").zeebeJobType("task1")
          .endEvent()
        .moveToLastGateway()
        .sequenceFlowId("s2").condition("=foo >= 5")
          .serviceTask("task2").zeebeJobType("task2")
          .endEvent()
      .done();
    final String resourceName = processId + ".bpmn";
    final Long processDefinitionKey = deployProcess(process, resourceName);

    //when
    final Long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"a\": \"b\"}");      //wrong payload provokes incident
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, processInstanceKey);

    //then incident created, activity in INCIDENT state
    final List<IncidentEntity> allIncidents = incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey);
    assertThat(allIncidents).hasSize(1);
    IncidentEntity incidentEntity = allIncidents.get(0);
    assertThat(incidentEntity.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(incidentEntity.getFlowNodeId()).isEqualTo(activityId);
    assertThat(incidentEntity.getFlowNodeInstanceKey()).isNotNull();
    assertThat(incidentEntity.getErrorMessage()).isNotEmpty();
    assertThat(incidentEntity.getErrorType()).isNotNull();
    assertThat(incidentEntity.getState()).isEqualTo(IncidentState.ACTIVE);

    //assert list view data
    final ListViewProcessInstanceDto wi = getSingleProcessInstanceForListView();
    assertThat(wi.getState()).isEqualTo(ProcessInstanceStateDto.INCIDENT);

    //assert flow node instances
    final List<FlowNodeInstanceEntity> allFlowNodeInstances = tester
        .getAllFlowNodeInstances(processInstanceKey);
    assertThat(allFlowNodeInstances).hasSize(2);
    final FlowNodeInstanceEntity activity = allFlowNodeInstances.get(1);
    assertThat(activity.getState()).isEqualTo(FlowNodeState.ACTIVE);
    assertThat(activity.getIncidentKey()).isNotNull();
    assertThat(activity.getFlowNodeId()).isEqualTo(activityId);

    //when payload updated
//TODO    ZeebeUtil.updateVariables(zeebeClient, gatewayActivity.getKey(), processInstanceKey, "{\"foo\": 7}", processId, processId);
//    elasticsearchTestRule.processAllEvents(5);

    //then incident is resolved
//TODO    processInstanceEntity = processInstanceReader.getProcessInstanceByKey(processInstancekey);
//    assertThat(processInstanceEntity.getIncidents().size()).isEqualTo(1);
//    incidentEntity = processInstanceEntity.getIncidents().get(0);
//    assertThat(incidentEntity.getElementId()).isEqualTo(activityId);
//    assertThat(incidentEntity.getState()).isEqualTo(IncidentState.RESOLVED);

    //assert activity fields
//TODO    final ActivityInstanceEntity xorActivity = processInstanceEntity.getActivities().stream().filter(a -> a.getElementId().equals("xor"))
//      .findFirst().get();
//    assertThat(xorActivity.getState()).isEqualTo(ActivityState.COMPLETED);
//    assertThat(xorActivity.getEndDate()).isNotNull();
  }

  @Test
  public void testProcessInstanceWithIncidentOnGatewayIsCanceled() {
    // having
    String activityId = "xor";

    String processId = "demoProcess";
    BpmnModelInstance process = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
        .exclusiveGateway(activityId)
        .sequenceFlowId("s1").condition("=foo < 5")
          .serviceTask("task1").zeebeJobType("task1")
          .endEvent()
        .moveToLastGateway()
        .sequenceFlowId("s2").condition("=foo >= 5")
          .serviceTask("task2").zeebeJobType("task2")
          .endEvent()
      .done();
    final String resourceName = processId + ".bpmn";
    final Long processDefinitionKey = deployProcess(process, resourceName);

    //when
    final Long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"a\": \"b\"}");      //wrong payload provokes incident
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, processInstanceKey);

    //then incident created, activity in INCIDENT state
    List<IncidentEntity> allIncidents = incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey);
    assertThat(allIncidents).hasSize(1);
    IncidentEntity incidentEntity = allIncidents.get(0);
    assertThat(incidentEntity.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(incidentEntity.getState()).isEqualTo(IncidentState.ACTIVE);

    //when I cancel process instance
    ZeebeTestUtil.cancelProcessInstance(zeebeClient, processInstanceKey);
    elasticsearchTestRule.processAllRecordsAndWait(processInstanceIsCanceledCheck, processInstanceKey);
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsResolvedCheck, processInstanceKey);

    //then incident is deleted
    ProcessInstanceForListViewEntity processInstanceEntity = processInstanceReader.getProcessInstanceByKey(processInstanceKey);
    assertThat(processInstanceEntity.getState()).isEqualTo(ProcessInstanceState.CANCELED);
    allIncidents = incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey);
    assertThat(allIncidents).hasSize(0);

    //assert list view data
    final ListViewProcessInstanceDto wi = getSingleProcessInstanceForListView();
    assertThat(wi.getState()).isEqualTo(ProcessInstanceStateDto.CANCELED);

    //assert flow node instances
    final List<FlowNodeInstanceEntity> allFlowNodeInstances = tester
        .getAllFlowNodeInstances(processInstanceKey);
    assertThat(allFlowNodeInstances).hasSize(2);
    final FlowNodeInstanceEntity activity = allFlowNodeInstances.get(1);
    assertThat(activity.getState()).isEqualTo(FlowNodeState.TERMINATED);
    assertThat(activity.getFlowNodeId()).isEqualTo(activityId);
    assertThat(activity.getEndDate()).isNotNull();
  }

  @Test
  public void testProcessInstanceGatewayIsPassed() {
    // having
    String activityId = "xor";

    String processId = "demoProcess";
    BpmnModelInstance process = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
        .exclusiveGateway(activityId)
        .sequenceFlowId("s1").condition("=foo < 5")
          .serviceTask("task1").zeebeJobType("task1")
          .endEvent()
        .moveToLastGateway()
        .sequenceFlowId("s2").condition("=foo >= 5")
          .serviceTask("task2").zeebeJobType("task2")
          .endEvent()
      .done();
    final String resourceName = processId + ".bpmn";
    deployProcess(process, resourceName);

    //when
    final long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"foo\": 6}");
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "task2");

    //assert flow node instances
    final List<FlowNodeInstanceEntity> allFlowNodeInstances = tester
        .getAllFlowNodeInstances(processInstanceKey);
    assertThat(allFlowNodeInstances.size()).isGreaterThanOrEqualTo(2);
    final FlowNodeInstanceEntity activity = allFlowNodeInstances.get(1);
    assertThat(activity.getState()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(activity.getFlowNodeId()).isEqualTo(activityId);
    assertThat(activity.getEndDate()).isNotNull();
  }

  @Test
  public void testProcessInstanceEventBasedGatewayIsActive() {
    // having
    String activityId = "gateway";

    String processId = "demoProcess";
    BpmnModelInstance process = Bpmn.createExecutableProcess(processId)
      .startEvent()
      .eventBasedGateway(activityId)
      .intermediateCatchEvent(
        "msg-1", i -> i.message(m -> m.name("msg-1").zeebeCorrelationKey("=key1")))
      .endEvent()
      .moveToLastGateway()
      .intermediateCatchEvent(
        "msg-2", i -> i.message(m -> m.name("msg-2").zeebeCorrelationKey("=key2")))
      .endEvent()
      .done();
    final String resourceName = processId + ".bpmn";
    deployProcess(process, resourceName);

    //when
    final long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"key1\": \"value1\", \"key2\": \"value2\"}");
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "gateway");

    //assert flow node instances
    final List<FlowNodeInstanceEntity> allFlowNodeInstances = tester
        .getAllFlowNodeInstances(processInstanceKey);
    assertThat(allFlowNodeInstances).hasSize(2);
    final FlowNodeInstanceEntity activity = allFlowNodeInstances.get(1);
    assertThat(activity.getState()).isEqualTo(FlowNodeState.ACTIVE);
    assertThat(activity.getFlowNodeId()).isEqualTo(activityId);
    assertThat(activity.getEndDate()).isNull();

  }

  @Test
  public void testProcessInstanceCanceled() {
    // having
    final OffsetDateTime testStartTime = OffsetDateTime.now();

    String processId = "demoProcess";
    deployProcess("demoProcess_v_1.bpmn");
    final Long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "taskA");

    //when
    cancelProcessInstance(processInstanceKey);

    //then
    final ProcessInstanceForListViewEntity processInstanceEntity = processInstanceReader.getProcessInstanceByKey(processInstanceKey);
    assertThat(processInstanceEntity.getId()).isEqualTo(processInstanceKey.toString());
    assertThat(processInstanceEntity.getKey()).isEqualTo(processInstanceKey);
    assertThat(processInstanceEntity.getState()).isEqualTo(ProcessInstanceState.CANCELED);
    assertThat(processInstanceEntity.getEndDate()).isNotNull();
    assertThat(processInstanceEntity.getEndDate()).isAfterOrEqualTo(testStartTime);
    assertThat(processInstanceEntity.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());

    //assert list view data
    final ListViewProcessInstanceDto wi = getSingleProcessInstanceForListView();
    assertThat(wi.getState()).isEqualTo(ProcessInstanceStateDto.CANCELED);
    assertThat(wi.getId()).isEqualTo(processInstanceKey.toString());
    assertThat(wi.getEndDate()).isNotNull();
    assertThat(wi.getEndDate()).isAfterOrEqualTo(testStartTime);
    assertThat(wi.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());

    //assert flow node instances
    final List<FlowNodeInstanceEntity> allFlowNodeInstances = tester
        .getAllFlowNodeInstances(processInstanceKey);
    assertThat(allFlowNodeInstances).hasSize(2);
    final FlowNodeInstanceEntity activity = allFlowNodeInstances.get(1);
    assertThat(activity.getState()).isEqualTo(FlowNodeState.TERMINATED);
    assertThat(activity.getEndDate()).isNotNull();
    assertThat(activity.getEndDate()).isAfterOrEqualTo(testStartTime);
    assertThat(activity.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());

  }

  @Test
  public void testProcessInstanceCanceledOnMessageEvent() {
    // having
    final OffsetDateTime testStartTime = OffsetDateTime.now();

    String processId = "eventProcess";
    deployProcess("messageEventProcess_v_1.bpmn");
//    final long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    final Long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"clientId\": \"5\"}");
    sleepFor(1000);

    //when
    cancelProcessInstance(processInstanceKey);

    //then
    final ProcessInstanceForListViewEntity processInstanceEntity = processInstanceReader.getProcessInstanceByKey(processInstanceKey);
    assertThat(processInstanceEntity.getId()).isEqualTo(processInstanceKey.toString());
    assertThat(processInstanceEntity.getKey()).isEqualTo(processInstanceKey);
    assertThat(processInstanceEntity.getState()).isEqualTo(ProcessInstanceState.CANCELED);
    assertThat(processInstanceEntity.getEndDate()).isNotNull();
    assertThat(processInstanceEntity.getEndDate()).isAfterOrEqualTo(testStartTime);
    assertThat(processInstanceEntity.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());

    //assert list view data
    final ListViewProcessInstanceDto wi = getSingleProcessInstanceForListView();
    assertThat(wi.getState()).isEqualTo(ProcessInstanceStateDto.CANCELED);
    assertThat(wi.getId()).isEqualTo(processInstanceKey.toString());
    assertThat(wi.getEndDate()).isNotNull();
    assertThat(wi.getEndDate()).isAfterOrEqualTo(testStartTime);
    assertThat(wi.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());

    //assert flow node instances
    final List<FlowNodeInstanceEntity> allFlowNodeInstances = tester
        .getAllFlowNodeInstances(processInstanceKey);
    assertThat(allFlowNodeInstances).hasSize(2);
    final FlowNodeInstanceEntity activity = allFlowNodeInstances.get(1);
    assertThat(activity.getState()).isEqualTo(FlowNodeState.TERMINATED);
    assertThat(activity.getEndDate()).isNotNull();
    assertThat(activity.getEndDate()).isAfterOrEqualTo(testStartTime);
    assertThat(activity.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());

  }

  @Test
  public void testMessageEventPassed() {
    // having
    final OffsetDateTime testStartTime = OffsetDateTime.now();

    String processId = "eventProcess";
    deployProcess("messageEventProcess_v_1.bpmn");
    final Long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"clientId\": \"5\"}");
    sleepFor(1000);

    //when
    sendMessages("clientMessage", "{\"messageVar\": \"someValue\"}", 20);
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "taskA");

    //assert flow node instances
    final List<FlowNodeInstanceEntity> allFlowNodeInstances = tester
        .getAllFlowNodeInstances(processInstanceKey);
    assertThat(allFlowNodeInstances).hasSize(3);
    final FlowNodeInstanceEntity activity = allFlowNodeInstances.get(1);
    assertThat(activity.getState()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(activity.getEndDate()).isNotNull();
    assertThat(activity.getEndDate()).isAfterOrEqualTo(testStartTime);
    assertThat(activity.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());

  }

  private void sendMessages(String messageName, String payload, int count, String correlationKey) {
    for (int i = 0; i<count; i++) {
      zeebeClient.newPublishMessageCommand()
          .messageName(messageName)
          .correlationKey(correlationKey)
          .variables(payload)
          .timeToLive(Duration.ofSeconds(30))
          .messageId(UUID.randomUUID().toString())
          .send().join();
    }
  }
  private void sendMessages(String messageName, String payload, int count) {
    sendMessages(messageName, payload, count, String.valueOf(5));
  }

  @Test
  public void testProcessInstanceById() {
    String processId = "demoProcess";
    deployProcess("demoProcess_v_1.bpmn");
    final long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    elasticsearchTestRule.processAllRecordsAndWait(processInstanceIsCreatedCheck, processInstanceKey);

    final ProcessInstanceForListViewEntity processInstanceById = processInstanceReader.getProcessInstanceByKey(processInstanceKey);
    assertThat(processInstanceById).isNotNull();
    assertThat(processInstanceById.getState()).isEqualTo(ProcessInstanceState.ACTIVE);
  }

  @Test
  public void testProcessInstanceWithIncidentById() {
    String activityId = "taskA";
    final String errorMessage = "Error occurred when working on the job";
    String processId = "demoProcess";
    deployProcess("demoProcess_v_1.bpmn");
    final long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    elasticsearchTestRule.processAllRecordsAndWait(processInstanceIsCreatedCheck, processInstanceKey);

    //create an incident
    failTaskWithNoRetriesLeft(activityId, processInstanceKey, errorMessage);
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, processInstanceKey);

    final ProcessInstanceForListViewEntity processInstanceById = processInstanceReader.getProcessInstanceByKey(processInstanceKey);
    assertThat(processInstanceById).isNotNull();
    assertThat(processInstanceById.getState()).isEqualTo(ProcessInstanceState.INCIDENT);
  }

  @Test
  public void testEventSubprocess() {
    // having
    final OffsetDateTime testStartTime = OffsetDateTime.now();

    String processId = "eventSubProcess";
    deployProcess("eventSubProcess.bpmn");
    final Long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, null);
    sleepFor(5_000);

    //when
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsTerminatedCheck, processInstanceKey, "taskA");

    //assert flow node instances
    final List<FlowNodeInstanceEntity> allFlowNodeInstances = tester
        .getAllFlowNodeInstances(processInstanceKey);
    assertThat(allFlowNodeInstances).hasSize(5);
    final List<FlowNodeInstanceEntity> eventSP = allFlowNodeInstances.stream()
        .filter(fn -> fn.getType().equals(FlowNodeType.EVENT_SUB_PROCESS))
        .collect(Collectors.toList());
    assertThat(eventSP).hasSize(1);
  }

  @Test(expected = NotFoundException.class)
  public void testProcessInstanceByIdFailForUnknownId() {
    String processId = "demoProcess";
    deployProcess("demoProcess_v_1.bpmn");
    final long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    elasticsearchTestRule.processAllRecordsAndWait(processInstanceIsCreatedCheck, processInstanceKey);

    /*final ProcessInstanceForListViewEntity processInstanceById =*/ processInstanceReader.getProcessInstanceByKey(-42L);
  }

  private void assertStartFlowNodeCompleted(FlowNodeInstanceEntity startActivity) {
    assertThat(startActivity.getFlowNodeId()).isEqualTo("start");
    assertThat(startActivity.getState()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(startActivity.getType()).isEqualTo(FlowNodeType.START_EVENT);
    assertThat(startActivity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(startActivity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());
    assertThat(startActivity.getEndDate()).isAfterOrEqualTo(startActivity.getStartDate());
    assertThat(startActivity.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());
  }

  private void assertFlowNodeIsActive(FlowNodeInstanceEntity flowNodeEntity, String flowNodeId) {
    assertThat(flowNodeEntity.getFlowNodeId()).isEqualTo(flowNodeId);
    assertThat(flowNodeEntity.getState()).isEqualTo(FlowNodeState.ACTIVE);
    assertThat(flowNodeEntity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(flowNodeEntity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());
    assertThat(flowNodeEntity.getEndDate()).isNull();
  }

}
