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
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.store.SequenceFlowStore;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.util.ZeebeTestUtil;
import io.camunda.operate.util.searchrepository.TestSearchRepository;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.reader.IncidentReader;
import io.camunda.operate.webapp.reader.ListViewReader;
import io.camunda.operate.webapp.rest.dto.ProcessInstanceReferenceDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import io.camunda.operate.webapp.rest.dto.listview.ProcessInstanceStateDto;
import io.camunda.operate.webapp.rest.dto.listview.VariablesQueryDto;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.webapps.schema.descriptors.operate.template.VariableTemplate;
import io.camunda.webapps.schema.entities.SequenceFlowEntity;
import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.webapps.schema.entities.flownode.FlowNodeType;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.incident.IncidentState;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.web.servlet.MvcResult;

public class ProcessInstanceZeebeImportIT extends OperateZeebeAbstractIT {

  @Autowired private ProcessInstanceReader processInstanceReader;

  @Autowired private IncidentReader incidentReader;

  @Autowired private ListViewReader listViewReader;

  @Autowired private SequenceFlowStore sequenceFlowStore;

  @Autowired private TestSearchRepository testSearchRepository;

  @Autowired
  @Qualifier("operateVariableTemplate")
  private VariableTemplate variableTemplate;

  @Test
  public void testProcessInstanceCreated() {
    // having
    final String processId = "demoProcess";
    final Long processDefinitionKey = deployProcess("demoProcess_v_1.bpmn");

    // when
    final Long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(camundaClient, processId, "{\"a\": \"b\"}");
    searchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "taskA");

