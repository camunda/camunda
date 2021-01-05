/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.webapp.rest.ActivityInstanceRestService.ACTIVITY_INSTANCE_URL;
import static org.camunda.operate.webapp.rest.FlowNodeInstanceRestService.FLOW_NODE_INSTANCE_URL;

import com.fasterxml.jackson.core.type.TypeReference;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.camunda.operate.entities.EventSourceType;
import org.camunda.operate.entities.EventType;
import org.camunda.operate.entities.FlowNodeState;
import org.camunda.operate.entities.FlowNodeType;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.util.ConversionUtils;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.webapp.es.reader.IncidentReader;
import org.camunda.operate.webapp.rest.dto.FlowNodeInstanceMetadataDto;
import org.camunda.operate.webapp.rest.dto.activity.ActivityInstanceTreeRequestDto;
import org.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceDto;
import org.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceRequestDto;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

public class FlowNodeInstanceIT extends OperateZeebeIntegrationTest {

  @Autowired
  private IncidentReader incidentReader;

  @Test
  public void testFlowNodeInstanceTreeForNonInterruptingBoundaryEvent() throws Exception {
    // having
    String processId = "nonInterruptingBoundaryEvent";
    deployWorkflow("nonInterruptingBoundaryEvent_v_2.bpmn");
    final Long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, null);
    //let the boundary event happen
    //Thread.sleep(1500L);
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, workflowInstanceKey, "task2");

    //when
    final String workflowInstanceId = String.valueOf(workflowInstanceKey);
    FlowNodeInstanceRequestDto request = new FlowNodeInstanceRequestDto(
        workflowInstanceId, workflowInstanceId);
    List<FlowNodeInstanceDto> instances = getFlowNodeInstanceTreeFromRest(request);

    //then
    assertThat(instances).hasSize(4);
    assertChild(instances, 0, "startEvent", FlowNodeState.COMPLETED, workflowInstanceId, FlowNodeType.START_EVENT);
    assertChild(instances, 1, "task1", FlowNodeState.ACTIVE, workflowInstanceId, FlowNodeType.SERVICE_TASK);
    assertChild(instances, 2, "boundaryEvent", FlowNodeState.COMPLETED, workflowInstanceId, FlowNodeType.BOUNDARY_EVENT);
    assertChild(instances, 3, "task2", FlowNodeState.ACTIVE, workflowInstanceId, FlowNodeType.SERVICE_TASK);
  }

  @Test
  public void testFlowNodeInstanceTreeIsBuild() throws Exception {
    // having
    String processId = "prWithSubprocess";
    deployWorkflow("subProcess.bpmn");
    final Long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"items\": [0, 1, 2, 3, 4, 5]}");
    ZeebeTestUtil.completeTask(zeebeClient, "taskA", getWorkerName(), null);
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, workflowInstanceKey, "taskB");
    ZeebeTestUtil.completeTask(zeebeClient, "taskB", getWorkerName(), null, 6);
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, workflowInstanceKey, "taskC");
    ZeebeTestUtil.failTask(zeebeClient, "taskC", getWorkerName(), 3, "some error");
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, workflowInstanceKey);

    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when - test level 0
    final String workflowInstanceId = String.valueOf(workflowInstanceKey);
    FlowNodeInstanceRequestDto request = new FlowNodeInstanceRequestDto(
        workflowInstanceId, workflowInstanceId);
    List<FlowNodeInstanceDto> instances = getFlowNodeInstanceTreeFromRest(request);

    //then
    assertThat(instances).hasSize(3);
    assertChild(instances, 0, "startEvent", FlowNodeState.COMPLETED, workflowInstanceId, FlowNodeType.START_EVENT);
    assertChild(instances, 1, "taskA", FlowNodeState.COMPLETED, workflowInstanceId, FlowNodeType.SERVICE_TASK);
    final FlowNodeInstanceDto subprocess = assertChild(instances, 2, "subprocess",
        //FIXME OPE-1183
//        FlowNodeState.INCIDENT, workflowInstanceId, FlowNodeType.SUB_PROCESS);
        FlowNodeState.ACTIVE, workflowInstanceId, FlowNodeType.SUB_PROCESS);

    //when - test level 1
    request = new FlowNodeInstanceRequestDto(
        workflowInstanceId, subprocess.getTreePath());
    instances = getFlowNodeInstanceTreeFromRest(request);

    //then
    assertThat(instances).hasSize(3);
    assertChild(instances, 0, "startEventSubprocess", FlowNodeState.COMPLETED, subprocess.getTreePath(), FlowNodeType.START_EVENT);
    assertChild(instances, 2, "taskC", FlowNodeState.INCIDENT, subprocess.getTreePath(), FlowNodeType.SERVICE_TASK);
    final FlowNodeInstanceDto innerSubprocess = assertChild(instances, 1, "innerSubprocess",
        FlowNodeState.COMPLETED, subprocess.getTreePath(), FlowNodeType.SUB_PROCESS);

    //when - test level 2 - multi-instance body
    request = new FlowNodeInstanceRequestDto(
        workflowInstanceId, innerSubprocess.getTreePath());
    instances = getFlowNodeInstanceTreeFromRest(request);

    //then
    assertThat(instances).hasSize(3);
    assertChild(instances, 0, "startEventInnerSubprocess", FlowNodeState.COMPLETED, innerSubprocess.getTreePath(), FlowNodeType.START_EVENT);
    final FlowNodeInstanceDto multiInstanceBody = assertChild(instances, 1, "taskB", FlowNodeState.COMPLETED,
        innerSubprocess.getTreePath(), FlowNodeType.MULTI_INSTANCE_BODY);
    assertChild(instances, 2,
        "endEventInnerSubprocess", FlowNodeState.COMPLETED, innerSubprocess.getTreePath(),
        FlowNodeType.END_EVENT);

    //when - test level 3 - page 1
    request = new FlowNodeInstanceRequestDto(
        workflowInstanceId, multiInstanceBody.getTreePath());
    request.setPageSize(4);
    instances = getFlowNodeInstanceTreeFromRest(request);

    //then
    assertThat(instances).hasSize(4);
    assertChild(instances, 0, "taskB", FlowNodeState.COMPLETED, multiInstanceBody.getTreePath(), FlowNodeType.SERVICE_TASK);
    assertChild(instances, 1, "taskB", FlowNodeState.COMPLETED, multiInstanceBody.getTreePath(), FlowNodeType.SERVICE_TASK);
    assertChild(instances, 2, "taskB", FlowNodeState.COMPLETED, multiInstanceBody.getTreePath(), FlowNodeType.SERVICE_TASK);
    final FlowNodeInstanceDto lastTaskPage1 = assertChild(instances, 3, "taskB", FlowNodeState.COMPLETED,
        multiInstanceBody.getTreePath(), FlowNodeType.SERVICE_TASK);

    //when - test level 3 - page 2
    request = new FlowNodeInstanceRequestDto(
        workflowInstanceId, multiInstanceBody.getTreePath());
    request.setPageSize(5);
    request.setSearchAfter(lastTaskPage1.getSortValues());
    instances = getFlowNodeInstanceTreeFromRest(request);

    //then
    assertThat(instances).hasSize(2);
    final FlowNodeInstanceDto taskBBeforeLast = assertChild(instances, 0, "taskB", FlowNodeState.COMPLETED,
        multiInstanceBody.getTreePath(), FlowNodeType.SERVICE_TASK);
    final FlowNodeInstanceDto taskBLast = assertChild(instances, 1, "taskB", FlowNodeState.COMPLETED,
        multiInstanceBody.getTreePath(), FlowNodeType.SERVICE_TASK);

    //when - test level 3 - searchBefore
    request = new FlowNodeInstanceRequestDto(
        workflowInstanceId, multiInstanceBody.getTreePath());
    request.setPageSize(4);
    request.setSearchBefore(taskBLast.getSortValues());
    instances = getFlowNodeInstanceTreeFromRest(request);

    //then
    assertThat(instances).hasSize(4);
    for (int i=0; i<3; i++) {
      assertChild(instances, i, "taskB", FlowNodeState.COMPLETED, multiInstanceBody.getTreePath(), FlowNodeType.SERVICE_TASK);
    }
    assertThat(assertChild(instances, 3, "taskB", FlowNodeState.COMPLETED, multiInstanceBody.getTreePath(), FlowNodeType.SERVICE_TASK).getId())
      .isEqualTo(taskBBeforeLast.getId());

  }

  protected FlowNodeInstanceDto assertChild(List<FlowNodeInstanceDto> children, int childPosition, String flowNodeId, FlowNodeState state, String parentTreePath, FlowNodeType type) {
    final FlowNodeInstanceDto flowNode = children.get(childPosition);
    assertThat(flowNode.getFlowNodeId()).isEqualTo(flowNodeId);
    assertThat(flowNode.getId()).isNotNull();
    assertThat(flowNode.getState()).isEqualTo(state);
    assertThat(flowNode.getTreePath()).isEqualTo(ConversionUtils.toStringOrNull(parentTreePath + "/" + flowNode.getId()));
    assertThat(flowNode.getStartDate()).isNotNull();
    if (state.equals(FlowNodeState.COMPLETED) || state.equals(FlowNodeState.TERMINATED)) {
      assertThat(flowNode.getEndDate()).isNotNull();
      assertThat(flowNode.getStartDate()).isBeforeOrEqualTo(flowNode.getEndDate());
    } else {
      assertThat(flowNode.getEndDate()).isNull();
    }
    assertThat(flowNode.getType()).isEqualTo(type);
    return flowNode;
  }

  @Test
  @Ignore("OPE-1183")
  public void testActivityInstanceTreeIncidentStatePropagated() throws Exception {

    // having
    String processId = "prWithSubprocess";
    deployWorkflow("subProcess.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, null);
    ZeebeTestUtil.completeTask(zeebeClient, "taskA", getWorkerName(), null);
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, workflowInstanceKey, "taskB");
    ZeebeTestUtil.failTask(zeebeClient, "taskB", getWorkerName(), 3, "some error");
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, workflowInstanceKey);

    //when
    final String workflowInstanceId = String.valueOf(workflowInstanceKey);
    FlowNodeInstanceRequestDto request = new FlowNodeInstanceRequestDto(
        workflowInstanceId, workflowInstanceId);
    List<FlowNodeInstanceDto> instances = getFlowNodeInstanceTreeFromRest(request);

    //then
    assertThat(instances).filteredOn("flowNodeId", "subprocess").hasSize(1);
    final FlowNodeInstanceDto subprocess = instances.stream().filter(ai -> ai.getFlowNodeId().equals("subprocess"))
      .findFirst().get();
    assertThat(subprocess.getState()).isEqualTo(FlowNodeState.INCIDENT);
    assertThat(instances).filteredOn(ai -> !ai.getFlowNodeId().equals("subprocess")).allMatch(ai -> !ai.getState().equals(FlowNodeState.INCIDENT));

    assertThat(instances).filteredOn("flowNodeId", "innerSubprocess").hasSize(1);
    final FlowNodeInstanceDto innerSubprocess = instances.stream().filter(ai -> ai.getFlowNodeId().equals("innerSubprocess"))
      .findFirst().get();
    assertThat(innerSubprocess.getState()).isEqualTo(FlowNodeState.INCIDENT);
    assertThat(instances).filteredOn(ai -> !ai.getFlowNodeId().equals("innerSubprocess")).allMatch(ai -> !ai.getState().equals(FlowNodeState.INCIDENT));

    assertThat(instances).filteredOn("activityId", "taskB").allMatch(ai -> ai.getState().equals(
        FlowNodeState.INCIDENT));
    assertThat(instances).filteredOn(ai -> !ai.getFlowNodeId().equals("taskB")).allMatch(ai -> !ai.getState().equals(FlowNodeState.INCIDENT));

  }

  @Test
  public void testActivityInstanceTreeFails() throws Exception {
    ActivityInstanceTreeRequestDto treeRequest = new ActivityInstanceTreeRequestDto();

    MvcResult mvcResult = postRequestThatShouldFail(ACTIVITY_INSTANCE_URL,treeRequest);

    assertErrorMessageIsEqualTo(mvcResult, "Workflow instance id must be provided when requesting for activity instance tree.");
  }

  protected List<FlowNodeInstanceDto> getFlowNodeInstanceTreeFromRest(FlowNodeInstanceRequestDto request) throws Exception {
    MvcResult mvcResult = postRequest(FLOW_NODE_INSTANCE_URL,request);
    return mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() { });
  }

  protected FlowNodeInstanceMetadataDto getFlowNodeInstanceMetadataFromRest(String flowNodeInstanceId) throws Exception {
    MvcResult mvcResult = getRequest(String.format(FLOW_NODE_INSTANCE_URL + "/%s/metadata", flowNodeInstanceId));
    return mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() { });
  }

  @Test
  public void testMetadataForFinishedWorkflow() throws Exception {
    // having
    final String processId = "processWithGateway";
    final String taskA = "taskA";
    final String taskC = "taskC";
    final String errorMessage = "Some error";
    final Long workflowKey = deployWorkflow("processWithGateway.bpmn");

    final Long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(super.getClient(), processId, "{\"a\": \"b\"}");

    //create an incident
    /*final Long jobKey =*/ failTaskWithNoRetriesLeft(taskA, workflowInstanceKey, errorMessage);

    //update retries
    List<IncidentEntity> allIncidents = incidentReader.getAllIncidentsByWorkflowInstanceKey(workflowInstanceKey);
    assertThat(allIncidents).hasSize(1);
    ZeebeTestUtil.resolveIncident(zeebeClient, allIncidents.get(0).getJobKey(), allIncidents.get(0).getKey());
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsResolvedCheck, workflowInstanceKey);

    //complete task A
    completeTask(workflowInstanceKey, taskA, "{\"goToTaskC\":true}");

    //complete task C
    completeTask(workflowInstanceKey, taskC, "{\"b\": \"d\"}");

    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsCompletedCheck, workflowInstanceKey, taskC);
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstanceIsCompletedCheck, workflowInstanceKey);

    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //get flow node instance tree
    final String workflowInstanceId = String.valueOf(workflowInstanceKey);
    FlowNodeInstanceRequestDto request = new FlowNodeInstanceRequestDto(
        workflowInstanceId, workflowInstanceId);
    List<FlowNodeInstanceDto> instances = getFlowNodeInstanceTreeFromRest(request);

    assertMetadata(instances, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETED, processId, workflowKey, workflowInstanceKey, "start");
    try {
      assertMetadata(instances, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETED, processId, workflowKey, workflowInstanceKey, "taskA");
    } catch (AssertionError ae) {
      assertMetadata(instances, EventSourceType.JOB, EventType.COMPLETED, processId, workflowKey, workflowInstanceKey, "taskA");
    }
    assertMetadata(instances, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETED, processId, workflowKey, workflowInstanceKey, "gateway");
    try {
      assertMetadata(instances, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETED, processId, workflowKey, workflowInstanceKey, "taskC");
    } catch (AssertionError ae) {
      assertMetadata(instances, EventSourceType.JOB, EventType.COMPLETED, processId, workflowKey, workflowInstanceKey, "taskC");
    }
    assertMetadata(instances, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETED, processId, workflowKey, workflowInstanceKey, "end1");

  }

  @Test
  public void testMetadataForCanceledOnIncident() throws Exception {
    // having
    String flowNodeId = "taskA";

    String processId = "demoProcess";
    final Long workflowKey = deployWorkflow("demoProcess_v_1.bpmn");
    final Long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(super.getClient(), processId, null);
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, workflowInstanceKey);

    cancelWorkflowInstance(workflowInstanceKey);
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsTerminatedCheck, workflowInstanceKey, flowNodeId);
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstanceIsCanceledCheck, workflowInstanceKey);
    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when
    //get flow node instance tree
    final String workflowInstanceId = String.valueOf(workflowInstanceKey);
    FlowNodeInstanceRequestDto request = new FlowNodeInstanceRequestDto(
        workflowInstanceId, workflowInstanceId);
    List<FlowNodeInstanceDto> instances = getFlowNodeInstanceTreeFromRest(request);

    //then
    try {
      assertMetadata(instances, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_TERMINATED, processId, workflowKey, workflowInstanceKey, flowNodeId);
    } catch (AssertionError ae) {
      assertMetadata(instances, EventSourceType.INCIDENT, EventType.RESOLVED, processId, null, workflowInstanceKey, flowNodeId);
    }

  }

  @Test
  public void testMetadataIncidentOnInputMapping() throws Exception {
    // having
    String processId = "demoProcess";
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
        .startEvent("start")
        .serviceTask("task1").zeebeJobType("task1")
        .zeebeInput("=var", "varIn")
        .endEvent()
        .done();

    deployWorkflow(workflow, processId + ".bpmn");

    final Long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");      //wrong payload provokes incident
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, workflowInstanceKey);

    //when
    //get flow node instance tree
    final String workflowInstanceId = String.valueOf(workflowInstanceKey);
    FlowNodeInstanceRequestDto request = new FlowNodeInstanceRequestDto(
        workflowInstanceId, workflowInstanceId);
    List<FlowNodeInstanceDto> instances = getFlowNodeInstanceTreeFromRest(request);

    //then last event does not have a jobId
    final FlowNodeInstanceMetadataDto flowNodeInstanceMetadata = getFlowNodeInstanceMetadataFromRest(
        instances.get(instances.size() - 1).getId());
    try {
      assertThat(flowNodeInstanceMetadata.getEventSourceType()).isEqualTo(EventSourceType.INCIDENT);
      assertThat(flowNodeInstanceMetadata.getEventType()).isEqualTo(EventType.CREATED);
      assertThat(flowNodeInstanceMetadata.getMetadata().getJobId()).isNull();
    } catch (AssertionError ae) {
      assertThat(flowNodeInstanceMetadata.getEventSourceType()).isEqualTo(EventSourceType.WORKFLOW_INSTANCE);
      assertThat(flowNodeInstanceMetadata.getEventType()).isEqualTo(EventType.ELEMENT_ACTIVATING);
    }

  }

  public void assertMetadata(List<FlowNodeInstanceDto> flowNodes, EventSourceType eventSourceType, EventType eventType,
      String processId, Long workflowKey, long workflowInstanceKey) throws Exception {
    assertMetadata(flowNodes, eventSourceType, eventType, processId, workflowKey, workflowInstanceKey, null);
  }

  public void assertMetadata(List<FlowNodeInstanceDto> flowNodes, EventSourceType eventSourceType, EventType eventType,
      String processId, Long workflowKey, Long workflowInstanceKey, String activityId)
      throws Exception {
    assertMetadata(flowNodes, eventSourceType, eventType, processId, workflowKey, workflowInstanceKey, activityId, null);
  }

  public void assertMetadata(List<FlowNodeInstanceDto> flowNodes, EventSourceType eventSourceType, EventType eventType,
      String processId, Long workflowKey, Long workflowInstanceKey, String flowNodeId, String errorMessage)
      throws Exception {

    final Optional<FlowNodeInstanceDto> flowNodeInstance = flowNodes.stream()
        .filter(fni -> fni.getFlowNodeId().equals(flowNodeId)).findFirst();
    assertThat(flowNodeInstance).isNotEmpty();

    //call REST API to get metadata
    final FlowNodeInstanceMetadataDto flowNodeInstanceMetadata = getFlowNodeInstanceMetadataFromRest(
        flowNodeInstance.get().getId());

    String assertionName = String.format("%s.%s", eventSourceType, eventType);

    assertThat(flowNodeInstanceMetadata.getWorkflowInstanceId()).as(assertionName + ".workflowInstanceKey")
        .isEqualTo(String.valueOf(workflowInstanceKey));
    if (workflowKey != null) {
      assertThat(flowNodeInstanceMetadata.getWorkflowId()).as(assertionName + ".workflowKey")
          .isEqualTo(String.valueOf(workflowKey));
    }
    assertThat(flowNodeInstanceMetadata.getDateTime()).as(assertionName + ".dateTimeAfter")
        .isAfterOrEqualTo(testStartTime);
    assertThat(flowNodeInstanceMetadata.getDateTime()).as(assertionName + ".dateTimeBefore").isBeforeOrEqualTo(
        OffsetDateTime.now());
    assertThat(flowNodeInstanceMetadata.getBpmnProcessId()).as(assertionName + ".bpmnProcessId")
        .isEqualTo(processId);
    assertThat(flowNodeInstanceMetadata.getFlowNodeId()).as(assertionName + ".flowNodeId")
          .isEqualTo(flowNodeId);
    if (!flowNodeInstanceMetadata.getId().equals(flowNodeInstanceMetadata.getWorkflowInstanceId())) {
      assertThat(flowNodeInstanceMetadata.getWorkflowInstanceId()).as(assertionName + ".flowNodeInstanceKey")
          .isNotNull();
    }
    if (eventSourceType.equals(EventSourceType.INCIDENT)) {
      if (errorMessage != null) {
        assertThat(flowNodeInstanceMetadata.getMetadata().getIncidentErrorMessage())
            .as(assertionName + ".incidentErrorMessage").isEqualTo(errorMessage);
      } else {
        assertThat(flowNodeInstanceMetadata.getMetadata().getIncidentErrorMessage())
            .as(assertionName + ".incidentErrorMessage").isNotEmpty();
      }
      assertThat(flowNodeInstanceMetadata.getMetadata().getIncidentErrorType())
          .as(assertionName + ".incidentErrorType").isNotNull();
    }

  }


}
