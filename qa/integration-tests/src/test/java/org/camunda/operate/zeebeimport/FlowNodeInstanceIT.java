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
import java.util.List;
import org.camunda.operate.entities.FlowNodeState;
import org.camunda.operate.entities.FlowNodeType;
import org.camunda.operate.util.ConversionUtils;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.webapp.rest.dto.activity.ActivityInstanceTreeRequestDto;
import org.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceDto;
import org.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceRequestDto;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;

public class FlowNodeInstanceIT extends OperateZeebeIntegrationTest {

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
    final FlowNodeInstanceDto tasbBLast = assertChild(instances, 1, "taskB", FlowNodeState.COMPLETED,
        multiInstanceBody.getTreePath(), FlowNodeType.SERVICE_TASK);

    //when - test level 3 - searchBefore
    request = new FlowNodeInstanceRequestDto(
        workflowInstanceId, multiInstanceBody.getTreePath());
    request.setPageSize(4);
    request.setSearchBefore(tasbBLast.getSortValues());
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
    final FlowNodeInstanceDto innerSuprocess = instances.stream().filter(ai -> ai.getFlowNodeId().equals("innerSubprocess"))
      .findFirst().get();
    assertThat(innerSuprocess.getState()).isEqualTo(FlowNodeState.INCIDENT);
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

    MvcResult mvcResult =  postRequest(FLOW_NODE_INSTANCE_URL,request);

    return mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() { });
  }

}
