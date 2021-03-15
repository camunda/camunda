/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.webapp.rest.FlowNodeInstanceRestService.FLOW_NODE_INSTANCE_URL;

import com.fasterxml.jackson.core.type.TypeReference;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.camunda.operate.entities.FlowNodeState;
import org.camunda.operate.entities.FlowNodeType;
import org.camunda.operate.util.ConversionUtils;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.webapp.es.reader.IncidentReader;
import org.camunda.operate.webapp.rest.WorkflowInstanceRestService;
import org.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceDto;
import org.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceQueryDto;
import org.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceRequestDto;
import org.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceResponseDto;
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
    FlowNodeInstanceQueryDto request = new FlowNodeInstanceQueryDto(
        workflowInstanceId, workflowInstanceId);
    List<FlowNodeInstanceDto> instances = getFlowNodeInstanceOneListFromRest(request);

    //then
    assertThat(instances).hasSize(4);
    assertChild(instances, 0, "startEvent", FlowNodeState.COMPLETED, workflowInstanceId, FlowNodeType.START_EVENT);
    assertChild(instances, 1, "task1", FlowNodeState.ACTIVE, workflowInstanceId, FlowNodeType.SERVICE_TASK);
    assertChild(instances, 2, "boundaryEvent", FlowNodeState.COMPLETED, workflowInstanceId, FlowNodeType.BOUNDARY_EVENT);
    assertChild(instances, 3, "task2", FlowNodeState.ACTIVE, workflowInstanceId, FlowNodeType.SERVICE_TASK);
  }

  @Test
  public void testFlowNodeInstanceTreeSeveralQueriesInOne() throws Exception {
    //having process with multi-instance subprocess
    final String taskId = "taskA";
    final String workflowId = "testProcess";
    final BpmnModelInstance testProcess =
        Bpmn.createExecutableProcess(workflowId)
            .startEvent()
            .subProcess(
                "subprocess",
                s -> s.multiInstance(m -> m.zeebeInputCollectionExpression("items").parallel())
                    .embeddedSubProcess()
                    .startEvent()
                    .serviceTask("someTask").zeebeJobType(taskId)
                    .serviceTask(taskId).zeebeJobType(taskId)
                    .endEvent())
            .endEvent()
            .done();
    deployWorkflow(testProcess, workflowId + ".bpmn");
    final long workflowInstanceKey = ZeebeTestUtil
        .startWorkflowInstance(zeebeClient, workflowId, "{\"items\": [0, 1]}");
    ZeebeTestUtil.completeTask(zeebeClient, "taskA", getWorkerName(), null, 3);
    elasticsearchTestRule
        .processAllRecordsAndWait(flowNodeIsCompletedCheck, workflowInstanceKey, taskId);

    //find out subprocess instance ids
    final String workflowInstanceId = String.valueOf(workflowInstanceKey);
    List<FlowNodeInstanceDto> instances = getFlowNodeInstanceOneListFromRest(new FlowNodeInstanceQueryDto(
        workflowInstanceId, workflowInstanceId));
    final String multiInstanceBodyTreePath = workflowInstanceId + "/" + instances.get(1).getId();
    instances = getFlowNodeInstanceOneListFromRest(new FlowNodeInstanceQueryDto(workflowInstanceId,
        multiInstanceBodyTreePath));
    final List<String> subprocessInstanceIds = instances.stream()
        .filter(fni -> fni.getType().equals(FlowNodeType.SUB_PROCESS)).map(FlowNodeInstanceDto::getId)
        .collect(Collectors.toList());
    assertThat(subprocessInstanceIds).hasSize(2);

    //when querying for 1st level + 2 subprocess tree branches
    final String subprocess1ParentTreePath = String
        .format("%s/%s", multiInstanceBodyTreePath, subprocessInstanceIds.get(0));
    final String subprocess2ParentTreePath = String
        .format("%s/%s", multiInstanceBodyTreePath, subprocessInstanceIds.get(1));
    FlowNodeInstanceQueryDto query1 = new FlowNodeInstanceQueryDto(workflowInstanceId,
        workflowInstanceId);
    FlowNodeInstanceQueryDto query2 = new FlowNodeInstanceQueryDto(workflowInstanceId,
        subprocess1ParentTreePath);
    FlowNodeInstanceQueryDto query3 = new FlowNodeInstanceQueryDto(workflowInstanceId,
        subprocess2ParentTreePath);
    final Map<String, FlowNodeInstanceResponseDto> response = getFlowNodeInstanceListsFromRest(
        query1, query2, query3);

    //then
    final FlowNodeInstanceResponseDto level1Response = response.get(workflowInstanceId);
    assertThat(level1Response).isNotNull();
    assertThat(level1Response.getRunning()).isNull();    //on workflow instance level we don't know if parent is running -> returning null
    assertThat(level1Response.getChildren()).hasSize(2);

    int countRunningResponses = 0;
    int countFinishedTasks = 0;

    final FlowNodeInstanceResponseDto level3Response1 = response.get(subprocess1ParentTreePath);
    assertThat(level3Response1).isNotNull();
    assertThat(level3Response1.getRunning()).isNotNull();
    if (level3Response1.getRunning()) { countRunningResponses++;}
    countFinishedTasks += level3Response1.getChildren().stream().filter(
        fni -> fni.getType().equals(FlowNodeType.SERVICE_TASK) && fni.getState()
            .equals(FlowNodeState.COMPLETED)).count();


    final FlowNodeInstanceResponseDto level3Response2 = response.get(subprocess2ParentTreePath);
    assertThat(level3Response2).isNotNull();
    assertThat(level3Response2.getRunning()).isNotNull();
    if (level3Response2.getRunning()) { countRunningResponses++;}
    countFinishedTasks += level3Response2.getChildren().stream().filter(
        fni -> fni.getType().equals(FlowNodeType.SERVICE_TASK) && fni.getState()
            .equals(FlowNodeState.COMPLETED)).count();

    assertThat(countRunningResponses).isEqualTo(1);     //only one of subprocesses is still running
    assertThat(countFinishedTasks).isEqualTo(3);        //3 out of 4 tasks are finished
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
    FlowNodeInstanceQueryDto request = new FlowNodeInstanceQueryDto(
        workflowInstanceId, workflowInstanceId);
    List<FlowNodeInstanceDto> instances = getFlowNodeInstanceOneListFromRest(request);

    //then
    assertThat(instances).hasSize(3);
    assertChild(instances, 0, "startEvent", FlowNodeState.COMPLETED, workflowInstanceId, FlowNodeType.START_EVENT);
    assertChild(instances, 1, "taskA", FlowNodeState.COMPLETED, workflowInstanceId, FlowNodeType.SERVICE_TASK);
    final FlowNodeInstanceDto subprocess = assertChild(instances, 2, "subprocess",
        FlowNodeState.INCIDENT, workflowInstanceId, FlowNodeType.SUB_PROCESS);

    //when - test level 1
    request = new FlowNodeInstanceQueryDto(
        workflowInstanceId, subprocess.getTreePath());
    instances = getFlowNodeInstanceOneListFromRest(request);

    //then
    assertThat(instances).hasSize(3);
    assertChild(instances, 0, "startEventSubprocess", FlowNodeState.COMPLETED, subprocess.getTreePath(), FlowNodeType.START_EVENT);
    assertChild(instances, 2, "taskC", FlowNodeState.INCIDENT, subprocess.getTreePath(), FlowNodeType.SERVICE_TASK);
    final FlowNodeInstanceDto innerSubprocess = assertChild(instances, 1, "innerSubprocess",
        FlowNodeState.COMPLETED, subprocess.getTreePath(), FlowNodeType.SUB_PROCESS);

    //when - test level 2 - multi-instance body
    request = new FlowNodeInstanceQueryDto(
        workflowInstanceId, innerSubprocess.getTreePath());
    instances = getFlowNodeInstanceOneListFromRest(request);

    //then
    assertThat(instances).hasSize(3);
    assertChild(instances, 0, "startEventInnerSubprocess", FlowNodeState.COMPLETED, innerSubprocess.getTreePath(), FlowNodeType.START_EVENT);
    final FlowNodeInstanceDto multiInstanceBody = assertChild(instances, 1, "taskB", FlowNodeState.COMPLETED,
        innerSubprocess.getTreePath(), FlowNodeType.MULTI_INSTANCE_BODY);
    assertChild(instances, 2,
        "endEventInnerSubprocess", FlowNodeState.COMPLETED, innerSubprocess.getTreePath(),
        FlowNodeType.END_EVENT);

    final String multiInstanceBodyTreePath = multiInstanceBody.getTreePath();

    assertPagesWithSearchAfterBefore(workflowInstanceId, multiInstanceBodyTreePath);
    assertPagesWithSearchAfterBeforeOrEqual(workflowInstanceId, multiInstanceBodyTreePath);

  }

  private void assertPagesWithSearchAfterBefore(final String workflowInstanceId,
      final String multiInstanceBodyTreePath) throws Exception {

    //when - test level 3 - page 1
    FlowNodeInstanceQueryDto request = new FlowNodeInstanceQueryDto(
        workflowInstanceId, multiInstanceBodyTreePath);
    request.setPageSize(4);
    List<FlowNodeInstanceDto> instances = getFlowNodeInstanceOneListFromRest(request);

    //then
    assertThat(instances).hasSize(4);
    assertChild(instances, 0, "taskB", FlowNodeState.COMPLETED, multiInstanceBodyTreePath,
        FlowNodeType.SERVICE_TASK);
    assertChild(instances, 1, "taskB", FlowNodeState.COMPLETED, multiInstanceBodyTreePath,
        FlowNodeType.SERVICE_TASK);
    assertChild(instances, 2, "taskB", FlowNodeState.COMPLETED, multiInstanceBodyTreePath,
        FlowNodeType.SERVICE_TASK);
    final FlowNodeInstanceDto lastTaskPage1 = assertChild(instances, 3, "taskB",
        FlowNodeState.COMPLETED,
        multiInstanceBodyTreePath, FlowNodeType.SERVICE_TASK);

    //when - test level 3 - page 2
    request = new FlowNodeInstanceQueryDto(
        workflowInstanceId, multiInstanceBodyTreePath);
    request.setPageSize(5);
    request.setSearchAfter(lastTaskPage1.getSortValues());
    instances = getFlowNodeInstanceOneListFromRest(request);

    //then
    assertThat(instances).hasSize(2);
    final FlowNodeInstanceDto taskBBeforeLast = assertChild(instances, 0, "taskB",
        FlowNodeState.COMPLETED,
        multiInstanceBodyTreePath, FlowNodeType.SERVICE_TASK);
    final FlowNodeInstanceDto taskBLast = assertChild(instances, 1, "taskB",
        FlowNodeState.COMPLETED,
        multiInstanceBodyTreePath, FlowNodeType.SERVICE_TASK);

    //when - test level 3 - searchBefore
    request = new FlowNodeInstanceQueryDto(
        workflowInstanceId, multiInstanceBodyTreePath);
    request.setPageSize(4);
    request.setSearchBefore(taskBLast.getSortValues());
    instances = getFlowNodeInstanceOneListFromRest(request);

    //then
    assertThat(instances).hasSize(4);
    for (int i = 0; i < 3; i++) {
      assertChild(instances, i, "taskB", FlowNodeState.COMPLETED, multiInstanceBodyTreePath,
          FlowNodeType.SERVICE_TASK);
    }
    assertThat(assertChild(instances, 3, "taskB", FlowNodeState.COMPLETED,
        multiInstanceBodyTreePath, FlowNodeType.SERVICE_TASK).getId())
        .isEqualTo(taskBBeforeLast.getId());
  }

  private void assertPagesWithSearchAfterBeforeOrEqual(final String workflowInstanceId,
      final String multiInstanceBodyTreePath) throws Exception {

    //when - test level 3 - page 1
    FlowNodeInstanceQueryDto request = new FlowNodeInstanceQueryDto(
        workflowInstanceId, multiInstanceBodyTreePath);
    request.setPageSize(4);
    List<FlowNodeInstanceDto> instances = getFlowNodeInstanceOneListFromRest(request);

    //then
    assertThat(instances).hasSize(4);
    final FlowNodeInstanceDto lastTaskPage1 = assertChild(instances, 3, "taskB",
        FlowNodeState.COMPLETED,
        multiInstanceBodyTreePath, FlowNodeType.SERVICE_TASK);

    //when - test level 3 - same page 1 with searchAfterOrEqual
    request.setSearchAfterOrEqual(instances.get(0).getSortValues());
    assertThat(getFlowNodeInstanceOneListFromRest(request))
        .containsExactly(instances.stream().toArray(FlowNodeInstanceDto[]::new));

    //when - test level 3 - page 2
    request = new FlowNodeInstanceQueryDto(
        workflowInstanceId, multiInstanceBodyTreePath)
        .setPageSize(5)
        .setSearchAfter(lastTaskPage1.getSortValues());
    instances = getFlowNodeInstanceOneListFromRest(request);

    //then - test level 3 - same page 2 with searchBeforeorEqual
    assertThat(instances).hasSize(2);
    request = new FlowNodeInstanceQueryDto(
        workflowInstanceId, multiInstanceBodyTreePath)
        .setPageSize(2)
        .setSearchBeforeOrEqual(instances.get(instances.size() - 1).getSortValues());
    assertThat(getFlowNodeInstanceOneListFromRest(request))
        .containsExactly(instances.stream().toArray(FlowNodeInstanceDto[]::new));

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
  public void testFlowNodeInstanceTreeIncidentStatePropagated() throws Exception {

    // having
    String processId = "prWithSubprocess";
    deployWorkflow("subProcess.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil
        .startWorkflowInstance(zeebeClient, processId, "{\"items\": [0]}");
    ZeebeTestUtil.completeTask(zeebeClient, "taskA", getWorkerName(), null);
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, workflowInstanceKey, "taskB");
    ZeebeTestUtil.failTask(zeebeClient, "taskB", getWorkerName(), 3, "some error");
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, workflowInstanceKey);

    //when
    final String workflowInstanceId = String.valueOf(workflowInstanceKey);
    FlowNodeInstanceQueryDto request = new FlowNodeInstanceQueryDto(
        workflowInstanceId, workflowInstanceId);
    List<FlowNodeInstanceDto> instances = getFlowNodeInstanceOneListFromRest(request);

    //then
    //level 1
    assertThat(instances).filteredOn("flowNodeId", "subprocess").hasSize(1);
    final FlowNodeInstanceDto subprocess = instances.stream().filter(ai -> ai.getFlowNodeId().equals("subprocess"))
      .findFirst().get();
    assertThat(subprocess.getState()).isEqualTo(FlowNodeState.INCIDENT);
    assertThat(instances).filteredOn(ai -> !ai.getFlowNodeId().equals("subprocess")).allMatch(ai -> !ai.getState().equals(FlowNodeState.INCIDENT));

    //level 2
    request = new FlowNodeInstanceQueryDto(workflowInstanceId, subprocess.getTreePath());
    instances = getFlowNodeInstanceOneListFromRest(request);
    assertThat(instances).filteredOn("flowNodeId", "innerSubprocess").hasSize(1);
    final FlowNodeInstanceDto innerSubprocess = instances.stream().filter(ai -> ai.getFlowNodeId().equals("innerSubprocess"))
      .findFirst().get();
    assertThat(innerSubprocess.getState()).isEqualTo(FlowNodeState.INCIDENT);
    assertThat(instances).filteredOn(ai -> !ai.getFlowNodeId().equals("innerSubprocess")).allMatch(ai -> !ai.getState().equals(FlowNodeState.INCIDENT));

    //level 3
    request = new FlowNodeInstanceQueryDto(workflowInstanceId, innerSubprocess.getTreePath());
    instances = getFlowNodeInstanceOneListFromRest(request);
    assertThat(instances).filteredOn("flowNodeId", "taskB").allMatch(ai -> ai.getState().equals(
        FlowNodeState.INCIDENT));
    assertThat(instances).filteredOn(ai -> !ai.getFlowNodeId().equals("taskB")).allMatch(ai -> !ai.getState().equals(FlowNodeState.INCIDENT));

  }

  @Test
  public void testFlowNodeInstanceTreeIncidentStatePropagatedWithPageSize() throws Exception {

    // having
    String processId = "prWithSubprocess";
    deployWorkflow("subProcess.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil
        .startWorkflowInstance(zeebeClient, processId, "{\"items\": [0]}");
    ZeebeTestUtil.completeTask(zeebeClient, "taskA", getWorkerName(), null);
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, workflowInstanceKey, "taskB");
    ZeebeTestUtil.failTask(zeebeClient, "taskB", getWorkerName(), 3, "some error");
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, workflowInstanceKey);

    //when
    final String workflowInstanceId = String.valueOf(workflowInstanceKey);
    FlowNodeInstanceQueryDto request = new FlowNodeInstanceQueryDto(
        workflowInstanceId, workflowInstanceId).setPageSize(3);
    List<FlowNodeInstanceDto> instances = getFlowNodeInstanceOneListFromRest(request);

    //then
    //level 1
    assertThat(instances).filteredOn("flowNodeId", "subprocess").hasSize(1);
    final FlowNodeInstanceDto subprocess = instances.stream().filter(ai -> ai.getFlowNodeId().equals("subprocess"))
      .findFirst().get();
    assertThat(subprocess.getState()).isEqualTo(FlowNodeState.INCIDENT);
    assertThat(instances).filteredOn(ai -> !ai.getFlowNodeId().equals("subprocess")).allMatch(ai -> !ai.getState().equals(FlowNodeState.INCIDENT));

    //level 2
    request = new FlowNodeInstanceQueryDto(workflowInstanceId, subprocess.getTreePath()).setPageSize(2);
    instances = getFlowNodeInstanceOneListFromRest(request);
    assertThat(instances).filteredOn("flowNodeId", "innerSubprocess").hasSize(1);
    final FlowNodeInstanceDto innerSubprocess = instances.stream().filter(ai -> ai.getFlowNodeId().equals("innerSubprocess"))
      .findFirst().get();
    assertThat(innerSubprocess.getState()).isEqualTo(FlowNodeState.INCIDENT);
    assertThat(instances).filteredOn(ai -> !ai.getFlowNodeId().equals("innerSubprocess")).allMatch(ai -> !ai.getState().equals(FlowNodeState.INCIDENT));

    //level 3
    request = new FlowNodeInstanceQueryDto(workflowInstanceId, innerSubprocess.getTreePath()).setPageSize(2);
    instances = getFlowNodeInstanceOneListFromRest(request);
    assertThat(instances).filteredOn("flowNodeId", "taskB").allMatch(ai -> ai.getState().equals(
        FlowNodeState.INCIDENT));
    assertThat(instances).filteredOn(ai -> !ai.getFlowNodeId().equals("taskB")).allMatch(ai -> !ai.getState().equals(FlowNodeState.INCIDENT));

  }

  @Test
  public void testFlowNodeInstanceTreeFailsWithAbsentQueries() throws Exception {
    FlowNodeInstanceRequestDto treeRequest = new FlowNodeInstanceRequestDto();

    MvcResult mvcResult = postRequestThatShouldFail(FLOW_NODE_INSTANCE_URL, treeRequest);

    assertErrorMessageIsEqualTo(mvcResult, "At least one query must be provided when requesting for flow node instance tree.");
  }

  @Test
  public void testFlowNodeInstanceTreeFailsWithEmptyWorkflowInstanceIdOrTreePath() throws Exception {
    FlowNodeInstanceRequestDto treeRequest = new FlowNodeInstanceRequestDto(new FlowNodeInstanceQueryDto());
    MvcResult mvcResult = postRequestThatShouldFail(FLOW_NODE_INSTANCE_URL, treeRequest);
    assertErrorMessageIsEqualTo(mvcResult, "Workflow instance id and tree path must be provided when requesting for flow node instance tree.");

    treeRequest = new FlowNodeInstanceRequestDto(new FlowNodeInstanceQueryDto().setWorkflowInstanceId("some"));
    mvcResult = postRequestThatShouldFail(FLOW_NODE_INSTANCE_URL, treeRequest);
    assertErrorMessageIsEqualTo(mvcResult, "Workflow instance id and tree path must be provided when requesting for flow node instance tree.");

    treeRequest = new FlowNodeInstanceRequestDto(new FlowNodeInstanceQueryDto().setTreePath("some"));
    mvcResult = postRequestThatShouldFail(FLOW_NODE_INSTANCE_URL, treeRequest);
    assertErrorMessageIsEqualTo(mvcResult, "Workflow instance id and tree path must be provided when requesting for flow node instance tree.");
  }

  @Test
  public void testFlowNodeInstanceTreeFailsWithWrongSearchAfter() throws Exception {
    FlowNodeInstanceQueryDto treeQuery = new FlowNodeInstanceQueryDto()
        .setWorkflowInstanceId("123")
        .setTreePath("123")
        .setSearchAfter(new String[]{})
        .setSearchBeforeOrEqual(new String[]{});
    FlowNodeInstanceRequestDto treeRequest = new FlowNodeInstanceRequestDto(treeQuery);

    MvcResult mvcResult = postRequestThatShouldFail(FLOW_NODE_INSTANCE_URL, treeRequest);
    assertErrorMessageIsEqualTo(mvcResult, "Only one of [searchAfter, searchAfterOrEqual, searchBefore, searchBeforeOrEqual] must be present in request.");

    treeQuery = new FlowNodeInstanceQueryDto()
        .setWorkflowInstanceId("123")
        .setTreePath("123")
        .setSearchBefore(new String[]{})
        .setSearchBeforeOrEqual(new String[]{});

    mvcResult = postRequestThatShouldFail(FLOW_NODE_INSTANCE_URL, new FlowNodeInstanceRequestDto(treeQuery));
    assertErrorMessageIsEqualTo(mvcResult, "Only one of [searchAfter, searchAfterOrEqual, searchBefore, searchBeforeOrEqual] must be present in request.");

    treeQuery = new FlowNodeInstanceQueryDto()
        .setWorkflowInstanceId("123")
        .setTreePath("123")
        .setSearchAfter(new String[]{})
        .setSearchAfterOrEqual(new String[]{});

    mvcResult = postRequestThatShouldFail(FLOW_NODE_INSTANCE_URL, new FlowNodeInstanceRequestDto(treeQuery));
    assertErrorMessageIsEqualTo(mvcResult, "Only one of [searchAfter, searchAfterOrEqual, searchBefore, searchBeforeOrEqual] must be present in request.");

  }

  protected Map<String, FlowNodeInstanceResponseDto> getFlowNodeInstanceListsFromRest(
      FlowNodeInstanceQueryDto... queries) throws Exception {
    FlowNodeInstanceRequestDto request = new FlowNodeInstanceRequestDto(queries);
    MvcResult mvcResult = postRequest(FLOW_NODE_INSTANCE_URL, request);
    return mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() { });
  }

  protected List<FlowNodeInstanceDto> getFlowNodeInstanceOneListFromRest(
      FlowNodeInstanceQueryDto query) throws Exception {
    FlowNodeInstanceRequestDto request = new FlowNodeInstanceRequestDto(query);
    MvcResult mvcResult = postRequest(FLOW_NODE_INSTANCE_URL, request);
    final Map<String, FlowNodeInstanceResponseDto> response = mockMvcTestRule
        .fromResponse(mvcResult, new TypeReference<>() {
        });
    assertThat(response).hasSize(1);
    return response.values().iterator().next().getChildren();
  }

  protected Map<String, FlowNodeState> getFlowNodeStatesFromRest(String workflowInstanceId) throws Exception {
    MvcResult mvcResult = getRequest(String.format(WorkflowInstanceRestService.WORKFLOW_INSTANCE_URL + "/%s/flow-node-states", workflowInstanceId));
    return mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() { });
  }

  @Test
  public void testFlowNodeStatesIncidentIsPropagated() throws Exception {
    // having
    String processId = "prWithSubprocess";
    deployWorkflow("subProcess.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil
        .startWorkflowInstance(zeebeClient, processId, "{\"items\": [0]}");
    ZeebeTestUtil.completeTask(zeebeClient, "taskA", getWorkerName(), null);
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, workflowInstanceKey, "taskB");
    ZeebeTestUtil.failTask(zeebeClient, "taskB", getWorkerName(), 3, "some error");
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, workflowInstanceKey);

    //when
    final Map<String, FlowNodeState> flowNodeStates = getFlowNodeStatesFromRest(
        String.valueOf(workflowInstanceKey));

    //then
    assertThat(flowNodeStates).hasSize(7);
    assertFlowNodeState(flowNodeStates, "startEvent", FlowNodeState.COMPLETED);
    assertFlowNodeState(flowNodeStates, "taskA", FlowNodeState.COMPLETED);
    assertFlowNodeState(flowNodeStates, "subprocess", FlowNodeState.INCIDENT);
    assertFlowNodeState(flowNodeStates, "startEventSubprocess", FlowNodeState.COMPLETED);
    assertFlowNodeState(flowNodeStates, "innerSubprocess", FlowNodeState.INCIDENT);
    assertFlowNodeState(flowNodeStates, "startEventInnerSubprocess", FlowNodeState.COMPLETED);
    assertFlowNodeState(flowNodeStates, "taskB", FlowNodeState.INCIDENT);
  }

  @Test
  public void testFlowNodeStates() throws Exception {
    // having
    String processId = "prWithSubprocess";
    deployWorkflow("subProcess.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil
        .startWorkflowInstance(zeebeClient, processId, "{\"items\": [0,1,2,3,4,5,6,7,8,9]}");
    ZeebeTestUtil.completeTask(zeebeClient, "taskA", getWorkerName(), null);
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, workflowInstanceKey, "taskB");
    ZeebeTestUtil.completeTask(zeebeClient, "taskB", getWorkerName(), null, 9);
    elasticsearchTestRule.processAllRecordsAndWait(flowNodesAreCompletedCheck, workflowInstanceKey, "taskB", 9);

    //when
    final Map<String, FlowNodeState> flowNodeStates = getFlowNodeStatesFromRest(
        String.valueOf(workflowInstanceKey));

    //then
    assertThat(flowNodeStates).hasSize(7);
    assertFlowNodeState(flowNodeStates, "startEvent", FlowNodeState.COMPLETED);
    assertFlowNodeState(flowNodeStates, "taskA", FlowNodeState.COMPLETED);
    assertFlowNodeState(flowNodeStates, "subprocess", FlowNodeState.ACTIVE);
    assertFlowNodeState(flowNodeStates, "startEventSubprocess", FlowNodeState.COMPLETED);
    assertFlowNodeState(flowNodeStates, "innerSubprocess", FlowNodeState.ACTIVE);
    assertFlowNodeState(flowNodeStates, "startEventInnerSubprocess", FlowNodeState.COMPLETED);
    assertFlowNodeState(flowNodeStates, "taskB", FlowNodeState.ACTIVE);
  }

  private void assertFlowNodeState(Map<String, FlowNodeState> flowNodeStates, String flowNodeId, FlowNodeState... states) {
    assertThat(flowNodeStates.get(flowNodeId)).isIn(states);
  }

}
