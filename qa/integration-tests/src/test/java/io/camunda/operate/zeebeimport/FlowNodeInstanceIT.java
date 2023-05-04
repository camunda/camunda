/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport;

import static org.assertj.core.api.Assertions.assertThat;
import static io.camunda.operate.webapp.rest.FlowNodeInstanceRestService.FLOW_NODE_INSTANCE_URL;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeStateDto;
import io.camunda.operate.webapp.rest.dto.listview.SortValuesWrapper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.util.OperateZeebeIntegrationTest;
import io.camunda.operate.util.ZeebeTestUtil;
import io.camunda.operate.webapp.es.reader.IncidentReader;
import io.camunda.operate.webapp.rest.ProcessInstanceRestService;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceQueryDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceRequestDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceResponseDto;
import io.camunda.operate.webapp.zeebe.operation.CancelProcessInstanceHandler;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

public class FlowNodeInstanceIT extends OperateZeebeIntegrationTest {

  @Autowired
  private IncidentReader incidentReader;

  @Autowired
  private CancelProcessInstanceHandler cancelProcessInstanceHandler;

  @Before
  public void setup() {
    cancelProcessInstanceHandler.setZeebeClient(getClient());
  }

  @Test
  public void testFlowNodeInstanceTreeForNonInterruptingBoundaryEvent() throws Exception {
    // having
    String processId = "nonInterruptingBoundaryEvent";
    deployProcess("nonInterruptingBoundaryEvent_v_2.bpmn");
    final Long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, null);
    //let the boundary event happen
    //Thread.sleep(1500L);
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "task2");

    //when
    final String processInstanceId = String.valueOf(processInstanceKey);
    FlowNodeInstanceQueryDto request = new FlowNodeInstanceQueryDto(
        processInstanceId, processInstanceId);
    List<FlowNodeInstanceDto> instances = getFlowNodeInstanceOneListFromRest(request);

    //then
    assertThat(instances).hasSize(4);
    assertChild(instances, 0, "startEvent", FlowNodeStateDto.COMPLETED, processInstanceId, FlowNodeType.START_EVENT);
    assertChild(instances, 1, "task1", FlowNodeStateDto.ACTIVE, processInstanceId, FlowNodeType.SERVICE_TASK);
    assertChild(instances, 2, "boundaryEvent", FlowNodeStateDto.COMPLETED, processInstanceId, FlowNodeType.BOUNDARY_EVENT);
    assertChild(instances, 3, "task2", FlowNodeStateDto.ACTIVE, processInstanceId, FlowNodeType.SERVICE_TASK);
  }

  @Test
  public void testFlowNodeInstanceTreeSeveralQueriesInOne() throws Exception {
    //having process with multi-instance subprocess
    final String jobKey = "taskA";
    final String flowNodeId1 = "task1";
    final String flowNodeId2 = "task2";
    final String processId = "testProcess";
    String subprocessFlowNodeId = "subprocess";
    final BpmnModelInstance testProcess =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .subProcess(subprocessFlowNodeId,
                s -> s.multiInstance(m -> m.zeebeInputCollectionExpression("items").parallel())
                    .embeddedSubProcess()
                    .startEvent()
                    .serviceTask(flowNodeId1).zeebeJobType(jobKey)
                    .serviceTask(flowNodeId2).zeebeJobType(jobKey)
                    .endEvent())
            .endEvent()
            .done();
    deployProcess(testProcess, processId + ".bpmn");
    final long processInstanceKey = ZeebeTestUtil
        .startProcessInstance(zeebeClient, processId, "{\"items\": [0, 1]}");
    ZeebeTestUtil.completeTask(zeebeClient, jobKey, getWorkerName(), null, 3);
    elasticsearchTestRule
        .processAllRecordsAndWait(flowNodesAreCompletedCheck, processInstanceKey, flowNodeId1, 2);
    elasticsearchTestRule
        .processAllRecordsAndWait(flowNodesAreCompletedCheck, processInstanceKey, subprocessFlowNodeId, 1);

    //find out subprocess instance ids
    final String processInstanceId = String.valueOf(processInstanceKey);
    List<FlowNodeInstanceDto> instances = getFlowNodeInstanceOneListFromRest(new FlowNodeInstanceQueryDto(
        processInstanceId, processInstanceId));
    final String multiInstanceBodyTreePath = processInstanceId + "/" + instances.get(1).getId();
    instances = getFlowNodeInstanceOneListFromRest(new FlowNodeInstanceQueryDto(processInstanceId,
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
    FlowNodeInstanceQueryDto query1 = new FlowNodeInstanceQueryDto(processInstanceId,
        processInstanceId);
    FlowNodeInstanceQueryDto query2 = new FlowNodeInstanceQueryDto(processInstanceId,
        subprocess1ParentTreePath);
    FlowNodeInstanceQueryDto query3 = new FlowNodeInstanceQueryDto(processInstanceId,
        subprocess2ParentTreePath);
    final Map<String, FlowNodeInstanceResponseDto> response = getFlowNodeInstanceListsFromRest(
        query1, query2, query3);

    //then
    final FlowNodeInstanceResponseDto level1Response = response.get(processInstanceId);
    assertThat(level1Response).isNotNull();
    assertThat(level1Response.getRunning()).isNull();    //on process instance level we don't know if parent is running -> returning null
    assertThat(level1Response.getChildren()).hasSize(2);

    int countRunningResponses = 0;
    int countFinishedTasks = 0;

    final FlowNodeInstanceResponseDto level3Response1 = response.get(subprocess1ParentTreePath);
    assertThat(level3Response1).isNotNull();
    assertThat(level3Response1.getRunning()).isNotNull();
    if (level3Response1.getRunning()) { countRunningResponses++;}
    countFinishedTasks += level3Response1.getChildren().stream().filter(
        fni -> fni.getType().equals(FlowNodeType.SERVICE_TASK) && fni.getState()
            .equals(FlowNodeStateDto.COMPLETED)).count();


    final FlowNodeInstanceResponseDto level3Response2 = response.get(subprocess2ParentTreePath);
    assertThat(level3Response2).isNotNull();
    assertThat(level3Response2.getRunning()).isNotNull();
    if (level3Response2.getRunning()) { countRunningResponses++;}
    countFinishedTasks += level3Response2.getChildren().stream().filter(
        fni -> fni.getType().equals(FlowNodeType.SERVICE_TASK) && fni.getState()
            .equals(FlowNodeStateDto.COMPLETED)).count();

    assertThat(countRunningResponses).isEqualTo(1);     //only one of subprocesses is still running
    assertThat(countFinishedTasks).isEqualTo(3);        //3 out of 4 tasks are finished
  }

  @Test
  public void testFlowNodeInstanceTreeIsBuild() throws Exception {
    // having
    String processId = "prWithSubprocess";
    deployProcess("subProcess.bpmn");
    final Long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"items\": [0, 1, 2, 3, 4, 5]}");
    ZeebeTestUtil.completeTask(zeebeClient, "taskA", getWorkerName(), null);
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "taskB");
    ZeebeTestUtil.completeTask(zeebeClient, "taskB", getWorkerName(), null, 6);
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "taskC");
    ZeebeTestUtil.failTask(zeebeClient, "taskC", getWorkerName(), 3, "some error");
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, processInstanceKey);

    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when - test level 0
    final String processInstanceId = String.valueOf(processInstanceKey);
    FlowNodeInstanceQueryDto request = new FlowNodeInstanceQueryDto(
        processInstanceId, processInstanceId);
    List<FlowNodeInstanceDto> instances = getFlowNodeInstanceOneListFromRest(request);

    //then
    assertThat(instances).hasSize(3);
    assertChild(instances, 0, "startEvent", FlowNodeStateDto.COMPLETED, processInstanceId, FlowNodeType.START_EVENT);
    assertChild(instances, 1, "taskA", FlowNodeStateDto.COMPLETED, processInstanceId, FlowNodeType.SERVICE_TASK);
    final FlowNodeInstanceDto subprocess = assertChild(instances, 2, "subprocess",
        FlowNodeStateDto.INCIDENT, processInstanceId, FlowNodeType.SUB_PROCESS);

    //when - test level 1
    request = new FlowNodeInstanceQueryDto(
        processInstanceId, subprocess.getTreePath());
    instances = getFlowNodeInstanceOneListFromRest(request);

    //then
    assertThat(instances).hasSize(3);
    assertChild(instances, 0, "startEventSubprocess", FlowNodeStateDto.COMPLETED, subprocess.getTreePath(), FlowNodeType.START_EVENT);
    assertChild(instances, 2, "taskC", FlowNodeStateDto.INCIDENT, subprocess.getTreePath(), FlowNodeType.SERVICE_TASK);
    final FlowNodeInstanceDto innerSubprocess = assertChild(instances, 1, "innerSubprocess",
        FlowNodeStateDto.COMPLETED, subprocess.getTreePath(), FlowNodeType.SUB_PROCESS);

    //when - test level 2 - multi-instance body
    request = new FlowNodeInstanceQueryDto(
        processInstanceId, innerSubprocess.getTreePath());
    instances = getFlowNodeInstanceOneListFromRest(request);

    //then
    assertThat(instances).hasSize(3);
    assertChild(instances, 0, "startEventInnerSubprocess", FlowNodeStateDto.COMPLETED, innerSubprocess.getTreePath(), FlowNodeType.START_EVENT);
    final FlowNodeInstanceDto multiInstanceBody = assertChild(instances, 1, "taskB", FlowNodeStateDto.COMPLETED,
        innerSubprocess.getTreePath(), FlowNodeType.MULTI_INSTANCE_BODY);
    assertChild(instances, 2,
        "endEventInnerSubprocess", FlowNodeStateDto.COMPLETED, innerSubprocess.getTreePath(),
        FlowNodeType.END_EVENT);

    final String multiInstanceBodyTreePath = multiInstanceBody.getTreePath();

    assertPagesWithSearchAfterBefore(processInstanceId, multiInstanceBodyTreePath);
    assertPagesWithSearchAfterBeforeOrEqual(processInstanceId, multiInstanceBodyTreePath);

  }

  private void assertPagesWithSearchAfterBefore(final String processInstanceId,
      final String multiInstanceBodyTreePath) throws Exception {

    //when - test level 3 - page 1
    FlowNodeInstanceQueryDto request = new FlowNodeInstanceQueryDto(
        processInstanceId, multiInstanceBodyTreePath);
    request.setPageSize(4);
    List<FlowNodeInstanceDto> instances = getFlowNodeInstanceOneListFromRest(request);

    //then
    assertThat(instances).hasSize(4);
    assertChild(instances, 0, "taskB", FlowNodeStateDto.COMPLETED, multiInstanceBodyTreePath,
        FlowNodeType.SERVICE_TASK);
    assertChild(instances, 1, "taskB", FlowNodeStateDto.COMPLETED, multiInstanceBodyTreePath,
        FlowNodeType.SERVICE_TASK);
    assertChild(instances, 2, "taskB", FlowNodeStateDto.COMPLETED, multiInstanceBodyTreePath,
        FlowNodeType.SERVICE_TASK);
    final FlowNodeInstanceDto lastTaskPage1 = assertChild(instances, 3, "taskB",
        FlowNodeStateDto.COMPLETED,
        multiInstanceBodyTreePath, FlowNodeType.SERVICE_TASK);

    //when - test level 3 - page 2
    request = new FlowNodeInstanceQueryDto(
        processInstanceId, multiInstanceBodyTreePath);
    request.setPageSize(5);
    request.setSearchAfter(lastTaskPage1.getSortValues());
    instances = getFlowNodeInstanceOneListFromRest(request);

    //then
    assertThat(instances).hasSize(2);
    final FlowNodeInstanceDto taskBBeforeLast = assertChild(instances, 0, "taskB",
        FlowNodeStateDto.COMPLETED,
        multiInstanceBodyTreePath, FlowNodeType.SERVICE_TASK);
    final FlowNodeInstanceDto taskBLast = assertChild(instances, 1, "taskB",
        FlowNodeStateDto.COMPLETED,
        multiInstanceBodyTreePath, FlowNodeType.SERVICE_TASK);

    //when - test level 3 - searchBefore
    request = new FlowNodeInstanceQueryDto(
        processInstanceId, multiInstanceBodyTreePath);
    request.setPageSize(4);
    request.setSearchBefore(taskBLast.getSortValues());
    instances = getFlowNodeInstanceOneListFromRest(request);

    //then
    assertThat(instances).hasSize(4);
    for (int i = 0; i < 3; i++) {
      assertChild(instances, i, "taskB", FlowNodeStateDto.COMPLETED, multiInstanceBodyTreePath,
          FlowNodeType.SERVICE_TASK);
    }
    assertThat(assertChild(instances, 3, "taskB", FlowNodeStateDto.COMPLETED,
        multiInstanceBodyTreePath, FlowNodeType.SERVICE_TASK).getId())
        .isEqualTo(taskBBeforeLast.getId());
  }

  private void assertPagesWithSearchAfterBeforeOrEqual(final String processInstanceId,
      final String multiInstanceBodyTreePath) throws Exception {

    //when - test level 3 - page 1
    FlowNodeInstanceQueryDto request = new FlowNodeInstanceQueryDto(
        processInstanceId, multiInstanceBodyTreePath);
    request.setPageSize(4);
    List<FlowNodeInstanceDto> instances = getFlowNodeInstanceOneListFromRest(request);

    //then
    assertThat(instances).hasSize(4);
    final FlowNodeInstanceDto lastTaskPage1 = assertChild(instances, 3, "taskB",
        FlowNodeStateDto.COMPLETED,
        multiInstanceBodyTreePath, FlowNodeType.SERVICE_TASK);

    //when - test level 3 - same page 1 with searchAfterOrEqual
    request.setSearchAfterOrEqual(instances.get(0).getSortValues());
    assertThat(getFlowNodeInstanceOneListFromRest(request))
        .containsExactly(instances.stream().toArray(FlowNodeInstanceDto[]::new));

    //when - test level 3 - page 2
    request = new FlowNodeInstanceQueryDto(
        processInstanceId, multiInstanceBodyTreePath)
        .setPageSize(5)
        .setSearchAfter(lastTaskPage1.getSortValues());
    instances = getFlowNodeInstanceOneListFromRest(request);

    //then - test level 3 - same page 2 with searchBeforeorEqual
    assertThat(instances).hasSize(2);
    request = new FlowNodeInstanceQueryDto(
        processInstanceId, multiInstanceBodyTreePath)
        .setPageSize(2)
        .setSearchBeforeOrEqual(instances.get(instances.size() - 1).getSortValues());
    assertThat(getFlowNodeInstanceOneListFromRest(request))
        .containsExactly(instances.stream().toArray(FlowNodeInstanceDto[]::new));

  }

  protected FlowNodeInstanceDto assertChild(List<FlowNodeInstanceDto> children, int childPosition, String flowNodeId, FlowNodeStateDto state, String parentTreePath, FlowNodeType type) {
    final FlowNodeInstanceDto flowNode = children.get(childPosition);
    assertThat(flowNode.getFlowNodeId()).isEqualTo(flowNodeId);
    assertThat(flowNode.getId()).isNotNull();
    assertThat(flowNode.getState()).isEqualTo(state);
    assertThat(flowNode.getTreePath()).isEqualTo(ConversionUtils.toStringOrNull(parentTreePath + "/" + flowNode.getId()));
    assertThat(flowNode.getStartDate()).isNotNull();
    if (state.equals(FlowNodeStateDto.COMPLETED) || state.equals(FlowNodeStateDto.TERMINATED)) {
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
    deployProcess("subProcess.bpmn");
    final long processInstanceKey = ZeebeTestUtil
        .startProcessInstance(zeebeClient, processId, "{\"items\": [0]}");
    ZeebeTestUtil.completeTask(zeebeClient, "taskA", getWorkerName(), null);
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "taskB");
    ZeebeTestUtil.failTask(zeebeClient, "taskB", getWorkerName(), 3, "some error");
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, processInstanceKey);

    //when
    final String processInstanceId = String.valueOf(processInstanceKey);
    FlowNodeInstanceQueryDto request = new FlowNodeInstanceQueryDto(
        processInstanceId, processInstanceId);
    List<FlowNodeInstanceDto> instances = getFlowNodeInstanceOneListFromRest(request);

    //then
    //level 1
    assertThat(instances).filteredOn("flowNodeId", "subprocess").hasSize(1);
    final FlowNodeInstanceDto subprocess = instances.stream().filter(ai -> ai.getFlowNodeId().equals("subprocess"))
      .findFirst().get();
    assertThat(subprocess.getState()).isEqualTo(FlowNodeStateDto.INCIDENT);
    assertThat(instances).filteredOn(ai -> !ai.getFlowNodeId().equals("subprocess")).allMatch(ai -> !ai.getState().equals(FlowNodeStateDto.INCIDENT));

    //level 2
    request = new FlowNodeInstanceQueryDto(processInstanceId, subprocess.getTreePath());
    instances = getFlowNodeInstanceOneListFromRest(request);
    assertThat(instances).filteredOn("flowNodeId", "innerSubprocess").hasSize(1);
    final FlowNodeInstanceDto innerSubprocess = instances.stream().filter(ai -> ai.getFlowNodeId().equals("innerSubprocess"))
      .findFirst().get();
    assertThat(innerSubprocess.getState()).isEqualTo(FlowNodeStateDto.INCIDENT);
    assertThat(instances).filteredOn(ai -> !ai.getFlowNodeId().equals("innerSubprocess")).allMatch(ai -> !ai.getState().equals(FlowNodeStateDto.INCIDENT));

    //level 3
    request = new FlowNodeInstanceQueryDto(processInstanceId, innerSubprocess.getTreePath());
    instances = getFlowNodeInstanceOneListFromRest(request);
    assertThat(instances).filteredOn("flowNodeId", "taskB").allMatch(ai -> ai.getState().equals(
        FlowNodeStateDto.INCIDENT));
    assertThat(instances).filteredOn(ai -> !ai.getFlowNodeId().equals("taskB")).allMatch(ai -> !ai.getState().equals(FlowNodeStateDto.INCIDENT));

  }

  @Test
  public void testFlowNodeInstanceTreeIncidentStatePropagatedWithPageSize() throws Exception {

    // having
    String processId = "prWithSubprocess";
    deployProcess("subProcess.bpmn");
    final long processInstanceKey = ZeebeTestUtil
        .startProcessInstance(zeebeClient, processId, "{\"items\": [0]}");
    ZeebeTestUtil.completeTask(zeebeClient, "taskA", getWorkerName(), null);
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "taskB");
    ZeebeTestUtil.failTask(zeebeClient, "taskB", getWorkerName(), 3, "some error");
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, processInstanceKey);

    //when
    final String processInstanceId = String.valueOf(processInstanceKey);
    FlowNodeInstanceQueryDto request = new FlowNodeInstanceQueryDto(
        processInstanceId, processInstanceId).setPageSize(3);
    List<FlowNodeInstanceDto> instances = getFlowNodeInstanceOneListFromRest(request);

    //then
    //level 1
    assertThat(instances).filteredOn("flowNodeId", "subprocess").hasSize(1);
    final FlowNodeInstanceDto subprocess = instances.stream().filter(ai -> ai.getFlowNodeId().equals("subprocess"))
      .findFirst().get();
    assertThat(subprocess.getState()).isEqualTo(FlowNodeStateDto.INCIDENT);
    assertThat(instances).filteredOn(ai -> !ai.getFlowNodeId().equals("subprocess")).allMatch(ai -> !ai.getState().equals(FlowNodeStateDto.INCIDENT));

    //level 2
    request = new FlowNodeInstanceQueryDto(processInstanceId, subprocess.getTreePath()).setPageSize(2);
    instances = getFlowNodeInstanceOneListFromRest(request);
    assertThat(instances).filteredOn("flowNodeId", "innerSubprocess").hasSize(1);
    final FlowNodeInstanceDto innerSubprocess = instances.stream().filter(ai -> ai.getFlowNodeId().equals("innerSubprocess"))
      .findFirst().get();
    assertThat(innerSubprocess.getState()).isEqualTo(FlowNodeStateDto.INCIDENT);
    assertThat(instances).filteredOn(ai -> !ai.getFlowNodeId().equals("innerSubprocess")).allMatch(ai -> !ai.getState().equals(FlowNodeStateDto.INCIDENT));

    //level 3
    request = new FlowNodeInstanceQueryDto(processInstanceId, innerSubprocess.getTreePath()).setPageSize(2);
    instances = getFlowNodeInstanceOneListFromRest(request);
    assertThat(instances).filteredOn("flowNodeId", "taskB").allMatch(ai -> ai.getState().equals(
        FlowNodeStateDto.INCIDENT));
    assertThat(instances).filteredOn(ai -> !ai.getFlowNodeId().equals("taskB")).allMatch(ai -> !ai.getState().equals(FlowNodeStateDto.INCIDENT));

  }

  @Test
  public void testFlowNodeInstanceTreeFailsWithAbsentQueries() throws Exception {
    FlowNodeInstanceRequestDto treeRequest = new FlowNodeInstanceRequestDto();

    MvcResult mvcResult = postRequestThatShouldFail(FLOW_NODE_INSTANCE_URL, treeRequest);

    assertErrorMessageIsEqualTo(mvcResult, "At least one query must be provided when requesting for flow node instance tree.");
  }

  @Test
  public void testFlowNodeInstanceTreeFailsWithEmptyProcessInstanceIdOrTreePath() throws Exception {
    FlowNodeInstanceRequestDto treeRequest = new FlowNodeInstanceRequestDto(new FlowNodeInstanceQueryDto());
    MvcResult mvcResult = postRequestThatShouldFail(FLOW_NODE_INSTANCE_URL, treeRequest);
    assertErrorMessageIsEqualTo(mvcResult, "Process instance id and tree path must be provided when requesting for flow node instance tree.");

    treeRequest = new FlowNodeInstanceRequestDto(new FlowNodeInstanceQueryDto().setProcessInstanceId("some"));
    mvcResult = postRequestThatShouldFail(FLOW_NODE_INSTANCE_URL, treeRequest);
    assertErrorMessageIsEqualTo(mvcResult, "Process instance id and tree path must be provided when requesting for flow node instance tree.");

    treeRequest = new FlowNodeInstanceRequestDto(new FlowNodeInstanceQueryDto().setTreePath("some"));
    mvcResult = postRequestThatShouldFail(FLOW_NODE_INSTANCE_URL, treeRequest);
    assertErrorMessageIsEqualTo(mvcResult, "Process instance id and tree path must be provided when requesting for flow node instance tree.");
  }

  @Test
  public void testFlowNodeInstanceTreeFailsWithWrongSearchAfter() throws Exception {
    FlowNodeInstanceQueryDto treeQuery = new FlowNodeInstanceQueryDto()
        .setProcessInstanceId("123")
        .setTreePath("123")
        .setSearchAfter(new SortValuesWrapper[]{})
        .setSearchBeforeOrEqual(new SortValuesWrapper[]{});
    FlowNodeInstanceRequestDto treeRequest = new FlowNodeInstanceRequestDto(treeQuery);

    MvcResult mvcResult = postRequestThatShouldFail(FLOW_NODE_INSTANCE_URL, treeRequest);
    assertErrorMessageIsEqualTo(mvcResult, "Only one of [searchAfter, searchAfterOrEqual, searchBefore, searchBeforeOrEqual] must be present in request.");

    treeQuery = new FlowNodeInstanceQueryDto()
        .setProcessInstanceId("123")
        .setTreePath("123")
        .setSearchBefore(new SortValuesWrapper[]{})
        .setSearchBeforeOrEqual(new SortValuesWrapper[]{});

    mvcResult = postRequestThatShouldFail(FLOW_NODE_INSTANCE_URL, new FlowNodeInstanceRequestDto(treeQuery));
    assertErrorMessageIsEqualTo(mvcResult, "Only one of [searchAfter, searchAfterOrEqual, searchBefore, searchBeforeOrEqual] must be present in request.");

    treeQuery = new FlowNodeInstanceQueryDto()
        .setProcessInstanceId("123")
        .setTreePath("123")
        .setSearchAfter(new SortValuesWrapper[]{})
        .setSearchAfterOrEqual(new SortValuesWrapper[]{});

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

  protected Map<String, FlowNodeStateDto> getFlowNodeStateDtosFromRest(String processInstanceId) throws Exception {
    MvcResult mvcResult = getRequest(String.format(ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/%s/flow-node-states", processInstanceId));
    return mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() { });
  }

  @Test
  public void testFlowNodeStateDtosIncidentIsPropagated() throws Exception {
    // having
    String processId = "prWithSubprocess";
    deployProcess("subProcess.bpmn");
    final long processInstanceKey = ZeebeTestUtil
        .startProcessInstance(zeebeClient, processId, "{\"items\": [0]}");
    ZeebeTestUtil.completeTask(zeebeClient, "taskA", getWorkerName(), null);
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "taskB");
    ZeebeTestUtil.failTask(zeebeClient, "taskB", getWorkerName(), 3, "some error");
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, processInstanceKey);

    //when
    final Map<String, FlowNodeStateDto> FlowNodeStateDtos = getFlowNodeStateDtosFromRest(
        String.valueOf(processInstanceKey));

    //then
    assertThat(FlowNodeStateDtos).hasSize(7);
    assertFlowNodeStateDto(FlowNodeStateDtos, "startEvent", FlowNodeStateDto.COMPLETED);
    assertFlowNodeStateDto(FlowNodeStateDtos, "taskA", FlowNodeStateDto.COMPLETED);
    assertFlowNodeStateDto(FlowNodeStateDtos, "subprocess", FlowNodeStateDto.INCIDENT);
    assertFlowNodeStateDto(FlowNodeStateDtos, "startEventSubprocess", FlowNodeStateDto.COMPLETED);
    assertFlowNodeStateDto(FlowNodeStateDtos, "innerSubprocess", FlowNodeStateDto.INCIDENT);
    assertFlowNodeStateDto(FlowNodeStateDtos, "startEventInnerSubprocess", FlowNodeStateDto.COMPLETED);
    assertFlowNodeStateDto(FlowNodeStateDtos, "taskB", FlowNodeStateDto.INCIDENT);
  }

  @Test
  public void testFlowNodeStateDtos() throws Exception {
    // having
    String processId = "prWithSubprocess";
    deployProcess("subProcess.bpmn");
    final long processInstanceKey = ZeebeTestUtil
        .startProcessInstance(zeebeClient, processId, "{\"items\": [0,1,2,3,4,5,6,7,8,9]}");
    ZeebeTestUtil.completeTask(zeebeClient, "taskA", getWorkerName(), null);
    elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, "taskB");
    ZeebeTestUtil.completeTask(zeebeClient, "taskB", getWorkerName(), null, 9);
    elasticsearchTestRule.processAllRecordsAndWait(flowNodesAreCompletedCheck, processInstanceKey, "taskB", 9);

    //when
    final Map<String, FlowNodeStateDto> FlowNodeStateDtos = getFlowNodeStateDtosFromRest(
        String.valueOf(processInstanceKey));

    //then
    assertThat(FlowNodeStateDtos).hasSize(7);
    assertFlowNodeStateDto(FlowNodeStateDtos, "startEvent", FlowNodeStateDto.COMPLETED);
    assertFlowNodeStateDto(FlowNodeStateDtos, "taskA", FlowNodeStateDto.COMPLETED);
    assertFlowNodeStateDto(FlowNodeStateDtos, "subprocess", FlowNodeStateDto.ACTIVE);
    assertFlowNodeStateDto(FlowNodeStateDtos, "startEventSubprocess", FlowNodeStateDto.COMPLETED);
    assertFlowNodeStateDto(FlowNodeStateDtos, "innerSubprocess", FlowNodeStateDto.ACTIVE);
    assertFlowNodeStateDto(FlowNodeStateDtos, "startEventInnerSubprocess", FlowNodeStateDto.COMPLETED);
    assertFlowNodeStateDto(FlowNodeStateDtos, "taskB", FlowNodeStateDto.ACTIVE);
  }

  @Test
  public void testFlowNodeStateDtosTerminated() throws Exception {

    final String bpmnProcessId = "process";
    final String flowNodeId = "taskA";
    final Long processInstanceKey = tester.createAndDeploySimpleProcess(bpmnProcessId, flowNodeId)
        .waitUntil()
        .processIsDeployed()
        .and()
        .startProcessInstance(bpmnProcessId)
        .waitUntil()
        .flowNodeIsActive(flowNodeId)
        .and()
        .cancelProcessInstanceOperation()
        .executeOperations()
        .waitUntil()
        .processInstanceIsFinished()
        .getProcessInstanceKey();

    //when
    final Map<String, FlowNodeStateDto> FlowNodeStateDtos = getFlowNodeStateDtosFromRest(
        String.valueOf(processInstanceKey));

    //then
    assertThat(FlowNodeStateDtos).hasSize(2);
    assertFlowNodeStateDto(FlowNodeStateDtos, "start", FlowNodeStateDto.COMPLETED);
    assertFlowNodeStateDto(FlowNodeStateDtos, "taskA", FlowNodeStateDto.TERMINATED);
  }

  private void assertFlowNodeStateDto(Map<String, FlowNodeStateDto> FlowNodeStateDtos, String flowNodeId, FlowNodeStateDto... states) {
    assertThat(FlowNodeStateDtos.get(flowNodeId)).isIn(states);
  }

}
