/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.entities.ErrorType.JOB_NO_RETRIES;
import static org.camunda.operate.webapp.rest.WorkflowInstanceRestService.WORKFLOW_INSTANCE_URL;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.value.IncidentRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.camunda.operate.entities.FlowNodeInstanceEntity;
import org.camunda.operate.entities.FlowNodeState;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceState;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.TestUtil;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.webapp.es.reader.FlowNodeInstanceReader;
import org.camunda.operate.webapp.es.reader.IncidentReader;
import org.camunda.operate.webapp.es.reader.ListViewReader;
import org.camunda.operate.webapp.es.reader.WorkflowInstanceReader;
import org.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceDto;
import org.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceQueryDto;
import org.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import org.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import org.camunda.operate.webapp.rest.dto.listview.ListViewWorkflowInstanceDto;
import org.camunda.operate.webapp.rest.dto.listview.WorkflowInstanceStateDto;
import org.camunda.operate.webapp.rest.dto.metadata.FlowNodeInstanceMetadataDto;
import org.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataDto;
import org.camunda.operate.zeebe.ImportValueType;
import org.camunda.operate.zeebe.PartitionHolder;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public class ZeebeImportIT extends OperateZeebeIntegrationTest {

  @Autowired
  private WorkflowInstanceReader workflowInstanceReader;

  @Autowired
  private PartitionHolder partitionHolder;

  @Autowired
  private ListViewReader listViewReader;

  @Autowired
  private IncidentReader incidentReader;

  @Autowired
  private FlowNodeInstanceReader flowNodeInstanceReader;

  @Test
  public void testWorkflowNameAndVersionAreLoaded() {
    // having
    String processId = "demoProcess";
    final Long workflowKey = ZeebeTestUtil.deployWorkflow(zeebeClient, "demoProcess_v_1.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");

    //when
    //1st load workflow instance index, then deployment
    processImportTypeAndWait(ImportValueType.WORKFLOW_INSTANCE, workflowInstanceIsCreatedCheck, workflowInstanceKey);
    processImportTypeAndWait(ImportValueType.DEPLOYMENT, workflowIsDeployedCheck, workflowKey);

    //then
    final WorkflowInstanceForListViewEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceByKey(workflowInstanceKey);
    assertThat(workflowInstanceEntity.getWorkflowKey()).isEqualTo(workflowKey);
    assertThat(workflowInstanceEntity.getWorkflowName()).isNotNull();
    assertThat(workflowInstanceEntity.getWorkflowVersion()).isEqualTo(1);
  }

  protected void processImportTypeAndWait(ImportValueType importValueType,Predicate<Object[]> waitTill, Object... arguments) {
    elasticsearchTestRule.processRecordsWithTypeAndWait(importValueType,waitTill, arguments);
  }

  @Test
  public void testCreateWorkflowInstanceWithEmptyWorkflowName() {
    // given a process with empty name
    String processId = "emptyNameProcess";
    BpmnModelInstance model = Bpmn.createExecutableProcess(processId)
        .startEvent()
          .serviceTask("taskA")
          .zeebeJobType("taskA")
        .endEvent().done();

    final Long workflowKey = deployWorkflow(model,"emptyNameProcess.bpmn");

    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstanceIsCreatedCheck, workflowInstanceKey);
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, workflowInstanceKey, "taskA");

    // then it should returns the processId instead of an empty name
    final WorkflowInstanceForListViewEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceByKey(workflowInstanceKey);
    assertThat(workflowInstanceEntity.getWorkflowKey()).isEqualTo(workflowKey);
    assertThat(workflowInstanceEntity.getBpmnProcessId()).isEqualTo(processId);
    assertThat(workflowInstanceEntity.getWorkflowName()).isEqualTo(processId);
  }

  @Test
  public void testIncidentCreatesWorkflowInstance() {
    // having
    String activityId = "taskA";
    String processId = "demoProcess";
    final Long workflowKey = deployWorkflow("demoProcess_v_1.bpmn");
    final Long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");

    //create an incident
    ZeebeTestUtil.failTask(getClient(), activityId, getWorkerName(), 3, "Some error");

    //when
    //1st load incident
    processImportTypeAndWait(ImportValueType.INCIDENT,incidentIsActiveCheck, workflowInstanceKey);

    //and then workflow instance events
    processImportTypeAndWait(ImportValueType.WORKFLOW_INSTANCE, workflowInstanceIsCreatedCheck, workflowInstanceKey);

    //then
    final WorkflowInstanceForListViewEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceByKey(workflowInstanceKey);
    assertWorkflowInstanceListViewEntityWithIncident(workflowInstanceEntity,"Demo process",workflowKey,workflowInstanceKey);
    //and
    final List<IncidentEntity> allIncidents = incidentReader.getAllIncidentsByWorkflowInstanceKey(workflowInstanceKey);
    assertThat(allIncidents).hasSize(1);
    assertIncidentEntity(allIncidents.get(0),activityId, workflowKey,IncidentState.ACTIVE);

    //and
    final ListViewWorkflowInstanceDto wi = getSingleWorkflowInstanceForListView();
    assertListViewWorkflowInstanceDto(wi, workflowKey, workflowInstanceKey);

    //and
    final List<FlowNodeInstanceEntity> flowNodeInstances = getFlowNodeInstances(
        workflowInstanceKey);
    assertActivityInstanceTreeDto(flowNodeInstances, 2, activityId);
  }

  @Test
  public void testEarlierEventsAreIgnored() throws Exception {
    // having
    String activityId = "taskA";
    String processId = "demoProcess";
    final Long workflowKey = deployWorkflow("demoProcess_v_1.bpmn");
    final Long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");

    //create an incident
    final String incidentError = "Some error";
    ZeebeTestUtil.failTask(getClient(), activityId, getWorkerName(), 3, incidentError);

    //when
    //1st load incident
    processImportTypeAndWait(ImportValueType.INCIDENT, incidentIsActiveCheck, workflowInstanceKey);

    //and then workflow instance events
    processImportTypeAndWait(ImportValueType.WORKFLOW_INSTANCE, workflowInstanceIsCreatedCheck, workflowInstanceKey);
    processImportTypeAndWait(ImportValueType.JOB, workflowInstanceIsCreatedCheck, workflowInstanceKey);

    //when
    //get flow node instance tree
    final String workflowInstanceId = String.valueOf(workflowInstanceKey);
    FlowNodeInstanceQueryDto request = new FlowNodeInstanceQueryDto(
        workflowInstanceId, workflowInstanceId);
    List<FlowNodeInstanceDto> instances = tester.getFlowNodeInstanceOneListFromRest(request);

    final FlowNodeMetadataDto flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(workflowInstanceKey),
        null, null, instances.get(instances.size() - 1).getId());
    FlowNodeInstanceMetadataDto flowNodeInstanceMetadata = flowNodeMetadata.getInstanceMetadata();
    assertThat(flowNodeInstanceMetadata.getIncidentErrorMessage()).isEqualTo(incidentError);
    assertThat(flowNodeInstanceMetadata.getIncidentErrorType()).isEqualTo(JOB_NO_RETRIES);
  }

  private void assertActivityInstanceTreeDto(final List<FlowNodeInstanceEntity> tree, final int childrenCount, final String activityId) {
    assertThat(tree).hasSize(childrenCount);
    assertStartActivityCompleted(tree.get(0));
    assertFlowNodeIsInIncidentState(tree.get(1), activityId);
  }

  private void assertListViewWorkflowInstanceDto(final ListViewWorkflowInstanceDto wi, final Long workflowKey, final Long workflowInstanceKey) {
    assertThat(wi.getState()).isEqualTo(WorkflowInstanceStateDto.INCIDENT);
    assertThat(wi.getWorkflowId()).isEqualTo(workflowKey.toString());
    assertThat(wi.getWorkflowName()).isEqualTo("Demo process");
    assertThat(wi.getWorkflowVersion()).isEqualTo(1);
    assertThat(wi.getId()).isEqualTo(workflowInstanceKey.toString());
    assertThat(wi.getEndDate()).isNull();
    assertThat(wi.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(wi.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());
  }

  private void assertIncidentEntity(final IncidentEntity incidentEntity,String activityId, final Long workflowKey,final IncidentState state) {
    assertThat(incidentEntity.getFlowNodeId()).isEqualTo(activityId);
    assertThat(incidentEntity.getFlowNodeInstanceKey()).isNotNull();
    assertThat(incidentEntity.getErrorMessage()).isNotEmpty();
    assertThat(incidentEntity.getErrorType()).isNotNull();
    assertThat(incidentEntity.getState()).isEqualTo(state);
    assertThat(incidentEntity.getWorkflowKey()).isEqualTo(workflowKey);
  }

  private void assertWorkflowInstanceListViewEntityWithIncident(WorkflowInstanceForListViewEntity workflowInstanceEntity,final String workflowName,final Long workflowKey, final Long workflowInstanceKey) {
    assertThat(workflowInstanceEntity.getWorkflowKey()).isEqualTo(workflowKey);
    assertThat(workflowInstanceEntity.getWorkflowName()).isEqualTo(workflowName);
    assertThat(workflowInstanceEntity.getWorkflowVersion()).isEqualTo(1);
    assertThat(workflowInstanceEntity.getId()).isEqualTo(workflowInstanceKey.toString());
    assertThat(workflowInstanceEntity.getKey()).isEqualTo(workflowInstanceKey);
    assertThat(workflowInstanceEntity.getState()).isEqualTo(WorkflowInstanceState.INCIDENT);
    assertThat(workflowInstanceEntity.getEndDate()).isNull();
    assertThat(workflowInstanceEntity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(workflowInstanceEntity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());
  }

  protected ListViewWorkflowInstanceDto getSingleWorkflowInstanceForListView() {
    final ListViewRequestDto request = TestUtil.createGetAllWorkflowInstancesRequest();
    request.setPageSize(100);
    final ListViewResponseDto listViewResponse = listViewReader.queryWorkflowInstances(request);
    assertThat(listViewResponse.getTotalCount()).isEqualTo(1);
    assertThat(listViewResponse.getWorkflowInstances()).hasSize(1);
    return listViewResponse.getWorkflowInstances().get(0);
  }


  protected List<FlowNodeInstanceEntity> getFlowNodeInstances(Long workflowInstanceKey) {
    return tester.getAllFlowNodeInstances(workflowInstanceKey);
  }


  @Test
  public void testOnlyIncidentIsLoaded() throws Exception {
    // having
    String activityId = "taskA";
    String processId = "demoProcess";
    deployWorkflow("demoProcess_v_1.bpmn");
    Long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    //create an incident
    ZeebeTestUtil.failTask(getClient(), activityId, getWorkerName(), 3, "Some error");

    //when
    //load only incidents
    processImportTypeAndWait(ImportValueType.INCIDENT,incidentIsActiveCheck, workflowInstanceKey);

    assertListViewResponse();
    //if nothing is returned in list view - there is no way to access the workflow instance, no need to check other queries

  }

  protected void assertListViewResponse() throws Exception {
    ListViewRequestDto listViewRequest = TestUtil.createGetAllWorkflowInstancesRequest();
    listViewRequest.setPageSize(100);
    MockHttpServletRequestBuilder request = post(query())
      .content(mockMvcTestRule.json(listViewRequest))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc
      .perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    //check that nothing is returned
    final ListViewResponseDto listViewResponse = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {
    });
    assertThat(listViewResponse.getTotalCount()).isEqualTo(0);
    assertThat(listViewResponse.getWorkflowInstances()).hasSize(0);
  }

  @Test
  public void testIncidentDeletedAfterActivityCompleted() {
    // having
    String activityId = "taskA";


    String processId = "demoProcess";
    final BpmnModelInstance modelInstance =
      Bpmn.createExecutableProcess(processId)
        .startEvent("start")
          .serviceTask(activityId).zeebeJobType(activityId)
        .endEvent()
      .done();
    deployWorkflow(modelInstance, "demoProcess_v_1.bpmn");
    final Long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");

    //create an incident
    final Long jobKey = ZeebeTestUtil.failTask(getClient(), activityId, getWorkerName(), 3, "Some error");
    final long incidentKey = getOnlyIncidentKey();

    //when update retries
    ZeebeTestUtil.resolveIncident(zeebeClient, jobKey, incidentKey);
    ZeebeTestUtil.completeTask(getClient(), activityId, getWorkerName(), "{}");

    processImportTypeAndWait(ImportValueType.WORKFLOW_INSTANCE,workflowInstancesAreFinishedCheck, List.of(workflowInstanceKey));
    processImportTypeAndWait(ImportValueType.INCIDENT, incidentIsResolvedCheck, workflowInstanceKey);

    //then
    final List<IncidentEntity> allIncidents = incidentReader.getAllIncidentsByWorkflowInstanceKey(workflowInstanceKey);
    assertThat(allIncidents).hasSize(0);

    //assert list view data
    final ListViewWorkflowInstanceDto wi = getSingleWorkflowInstanceForListView();
    assertThat(wi.getId()).isEqualTo(workflowInstanceKey.toString());
    assertThat(wi.getState()).isEqualTo(WorkflowInstanceStateDto.COMPLETED);
    assertThat(wi.getEndDate()).isNotNull();

    //assert flow node instances
    final List<FlowNodeInstanceEntity> flowNodeInstances = getFlowNodeInstances(
        workflowInstanceKey);
    assertThat(flowNodeInstances.size()).isGreaterThanOrEqualTo(2);
    assertStartActivityCompleted(flowNodeInstances.get(0));
    assertFlowNodeIsCompleted(flowNodeInstances.get(1), "taskA");
  }

  protected long getOnlyIncidentKey() {
    final List<Record<IncidentRecordValue>> incidents = RecordingExporter.incidentRecords(IncidentIntent.CREATED)
      .collect(Collectors.toList());
    assertThat(incidents).hasSize(1);
    return incidents.get(0).getKey();
  }

  @Test
  public void testIncidentDeletedAfterActivityTerminated() {
    // having
    String activityId = "taskA";


    String processId = "demoProcess";
    final BpmnModelInstance modelInstance =
      Bpmn.createExecutableProcess(processId)
        .startEvent("start")
        .serviceTask(activityId).zeebeJobType(activityId)
        .endEvent()
        .done();
    deployWorkflow(modelInstance, "demoProcess_v_1.bpmn");
    final Long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");

    //create an incident
    final Long jobKey = ZeebeTestUtil.failTask(getClient(), activityId, getWorkerName(), 3, "Some error");

    final long incidentKey = getOnlyIncidentKey();

    //when update retries
    ZeebeTestUtil.resolveIncident(zeebeClient, jobKey, incidentKey);

    ZeebeTestUtil.cancelWorkflowInstance(getClient(), workflowInstanceKey);

    processImportTypeAndWait(ImportValueType.WORKFLOW_INSTANCE, workflowInstanceIsCanceledCheck, workflowInstanceKey);
    processImportTypeAndWait(ImportValueType.INCIDENT, incidentIsResolvedCheck,workflowInstanceKey);
    //then
    final List<IncidentEntity> allIncidents = incidentReader.getAllIncidentsByWorkflowInstanceKey(workflowInstanceKey);
    assertThat(allIncidents).hasSize(0);

    //assert list view data
    final ListViewWorkflowInstanceDto wi = getSingleWorkflowInstanceForListView();
    assertThat(wi.getId()).isEqualTo(workflowInstanceKey.toString());
    assertThat(wi.getState()).isEqualTo(WorkflowInstanceStateDto.CANCELED);
    assertThat(wi.getEndDate()).isNotNull();

    //assert flow node instances
    final List<FlowNodeInstanceEntity> flowNodeInstances = getFlowNodeInstances(
        workflowInstanceKey);
    assertThat(flowNodeInstances.size()).isGreaterThanOrEqualTo(2);
    final FlowNodeInstanceEntity flowNodeInstance = flowNodeInstances.get(1);
    assertThat(flowNodeInstance.getFlowNodeId()).isEqualTo(activityId);
    assertThat(flowNodeInstance.getState()).isEqualTo(FlowNodeState.TERMINATED);
    assertThat(flowNodeInstance.getEndDate()).isNotNull();

  }

  @Test
  public void testPartitionIds() {
    final List<Integer> operatePartitions = partitionHolder.getPartitionIds();
    final int zeebePartitionsCount = zeebeClient.newTopologyRequest().send().join().getPartitionsCount();
    assertThat(operatePartitions).hasSize(zeebePartitionsCount);
    assertThat(operatePartitions).allMatch(id -> id <= zeebePartitionsCount && id >= 1);
  }

  private void assertStartActivityCompleted(FlowNodeInstanceEntity activity) {
    assertFlowNodeIsCompleted(activity, "start");
  }

  private void assertFlowNodeIsInIncidentState(FlowNodeInstanceEntity activity, String activityId) {
    assertThat(activity.getFlowNodeId()).isEqualTo(activityId);
    assertThat(activity.getState()).isEqualTo(FlowNodeState.ACTIVE);
    assertThat(activity.getIncidentKey()).isNotNull();
    assertThat(activity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(activity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());
  }

  private void assertFlowNodeIsCompleted(FlowNodeInstanceEntity activity, String activityId) {
    assertThat(activity.getFlowNodeId()).isEqualTo(activityId);
    assertThat(activity.getState()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(activity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(activity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());
    assertThat(activity.getEndDate()).isAfterOrEqualTo(activity.getStartDate());
    assertThat(activity.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());
  }

  private String query() {
    return WORKFLOW_INSTANCE_URL;
  }

}