    // then
    final ProcessInstanceForListViewEntity processInstanceEntity =
        processInstanceReader.getProcessInstanceByKey(processInstanceKey);
    assertThat(processInstanceEntity.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(processInstanceEntity.getProcessName()).isEqualTo("Demo process");
    assertThat(processInstanceEntity.getProcessVersion()).isEqualTo(1);
    assertThat(processInstanceEntity.getId()).isEqualTo(processInstanceKey.toString());
    assertThat(processInstanceEntity.getKey()).isEqualTo(processInstanceKey);
    assertThat(processInstanceEntity.getState()).isEqualTo(ProcessInstanceState.ACTIVE);
    assertThat(processInstanceEntity.getEndDate()).isNull();
    assertThat(processInstanceEntity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(processInstanceEntity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());

    // assert list view data
    final ListViewProcessInstanceDto pi = getSingleProcessInstanceForListView();
    assertThat(pi.getProcessId()).isEqualTo(ConversionUtils.toStringOrNull(processDefinitionKey));
    assertThat(pi.getProcessName()).isEqualTo("Demo process");
    assertThat(pi.getProcessVersion()).isEqualTo(1);
    assertThat(pi.getId()).isEqualTo(processInstanceKey.toString());
    assertThat(pi.getState()).isEqualTo(ProcessInstanceStateDto.ACTIVE);
    assertThat(pi.getEndDate()).isNull();
    assertThat(pi.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(pi.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());

    // assert flow node instances
    final List<FlowNodeInstanceEntity> allFlowNodeInstances =
        tester.getAllFlowNodeInstances(processInstanceKey);
    assertThat(allFlowNodeInstances).hasSize(2);
    assertStartFlowNodeCompleted(allFlowNodeInstances.get(0));
    assertFlowNodeIsActive(allFlowNodeInstances.get(1), "taskA");
  }

  @Test
  public void processInstanceCreatedIfJobRecordProcessedFirst() {
    final String processId = "Process_Start_Listener";
    final Long processDefinitionKey = deployProcess("process-start-listener.bpmn");

    // when
    final Long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(camundaClient, processId, null);
    searchTestRule.processRecordsWithTypeAndWait(
        ImportValueType.JOB, listenerJobIsCreated, processInstanceKey, processId);
    searchTestRule.processAllRecordsAndWait(processInstanceIsCreatedCheck, processInstanceKey);
    final ListViewProcessInstanceDto dto = getSingleProcessInstanceForListView();
    assertThat(dto.getBpmnProcessId()).isEqualTo(processId);
  }

  @Test
  public void testVariablesAreLoaded() {
    // having
    final String processId = "demoProcess";
    deployProcess("demoProcess_v_1.bpmn");

    // when TC 1
    final Long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(camundaClient, processId, "{\"a\": \"b\"}");
    searchTestRule.processAllRecordsAndWait(processInstanceIsCreatedCheck, processInstanceKey);
    searchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "taskA");
    searchTestRule.processAllRecordsAndWait(variableExistsCheck, processInstanceKey, "a");

    // then we can find the instance by 2 variable values: a = b, foo = b
    assertVariableExists(processInstanceKey, "a", "\"b\"");
    assertVariableExists(processInstanceKey, "foo", "\"b\"");
    assertVariableDoesNotExist(processInstanceKey, "a", "\"c\"");

    // when TC 2
    // update variable
    ZeebeTestUtil.updateVariables(camundaClient, processInstanceKey, "{\"a\": \"c\"}");
    // elasticsearchTestRule.processAllEvents(2, ImportValueType.VARIABLE);
    searchTestRule.processAllRecordsAndWait(
        variableEqualsCheck, processInstanceKey, processInstanceKey, "a", "\"c\"");
    // then we can find the instance by 2 variable values: foo = b
    assertVariableDoesNotExist(processInstanceKey, "a", "\"b\"");
    assertVariableExists(processInstanceKey, "foo", "\"b\"");
    assertVariableExists(processInstanceKey, "a", "\"c\"");
  }

  private void assertVariableExists(
      final Long processInstanceKey, final String name, final String value) {
    final ListViewProcessInstanceDto pi =
        getSingleProcessInstanceForListView(
            createGetAllProcessInstancesRequest(
                q -> {
                  q.setVariable(new VariablesQueryDto(name, value));
                }));
    assertThat(pi.getId()).isEqualTo(processInstanceKey.toString());
  }

  private void assertVariableDoesNotExist(
      final Long processInstanceKey, final String name, final String value) {
    final ListViewRequestDto request =
        createGetAllProcessInstancesRequest(q -> q.setVariable(new VariablesQueryDto(name, value)));
    request.setPageSize(100);
    final ListViewResponseDto listViewResponse = listViewReader.queryProcessInstances(request);
    assertThat(listViewResponse.getTotalCount()).isEqualTo(0);
    assertThat(listViewResponse.getProcessInstances()).hasSize(0);
  }

  private ListViewProcessInstanceDto getSingleProcessInstanceForListView(
      final ListViewRequestDto request) {
    final ListViewResponseDto listViewResponse = listViewReader.queryProcessInstances(request);
    assertThat(listViewResponse.getTotalCount()).isEqualTo(1);
    assertThat(listViewResponse.getProcessInstances()).hasSize(1);
    return listViewResponse.getProcessInstances().get(0);
  }

  private ListViewProcessInstanceDto getSingleProcessInstanceForListView() {
    return getSingleProcessInstanceForListView(createGetAllProcessInstancesRequest());
  }

  @Test
  public void testProcessInstanceAndActivityCompleted() {
    // having
    final String processId = "demoProcess";
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .serviceTask("task1")
            .zeebeJobType("task1")
            .endEvent()
            .done();
    deployProcess(process, "demoProcess_v_1.bpmn");

    // when
    final long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(camundaClient, processId, null);
    searchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "task1");

    completeTask(processInstanceKey, "task1", null);

    searchTestRule.processAllRecordsAndWait(processInstanceIsCompletedCheck, processInstanceKey);

    // then
    final ProcessInstanceForListViewEntity processInstanceEntity =
        processInstanceReader.getProcessInstanceByKey(processInstanceKey);
    assertThat(processInstanceEntity.getState()).isEqualTo(ProcessInstanceState.COMPLETED);

    // assert list view data
    final ListViewProcessInstanceDto pi = getSingleProcessInstanceForListView();
    assertThat(pi.getState()).isEqualTo(ProcessInstanceStateDto.COMPLETED);

    // assert flow node instances
    final List<FlowNodeInstanceEntity> allFlowNodeInstances =
        tester.getAllFlowNodeInstances(processInstanceKey);
    assertThat(allFlowNodeInstances).hasSize(3);
    final FlowNodeInstanceEntity activity = allFlowNodeInstances.get(1);
    assertThat(activity.getState()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(activity.getEndDate()).isAfterOrEqualTo(testStartTime);
  }

  @Test
  public void testProcessInstanceStartTimeDoesNotChange() {
    // having
    final String processId = "demoProcess";
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .serviceTask("task1")
            .zeebeJobType("task1")
            .endEvent()
            .done();
    deployProcess(process, "demoProcess_v_1.bpmn");
    final long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(camundaClient, processId, null);
    searchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "task1");
    // remember start date
    final OffsetDateTime startDate =
        processInstanceReader.getProcessInstanceByKey(processInstanceKey).getStartDate();

    // when
    completeTask(processInstanceKey, "task1", null);
    searchTestRule.processAllRecordsAndWait(processInstanceIsCompletedCheck, processInstanceKey);

    // then
    final ProcessInstanceForListViewEntity processInstanceEntity =
        processInstanceReader.getProcessInstanceByKey(processInstanceKey);
    assertThat(processInstanceEntity.getState()).isEqualTo(ProcessInstanceState.COMPLETED);
    // assert start date did not change
    assertThat(processInstanceEntity.getStartDate()).isEqualTo(startDate);

    // assert list view data
    final ListViewProcessInstanceDto pi = getSingleProcessInstanceForListView();
    assertThat(pi.getStartDate()).isEqualTo(startDate);
  }

  @Test
  public void testIncidentDeleted() {
    // having
    final String activityId = "taskA";

    final String processId = "demoProcess";
    deployProcess("demoProcess_v_1.bpmn");
    final long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(camundaClient, processId, "{\"a\": \"b\"}");

    // create an incident
    failTaskWithNoRetriesLeft(activityId, processInstanceKey, "Some error");

    // when update retries
    List<IncidentEntity> allIncidents =
        incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey);
    assertThat(allIncidents).hasSize(1);
    ZeebeTestUtil.resolveIncident(
        camundaClient, allIncidents.get(0).getJobKey(), allIncidents.get(0).getKey());
    searchTestRule.processAllRecordsAndWait(noActivitiesHaveIncident, processInstanceKey);

    // then
    allIncidents = incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey);
    assertThat(allIncidents).hasSize(0);

    // assert list view data
    final ListViewProcessInstanceDto pi = getSingleProcessInstanceForListView();
    assertThat(pi.getState()).isEqualTo(ProcessInstanceStateDto.ACTIVE);

    // assert flow node instances
    final List<FlowNodeInstanceEntity> allFlowNodeInstances =
        tester.getAllFlowNodeInstances(processInstanceKey);
    assertThat(allFlowNodeInstances).hasSize(2);
    final FlowNodeInstanceEntity activity = allFlowNodeInstances.get(1);
    assertThat(activity.getState()).isEqualTo(FlowNodeState.ACTIVE);
    assertThat(activity.getFlowNodeId()).isEqualTo(activityId);
  }

  @Test
  public void testProcessInstanceWithIncidentCreated() {
    // having
    final String activityId = "taskA";
    final String errorMessage = "Error occurred when working on the job";

    final String processId = "demoProcess";
    final Long processDefinitionKey = deployProcess("demoProcess_v_1.bpmn");
    final Long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(camundaClient, processId, "{\"a\": \"b\"}");

    // when
    // create an incident
    failTaskWithNoRetriesLeft(activityId, processInstanceKey, errorMessage);

    // then
    final List<IncidentEntity> allIncidents =
        incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey);
    assertThat(allIncidents).hasSize(1);
    final IncidentEntity incidentEntity = allIncidents.get(0);
    assertThat(incidentEntity.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(incidentEntity.getBpmnProcessId()).isEqualTo(processId);
    assertThat(incidentEntity.getFlowNodeId()).isEqualTo(activityId);
    assertThat(incidentEntity.getFlowNodeInstanceKey()).isNotNull();
    assertThat(incidentEntity.getErrorMessage()).isEqualTo(errorMessage);
    assertThat(incidentEntity.getErrorType()).isNotNull();
    assertThat(incidentEntity.getState()).isEqualTo(IncidentState.ACTIVE);

    // assert list view data
    final ListViewProcessInstanceDto pi = getSingleProcessInstanceForListView();
    assertThat(pi.getState()).isEqualTo(ProcessInstanceStateDto.INCIDENT);

    // assert flow node instances
    final List<FlowNodeInstanceEntity> allFlowNodeInstances =
        tester.getAllFlowNodeInstances(processInstanceKey);
    assertThat(allFlowNodeInstances).hasSize(2);
    final FlowNodeInstanceEntity activity = allFlowNodeInstances.get(1);
    assertThat(activity.getState()).isEqualTo(FlowNodeState.ACTIVE);
    assertThat(activity.getIncidentKey()).isNotNull();
    assertThat(activity.getFlowNodeId()).isEqualTo(activityId);
  }

  @Test
  public void testProcessInstanceWithIncidentOnGateway() {
    // having
    final String activityId = "xor";

    final String processId = "demoProcess";
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .exclusiveGateway(activityId)
            .sequenceFlowId("s1")
            .condition("=foo < 5")
            .serviceTask("task1")
            .zeebeJobType("task1")
            .endEvent()
            .moveToLastGateway()
            .sequenceFlowId("s2")
            .condition("=foo >= 5")
            .serviceTask("task2")
            .zeebeJobType("task2")
            .endEvent()
            .done();
    final String resourceName = processId + ".bpmn";
    final Long processDefinitionKey = deployProcess(process, resourceName);

    // when
    final Long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(
            camundaClient, processId, "{\"a\": \"b\"}"); // wrong payload provokes incident
    searchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, processInstanceKey);

    // then incident created, activity in INCIDENT state
    final List<IncidentEntity> allIncidents =
        incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey);
    assertThat(allIncidents).hasSize(1);
    final IncidentEntity incidentEntity = allIncidents.get(0);
    assertThat(incidentEntity.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(incidentEntity.getBpmnProcessId()).isEqualTo(processId);
    assertThat(incidentEntity.getFlowNodeId()).isEqualTo(activityId);
    assertThat(incidentEntity.getFlowNodeInstanceKey()).isNotNull();
    assertThat(incidentEntity.getErrorMessage()).isNotEmpty();
    assertThat(incidentEntity.getErrorType()).isNotNull();
    assertThat(incidentEntity.getState()).isEqualTo(IncidentState.ACTIVE);

    // assert list view data
    final ListViewProcessInstanceDto pi = getSingleProcessInstanceForListView();
    assertThat(pi.getState()).isEqualTo(ProcessInstanceStateDto.INCIDENT);

    // assert flow node instances
    final List<FlowNodeInstanceEntity> allFlowNodeInstances =
        tester.getAllFlowNodeInstances(processInstanceKey);
    assertThat(allFlowNodeInstances).hasSize(2);
    final FlowNodeInstanceEntity activity = allFlowNodeInstances.get(1);
    assertThat(activity.getState()).isEqualTo(FlowNodeState.ACTIVE);
    assertThat(activity.getIncidentKey()).isNotNull();
    assertThat(activity.getFlowNodeId()).isEqualTo(activityId);

    // when payload updated
    // TODO    ZeebeUtil.updateVariables(zeebeClient, gatewayActivity.getKey(),
    // processInstanceKey,
    // "{\"foo\": 7}", processId, processId);
    //    elasticsearchTestRule.processAllEvents(5);

    // then incident is resolved
    // TODO    processInstanceEntity =
    // processInstanceReader.getProcessInstanceByKey(processInstancekey);
    //    assertThat(processInstanceEntity.getIncidents().size()).isEqualTo(1);
    //    incidentEntity = processInstanceEntity.getIncidents().get(0);
    //    assertThat(incidentEntity.getElementId()).isEqualTo(activityId);
    //    assertThat(incidentEntity.getState()).isEqualTo(IncidentState.RESOLVED);

    // assert activity fields
    // TODO    final ActivityInstanceEntity xorActivity =
    // processInstanceEntity.getActivities().stream().filter(a -> a.getElementId().equals("xor"))
    //      .findFirst().get();
    //    assertThat(xorActivity.getState()).isEqualTo(ActivityState.COMPLETED);
    //    assertThat(xorActivity.getEndDate()).isNotNull();
  }

  @Test
  public void testProcessInstanceWithIncidentOnGatewayIsCanceled() {
    // having
    final String activityId = "xor";

    final String processId = "demoProcess";
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .exclusiveGateway(activityId)
            .sequenceFlowId("s1")
            .condition("=foo < 5")
            .serviceTask("task1")
            .zeebeJobType("task1")
            .endEvent()
            .moveToLastGateway()
            .sequenceFlowId("s2")
            .condition("=foo >= 5")
            .serviceTask("task2")
            .zeebeJobType("task2")
            .endEvent()
            .done();
    final String resourceName = processId + ".bpmn";
    final Long processDefinitionKey = deployProcess(process, resourceName);

    // when
    final Long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(
            camundaClient, processId, "{\"a\": \"b\"}"); // wrong payload provokes incident
    searchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, processInstanceKey);

    // then incident created, activity in INCIDENT state
    List<IncidentEntity> allIncidents =
        incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey);
    assertThat(allIncidents).hasSize(1);
    final IncidentEntity incidentEntity = allIncidents.get(0);
    assertThat(incidentEntity.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(incidentEntity.getBpmnProcessId()).isEqualTo(processId);
    assertThat(incidentEntity.getState()).isEqualTo(IncidentState.ACTIVE);

    // when I cancel process instance
    ZeebeTestUtil.cancelProcessInstance(camundaClient, processInstanceKey);
    searchTestRule.processAllRecordsAndWait(processInstanceIsCanceledCheck, processInstanceKey);
    searchTestRule.processAllRecordsAndWait(noActivitiesHaveIncident, processInstanceKey);

    // then incident is deleted
    final ProcessInstanceForListViewEntity processInstanceEntity =
        processInstanceReader.getProcessInstanceByKey(processInstanceKey);
    assertThat(processInstanceEntity.getState()).isEqualTo(ProcessInstanceState.CANCELED);
    allIncidents = incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey);
    assertThat(allIncidents).hasSize(0);

    // assert list view data
    final ListViewProcessInstanceDto pi = getSingleProcessInstanceForListView();
    assertThat(pi.getState()).isEqualTo(ProcessInstanceStateDto.CANCELED);

    // assert flow node instances
    final List<FlowNodeInstanceEntity> allFlowNodeInstances =
        tester.getAllFlowNodeInstances(processInstanceKey);
    assertThat(allFlowNodeInstances).hasSize(2);
    final FlowNodeInstanceEntity activity = allFlowNodeInstances.get(1);
    assertThat(activity.getState()).isEqualTo(FlowNodeState.TERMINATED);
    assertThat(activity.getFlowNodeId()).isEqualTo(activityId);
    assertThat(activity.getEndDate()).isNotNull();
  }

  @Test
  public void testProcessInstanceGatewayIsPassed() {
    // having
    final String activityId = "xor";

    final String processId = "demoProcess";
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .exclusiveGateway(activityId)
            .sequenceFlowId("s1")
            .condition("=foo < 5")
            .serviceTask("task1")
            .zeebeJobType("task1")
            .endEvent()
            .moveToLastGateway()
            .sequenceFlowId("s2")
            .condition("=foo >= 5")
            .serviceTask("task2")
            .zeebeJobType("task2")
            .endEvent()
            .done();
    final String resourceName = processId + ".bpmn";
    deployProcess(process, resourceName);

    // when
    final long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(camundaClient, processId, "{\"foo\": 6}");
    searchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "task2");

    // assert flow node instances
    final List<FlowNodeInstanceEntity> allFlowNodeInstances =
        tester.getAllFlowNodeInstances(processInstanceKey);
    assertThat(allFlowNodeInstances.size()).isGreaterThanOrEqualTo(2);
    final FlowNodeInstanceEntity activity = allFlowNodeInstances.get(1);
    assertThat(activity.getState()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(activity.getFlowNodeId()).isEqualTo(activityId);
    assertThat(activity.getEndDate()).isNotNull();
  }

  @Test
  public void testProcessInstanceEventBasedGatewayIsActive() {
    // having
    final String activityId = "gateway";

    final String processId = "demoProcess";
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
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

    // when
    final long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(
            camundaClient, processId, "{\"key1\": \"value1\", \"key2\": \"value2\"}");
    searchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "gateway");

    // assert flow node instances
    final List<FlowNodeInstanceEntity> allFlowNodeInstances =
        tester.getAllFlowNodeInstances(processInstanceKey);
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

    final String processId = "demoProcess";
    deployProcess("demoProcess_v_1.bpmn");
    final Long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(camundaClient, processId, "{\"a\": \"b\"}");
    searchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "taskA");

    // when
    cancelProcessInstance(processInstanceKey);

    // then
    final ProcessInstanceForListViewEntity processInstanceEntity =
        processInstanceReader.getProcessInstanceByKey(processInstanceKey);
    assertThat(processInstanceEntity.getId()).isEqualTo(processInstanceKey.toString());
    assertThat(processInstanceEntity.getKey()).isEqualTo(processInstanceKey);
    assertThat(processInstanceEntity.getState()).isEqualTo(ProcessInstanceState.CANCELED);
    assertThat(processInstanceEntity.getEndDate()).isNotNull();
    assertThat(processInstanceEntity.getEndDate()).isAfterOrEqualTo(testStartTime);
    assertThat(processInstanceEntity.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());

    // assert list view data
    final ListViewProcessInstanceDto pi = getSingleProcessInstanceForListView();
    assertThat(pi.getState()).isEqualTo(ProcessInstanceStateDto.CANCELED);
    assertThat(pi.getId()).isEqualTo(processInstanceKey.toString());
    assertThat(pi.getEndDate()).isNotNull();
    assertThat(pi.getEndDate()).isAfterOrEqualTo(testStartTime);
    assertThat(pi.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());

    // assert flow node instances
    final List<FlowNodeInstanceEntity> allFlowNodeInstances =
        tester.getAllFlowNodeInstances(processInstanceKey);
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

    final String processId = "eventProcess";
    deployProcess("messageEventProcess_v_1.bpmn");
    //    final long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient,
    // processId,
    // "{\"a\": \"b\"}");
    final Long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(camundaClient, processId, "{\"clientId\": \"5\"}");
    sleepFor(1000);

    // when
    cancelProcessInstance(processInstanceKey);

    // then
    final ProcessInstanceForListViewEntity processInstanceEntity =
        processInstanceReader.getProcessInstanceByKey(processInstanceKey);
    assertThat(processInstanceEntity.getId()).isEqualTo(processInstanceKey.toString());
    assertThat(processInstanceEntity.getKey()).isEqualTo(processInstanceKey);
    assertThat(processInstanceEntity.getState()).isEqualTo(ProcessInstanceState.CANCELED);
    assertThat(processInstanceEntity.getEndDate()).isNotNull();
    assertThat(processInstanceEntity.getEndDate()).isAfterOrEqualTo(testStartTime);
    assertThat(processInstanceEntity.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());

    // assert list view data
    final ListViewProcessInstanceDto pi = getSingleProcessInstanceForListView();
    assertThat(pi.getState()).isEqualTo(ProcessInstanceStateDto.CANCELED);
    assertThat(pi.getId()).isEqualTo(processInstanceKey.toString());
    assertThat(pi.getEndDate()).isNotNull();
    assertThat(pi.getEndDate()).isAfterOrEqualTo(testStartTime);
    assertThat(pi.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());

    // assert flow node instances
    final List<FlowNodeInstanceEntity> allFlowNodeInstances =
        tester.getAllFlowNodeInstances(processInstanceKey);
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

    final String processId = "eventProcess";
    deployProcess("messageEventProcess_v_1.bpmn");
    final Long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(camundaClient, processId, "{\"clientId\": \"5\"}");
    sleepFor(1000);

    // when
    sendMessages("clientMessage", "{\"messageVar\": \"someValue\"}", 20);
    searchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "taskA");

    // assert flow node instances
    final List<FlowNodeInstanceEntity> allFlowNodeInstances =
        tester.getAllFlowNodeInstances(processInstanceKey);
    assertThat(allFlowNodeInstances).hasSize(3);
    final FlowNodeInstanceEntity activity = allFlowNodeInstances.get(1);
    assertThat(activity.getState()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(activity.getEndDate()).isNotNull();
    assertThat(activity.getEndDate()).isAfterOrEqualTo(testStartTime);
    assertThat(activity.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());
  }

  private void sendMessages(final String messageName, final String payload, final int count) {
    ZeebeTestUtil.sendMessages(camundaClient, messageName, payload, count, String.valueOf(5));
  }

  @Test
  public void testProcessInstanceById() {
    final String processId = "demoProcess";
    deployProcess("demoProcess_v_1.bpmn");
    final long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(camundaClient, processId, "{\"a\": \"b\"}");
    searchTestRule.processAllRecordsAndWait(processInstanceIsCreatedCheck, processInstanceKey);

    final ProcessInstanceForListViewEntity processInstanceById =
        processInstanceReader.getProcessInstanceByKey(processInstanceKey);
    assertThat(processInstanceById).isNotNull();
    assertThat(processInstanceById.getState()).isEqualTo(ProcessInstanceState.ACTIVE);
  }

  @Test
  public void testProcessInstanceWithIncidentById() {
    final String activityId = "taskA";
    final String errorMessage = "Error occurred when working on the job";
    final String processId = "demoProcess";
    deployProcess("demoProcess_v_1.bpmn");
    final long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(camundaClient, processId, "{\"a\": \"b\"}");
    searchTestRule.processAllRecordsAndWait(processInstanceIsCreatedCheck, processInstanceKey);

    // create an incident
    failTaskWithNoRetriesLeft(activityId, processInstanceKey, errorMessage);
    searchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, processInstanceKey);

    final ProcessInstanceForListViewEntity processInstanceById =
        processInstanceReader.getProcessInstanceByKey(processInstanceKey);
    assertThat(processInstanceById).isNotNull();
    assertThat(processInstanceById.getState()).isEqualTo(ProcessInstanceState.ACTIVE);
    assertThat(processInstanceById.isIncident()).isTrue();
  }

  @Test
  public void testJobFailedWithRetriesLeft() {
    final String activityId = "taskA";
    final String errorMessage = "Error occurred when working on the job";
    final String processId = "demoProcess";
    final String processId2 = "simpleProcess";
    deployProcess("demoProcess_v_1.bpmn");
    deployProcess(
        Bpmn.createExecutableProcess(processId2)
            .startEvent()
            .serviceTask("task1")
            .zeebeJobType("task1")
            .endEvent()
            .done(),
        processId2 + ".bpmn");
    final long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(camundaClient, processId, "{\"a\": \"b\"}");
    final long processInstanceKey2 =
        ZeebeTestUtil.startProcessInstance(camundaClient, processId2, null);
    searchTestRule.processAllRecordsAndWait(processInstanceIsCreatedCheck, processInstanceKey);
    searchTestRule.processAllRecordsAndWait(processInstanceIsCreatedCheck, processInstanceKey2);

    // fail task with retries left
    failTaskWithRetriesLeft(activityId, processInstanceKey, errorMessage);

    // when search by retriesLeft = true
    ListViewResponseDto response =
        listViewReader.queryProcessInstances(
            createGetAllProcessInstancesRequest(q -> q.setRetriesLeft(true)));
    // then
    assertThat(response.getProcessInstances().size()).isEqualTo(1);
    assertThat(response.getProcessInstances().get(0).getId())
        .isEqualTo(String.valueOf(processInstanceKey));

    // fail task with no retries -> incident
    failTaskWithNoRetriesLeft(activityId, processInstanceKey, 1, errorMessage);
    // when search by retriesLeft = true
    response =
        listViewReader.queryProcessInstances(
            createGetAllProcessInstancesRequest(q -> q.setRetriesLeft(true)));
    // then
    assertThat(response.getProcessInstances().size()).isEqualTo(0);

    // when search by retriesLeft = false
    response =
        listViewReader.queryProcessInstances(
            createGetAllProcessInstancesRequest(q -> q.setRetriesLeft(false)));
    // then
    assertThat(response.getProcessInstances().size()).isEqualTo(2);
    assertThat(response.getProcessInstances())
        .extracting(ListViewProcessInstanceDto::getId)
        .contains(String.valueOf(processInstanceKey));
  }

  @Test
  public void testProcessInstanceCalledFromCallActivityById() throws Exception {
    // having process with call activity
    final String parentProcessId = "parentProcess";
    final String calledProcessId = "process";
    final String callActivityId = "callActivity";
    final BpmnModelInstance testProcess =
        Bpmn.createExecutableProcess(parentProcessId)
            .startEvent()
            .callActivity(callActivityId)
            .zeebeProcessId(calledProcessId)
            .done();
    final long calledProcessDefinitionKey =
        tester.deployProcess("single-task.bpmn").getProcessDefinitionKey();

    final Long parentProcessDefinitionKey =
        tester.deployProcess(testProcess, "testProcess.bpmn").getProcessDefinitionKey();
    final long parentProcessInstanceKey =
        tester
            .startProcessInstance(parentProcessId, null)
            .and()
            .waitUntil()
            .conditionIsMet(processInstancesAreStartedByProcessId, calledProcessDefinitionKey, 1)
            .getProcessInstanceKey();

    // find called process instance key
    final ListViewResponseDto response =
        listViewReader.queryProcessInstances(
            createGetAllProcessInstancesRequest(
                q -> q.setBpmnProcessId(calledProcessId).setProcessVersion(1)));
    assertThat(response.getProcessInstances().size()).isEqualTo(1);
    final String calledProcessInstanceId = response.getProcessInstances().get(0).getId();

    // when
    final ListViewProcessInstanceDto processInstance =
        getProcessInstanceById(calledProcessInstanceId);

    // then
    assertThat(processInstance).isNotNull();
    assertThat(processInstance.getParentInstanceId())
        .isEqualTo(String.valueOf(parentProcessInstanceKey));
    assertThat(processInstance.getRootInstanceId())
        .isEqualTo(String.valueOf(parentProcessInstanceKey));

    assertThat(processInstance.getCallHierarchy()).isNotNull();
    assertThat(processInstance.getCallHierarchy()).hasSize(1);
    final ProcessInstanceReferenceDto callHier1 = processInstance.getCallHierarchy().get(0);
    assertThat(callHier1.getInstanceId()).isEqualTo(String.valueOf(parentProcessInstanceKey));
    assertThat(callHier1.getProcessDefinitionId())
        .isEqualTo(String.valueOf(parentProcessDefinitionKey));
    assertThat(callHier1.getProcessDefinitionName()).isEqualTo(parentProcessId);
  }

  private ListViewProcessInstanceDto getProcessInstanceById(final String processInstanceId)
      throws Exception {
    final String url = String.format("%s/%s", PROCESS_INSTANCE_URL, processInstanceId);
    final MvcResult result = getRequest(url);
    return mockMvcTestRule.fromResponse(result, new TypeReference<>() {});
  }

  @Test
  public void testEventSubprocess() {
    // having
    final OffsetDateTime testStartTime = OffsetDateTime.now();

    final String processId = "eventSubProcess";
    deployProcess("eventSubProcess.bpmn");
    final Long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(camundaClient, processId, null);
    sleepFor(5_000);

    // when
    searchTestRule.processAllRecordsAndWait(flowNodeIsTerminatedCheck, processInstanceKey, "taskA");

    // assert flow node instances
    final List<FlowNodeInstanceEntity> allFlowNodeInstances =
        tester.getAllFlowNodeInstances(processInstanceKey);
    assertThat(allFlowNodeInstances).hasSize(5);
    final List<FlowNodeInstanceEntity> eventSP =
        allFlowNodeInstances.stream()
            .filter(fn -> fn.getType().equals(FlowNodeType.EVENT_SUB_PROCESS))
            .collect(Collectors.toList());
    assertThat(eventSP).hasSize(1);
  }

  @Test(expected = NotFoundException.class)
  public void testProcessInstanceByIdFailForUnknownId() {
    final String processId = "demoProcess";
    deployProcess("demoProcess_v_1.bpmn");
    final long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(camundaClient, processId, "{\"a\": \"b\"}");
    searchTestRule.processAllRecordsAndWait(processInstanceIsCreatedCheck, processInstanceKey);

    /*final ProcessInstanceForListViewEntity processInstanceById =*/ processInstanceReader
        .getProcessInstanceByKey(-42L);
  }

  @Test
  public void testSequenceFlowEntityCreated() {

    // having
    final String processId = "demoProcess";
    final Long processDefinitionKey = deployProcess("demoProcess_v_1.bpmn");

    // when
    final Long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(camundaClient, processId, "{\"a\": \"b\"}");
    searchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "taskA");

    // then
    final List<SequenceFlowEntity> sequenceFlowEntities =
        sequenceFlowStore.getSequenceFlowsByProcessInstanceKey(processInstanceKey);
    assertThat(sequenceFlowEntities).hasSize(1);

    final SequenceFlowEntity sequenceFlowEntity = sequenceFlowEntities.get(0);
    assertThat(sequenceFlowEntity.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(sequenceFlowEntity.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(sequenceFlowEntity.getBpmnProcessId()).isEqualTo(processId);
    assertThat(sequenceFlowEntity.getActivityId()).isEqualTo("SequenceFlow_1sz6737");
  }

  @Test
  public void testVariableEntityCreated() {

    // having
    final String processId = "demoProcess";
    final Long processDefinitionKey = deployProcess("demoProcess_v_1.bpmn");

    // when
    final Long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(camundaClient, processId, "{\"a\": \"b\"}");
    searchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "taskA");

    // then
    final List<VariableEntity> variableEntities =
        testSearchRepository.getVariablesByProcessInstanceKey(
            variableTemplate.getAlias(), processInstanceKey);
    assertThat(variableEntities).isNotEmpty();

    final VariableEntity variableEntity = variableEntities.get(0);
    assertThat(variableEntity.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(variableEntity.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(variableEntity.getBpmnProcessId()).isEqualTo(processId);
    assertThat(variableEntity.getValue()).isEqualTo("\"b\"");
  }

  private void assertStartFlowNodeCompleted(final FlowNodeInstanceEntity startActivity) {
    assertThat(startActivity.getFlowNodeId()).isEqualTo("start");
    assertThat(startActivity.getProcessDefinitionKey()).isNotNull();
    assertThat(startActivity.getBpmnProcessId()).isNotNull();
    assertThat(startActivity.getState()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(startActivity.getType()).isEqualTo(FlowNodeType.START_EVENT);
    assertThat(startActivity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(startActivity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());
    assertThat(startActivity.getEndDate()).isAfterOrEqualTo(startActivity.getStartDate());
    assertThat(startActivity.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());
  }

  private void assertFlowNodeIsActive(
      final FlowNodeInstanceEntity flowNodeEntity, final String flowNodeId) {
    assertThat(flowNodeEntity.getFlowNodeId()).isEqualTo(flowNodeId);
    assertThat(flowNodeEntity.getProcessDefinitionKey()).isNotNull();
    assertThat(flowNodeEntity.getBpmnProcessId()).isNotNull();
    assertThat(flowNodeEntity.getState()).isEqualTo(FlowNodeState.ACTIVE);
    assertThat(flowNodeEntity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(flowNodeEntity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());
    assertThat(flowNodeEntity.getEndDate()).isNull();
  }
}
