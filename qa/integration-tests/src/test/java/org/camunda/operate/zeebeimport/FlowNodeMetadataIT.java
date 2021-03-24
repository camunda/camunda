/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.entities.FlowNodeType.MULTI_INSTANCE_BODY;
import static org.camunda.operate.entities.FlowNodeType.SERVICE_TASK;
import static org.camunda.operate.entities.FlowNodeType.SUB_PROCESS;
import static org.camunda.operate.webapp.rest.WorkflowInstanceRestService.WORKFLOW_INSTANCE_URL;

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.camunda.operate.entities.EventSourceType;
import org.camunda.operate.entities.EventType;
import org.camunda.operate.entities.FlowNodeType;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceDto;
import org.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceQueryDto;
import org.camunda.operate.webapp.rest.dto.metadata.FlowNodeInstanceBreadcrumbEntryDto;
import org.camunda.operate.webapp.rest.dto.metadata.FlowNodeInstanceMetadataDto;
import org.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataDto;
import org.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataRequestDto;
import org.camunda.operate.webapp.zeebe.operation.CancelWorkflowInstanceHandler;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

public class FlowNodeMetadataIT extends OperateZeebeIntegrationTest {

  @Autowired
  private CancelWorkflowInstanceHandler cancelWorkflowInstanceHandler;

  @Before
  public void before() {
    super.before();
    cancelWorkflowInstanceHandler.setZeebeClient(zeebeClient);
  }

  /**
   * Use cases 1.1 and 2.1.
   */
  @Test
  public void shouldReturnOneInstanceMetadata() throws Exception {
    //having
    final String taskId = "taskA";
    final String workflowId = "testProcess";
    final BpmnModelInstance testProcess =
        Bpmn.createExecutableProcess(workflowId)
            .startEvent()
            .serviceTask(taskId).zeebeJobType(taskId)
            .endEvent()
            .done();
    final long workflowInstanceKey = tester
        .deployWorkflow(testProcess, "testProcess.bpmn")
        .startWorkflowInstance(workflowId)
        .and()
        .waitUntil()
        .flowNodeIsActive(taskId)
        .getWorkflowInstanceKey();

    //when 1.1
    FlowNodeMetadataDto flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(workflowInstanceKey), taskId, null, null);

    //then
    assertThat(flowNodeMetadata.getBreadcrumb()).isEmpty();
    assertThat(flowNodeMetadata.getInstanceCount()).isNull();
    final String flowNodeInstanceId1 = flowNodeMetadata.getFlowNodeInstanceId();
    assertThat(flowNodeInstanceId1).isNotNull();
    assertThat(flowNodeMetadata.getFlowNodeId()).isNull();
    assertThat(flowNodeMetadata.getFlowNodeType()).isNull();
    assertThat(flowNodeMetadata.getInstanceMetadata()).isNotNull();
    assertFlowNodeInstanceData(flowNodeMetadata.getInstanceMetadata(), taskId, SERVICE_TASK,
        flowNodeInstanceId1, false);

    //when 2.1
    flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(workflowInstanceKey), null, null, flowNodeInstanceId1);

    //then
    assertThat(flowNodeMetadata.getBreadcrumb()).isEmpty();
    assertThat(flowNodeMetadata.getInstanceCount()).isNull();
    final String flowNodeInstanceId2 = flowNodeMetadata.getFlowNodeInstanceId();
    assertThat(flowNodeInstanceId2).isEqualTo(flowNodeInstanceId1);
    assertThat(flowNodeMetadata.getFlowNodeId()).isNull();
    assertThat(flowNodeMetadata.getFlowNodeType()).isNull();
    assertThat(flowNodeMetadata.getInstanceMetadata()).isNotNull();
    assertFlowNodeInstanceData(flowNodeMetadata.getInstanceMetadata(), taskId, SERVICE_TASK,
        flowNodeInstanceId2, false);
  }

  /**
   * Use cases 1.2, 2.2, 3.1.
   */
  @Test
  public void shouldReturnOneInstanceMetadataWithBreadcrumb() throws Exception {
    //having process with sequential multi-instance subprocess
    final String taskId = "taskA";
    final String workflowId = "testProcess";
    final String subprocessId = "subprocess";
    final BpmnModelInstance testProcess =
        Bpmn.createExecutableProcess(workflowId)
            .startEvent()
            .subProcess(
                subprocessId,
                s -> s.multiInstance(m -> m.zeebeInputCollectionExpression("items").sequential())
                    .embeddedSubProcess()
                    .startEvent()
                    .serviceTask(taskId).zeebeJobType(taskId)
                    .serviceTask("someTask").zeebeJobType(taskId)
                    .endEvent())
            .endEvent()
            .done();
    final long workflowInstanceKey = tester
        .deployWorkflow(testProcess, "testProcess.bpmn")
        .startWorkflowInstance(workflowId, "{\"items\": [0, 1]}")
        .and().waitUntil()
        .flowNodeIsActive(taskId)
        .getWorkflowInstanceKey();

    //when 1.2
    FlowNodeMetadataDto flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(workflowInstanceKey), subprocessId, null, null);

    //then
    assertBreadcrumb(flowNodeMetadata.getBreadcrumb(),
        new FlowNodeInstanceBreadcrumbEntryDto(subprocessId, MULTI_INSTANCE_BODY));
    assertThat(flowNodeMetadata.getInstanceCount()).isNull();
    final String flowNodeInstanceId1 = flowNodeMetadata.getFlowNodeInstanceId();
    assertThat(flowNodeInstanceId1).isNotNull();
    assertThat(flowNodeMetadata.getFlowNodeId()).isNull();
    assertThat(flowNodeMetadata.getFlowNodeType()).isNull();
    assertThat(flowNodeMetadata.getInstanceMetadata()).isNotNull();
    assertFlowNodeInstanceData(flowNodeMetadata.getInstanceMetadata(), subprocessId,
        MULTI_INSTANCE_BODY, flowNodeInstanceId1, false);

    //when 2.2 (is multi-instance body itseld)
    flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(workflowInstanceKey), null, null, flowNodeInstanceId1);

    //then
    assertBreadcrumb(flowNodeMetadata.getBreadcrumb(),
        new FlowNodeInstanceBreadcrumbEntryDto(subprocessId, MULTI_INSTANCE_BODY));
    assertThat(flowNodeMetadata.getInstanceCount()).isNull();
    final String flowNodeInstanceId2 = flowNodeMetadata.getFlowNodeInstanceId();
    assertThat(flowNodeInstanceId2).isEqualTo(flowNodeInstanceId1);
    assertThat(flowNodeMetadata.getFlowNodeId()).isNull();
    assertThat(flowNodeMetadata.getFlowNodeType()).isNull();
    assertThat(flowNodeMetadata.getInstanceMetadata()).isNotNull();
    assertFlowNodeInstanceData(flowNodeMetadata.getInstanceMetadata(), subprocessId,
        MULTI_INSTANCE_BODY, flowNodeInstanceId2, false);

    //when 3.1 (breadcrumb for multi-instance body)
    final FlowNodeInstanceBreadcrumbEntryDto breadcrumb = flowNodeMetadata
        .getBreadcrumb().get(0);
    flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(workflowInstanceKey), breadcrumb.getFlowNodeId(),
        breadcrumb.getFlowNodeType(), null);

    //then
    assertBreadcrumb(flowNodeMetadata.getBreadcrumb(),
        new FlowNodeInstanceBreadcrumbEntryDto(subprocessId, MULTI_INSTANCE_BODY));
    assertThat(flowNodeMetadata.getInstanceCount()).isNull();
    final String flowNodeInstanceId3 = flowNodeMetadata.getFlowNodeInstanceId();
    assertThat(flowNodeInstanceId3).isEqualTo(flowNodeInstanceId1);
    assertThat(flowNodeMetadata.getFlowNodeId()).isNull();
    assertThat(flowNodeMetadata.getFlowNodeType()).isNull();
    assertThat(flowNodeMetadata.getInstanceMetadata()).isNotNull();
    assertFlowNodeInstanceData(flowNodeMetadata.getInstanceMetadata(), subprocessId,
        MULTI_INSTANCE_BODY, flowNodeInstanceId3, false);

    //when 3.1 (breadcrumb for subprocess)
    flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(workflowInstanceKey), subprocessId, SUB_PROCESS, null);

    //then
    assertBreadcrumb(flowNodeMetadata.getBreadcrumb(),
        new FlowNodeInstanceBreadcrumbEntryDto(subprocessId, MULTI_INSTANCE_BODY),
        new FlowNodeInstanceBreadcrumbEntryDto(subprocessId, SUB_PROCESS));
    assertThat(flowNodeMetadata.getInstanceCount()).isNull();
    final String subprocessInstanceId1 = flowNodeMetadata.getFlowNodeInstanceId();
    assertThat(subprocessInstanceId1).isNotNull();
    assertThat(flowNodeMetadata.getFlowNodeId()).isNull();
    assertThat(flowNodeMetadata.getFlowNodeType()).isNull();
    assertThat(flowNodeMetadata.getInstanceMetadata()).isNotNull();
    assertFlowNodeInstanceData(flowNodeMetadata.getInstanceMetadata(), subprocessId,
        SUB_PROCESS, subprocessInstanceId1, false);

    //when 2.2 (is included in multi-instance)
    flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(workflowInstanceKey), null, null, subprocessInstanceId1);

    //then
    assertBreadcrumb(flowNodeMetadata.getBreadcrumb(),
        new FlowNodeInstanceBreadcrumbEntryDto(subprocessId, MULTI_INSTANCE_BODY),
        new FlowNodeInstanceBreadcrumbEntryDto(subprocessId, SUB_PROCESS));
    assertThat(flowNodeMetadata.getInstanceCount()).isNull();
    final String subprocessInstanceId2 = flowNodeMetadata.getFlowNodeInstanceId();
    assertThat(subprocessInstanceId2).isEqualTo(subprocessInstanceId1);
    assertThat(flowNodeMetadata.getFlowNodeId()).isNull();
    assertThat(flowNodeMetadata.getFlowNodeType()).isNull();
    assertThat(flowNodeMetadata.getInstanceMetadata()).isNotNull();
    assertFlowNodeInstanceData(flowNodeMetadata.getInstanceMetadata(), subprocessId,
        SUB_PROCESS, subprocessInstanceId2, false);
  }

  /**
   * Use case 1.3 and 3.2.
   */
  @Test
  public void shouldReturnInstanceCountPeterCase() throws Exception {
    //having process with Peter case, two instances of task are active
    final String taskId = "taskA";
    final String workflowId = "testProcess";
    final BpmnModelInstance testProcess =
        Bpmn.createExecutableProcess(workflowId)
            .startEvent()
            .parallelGateway("parallel")
            .exclusiveGateway("exclusive")
            .moveToNode("parallel")
            .connectTo("exclusive")
            .serviceTask(taskId).zeebeJobType(taskId)
            .endEvent()
            .done();
    final long workflowInstanceKey = tester
        .deployWorkflow(testProcess, "testProcess.bpmn")
        .startWorkflowInstance(workflowId)
        .and().waitUntil()
        .flowNodeIsActive(taskId)
        .getWorkflowInstanceKey();

    //when 1.3 - instance count by flowNodeId
    FlowNodeMetadataDto flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(workflowInstanceKey), taskId, null, null);

    //then
    assertThat(flowNodeMetadata.getInstanceCount()).isEqualTo(2);
    assertThat(flowNodeMetadata.getBreadcrumb()).isEmpty();
    assertThat(flowNodeMetadata.getFlowNodeType()).isEqualTo(SERVICE_TASK);
    assertThat(flowNodeMetadata.getFlowNodeId()).isEqualTo(taskId);

    //when 3.2 - instance count by breadcrump
    flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(workflowInstanceKey), taskId, SERVICE_TASK, null);

    //then
    assertThat(flowNodeMetadata.getInstanceCount()).isEqualTo(2);
    assertThat(flowNodeMetadata.getBreadcrumb()).isEmpty();
    assertThat(flowNodeMetadata.getFlowNodeType()).isEqualTo(SERVICE_TASK);
    assertThat(flowNodeMetadata.getFlowNodeId()).isEqualTo(taskId);
  }

  /**
   * Use case 2.3.
   */
  @Test
  public void shouldReturnBreadcrumbForPeterCase() throws Exception {
    //having process with Peter case, two instances of task are active
    final String taskId = "taskA";
    final String workflowId = "testProcess";
    final BpmnModelInstance testProcess =
        Bpmn.createExecutableProcess(workflowId)
            .startEvent()
            .parallelGateway("parallel")
            .exclusiveGateway("exclusive")
            .moveToNode("parallel")
            .connectTo("exclusive")
            .serviceTask(taskId).zeebeJobType(taskId)
            .endEvent()
            .done();
    final long workflowInstanceKey = tester
        .deployWorkflow(testProcess, "testProcess.bpmn")
        .startWorkflowInstance(workflowId)
        .and().waitUntil()
        .flowNodeIsActive(taskId)
        .getWorkflowInstanceKey();
    final List<FlowNodeInstanceDto> flowNodeInstances = tester.getFlowNodeInstanceOneListFromRest(
        String.valueOf(workflowInstanceKey));
    final List<FlowNodeInstanceDto> tasks = flowNodeInstances.stream()
        .filter(fni -> fni.getType().equals(SERVICE_TASK)).collect(Collectors.toList());
    final String flowNodeInstanceId = tasks.get(0).getId();

    //when 2.3 - one instance out of several (Peter case)
    FlowNodeMetadataDto flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(workflowInstanceKey), null, null, flowNodeInstanceId);

    //then
    assertBreadcrumb(flowNodeMetadata.getBreadcrumb(),
        new FlowNodeInstanceBreadcrumbEntryDto(taskId, SERVICE_TASK));
    assertThat(flowNodeMetadata.getInstanceCount()).isNull();
    final String id = flowNodeMetadata.getFlowNodeInstanceId();
    assertThat(id).isEqualTo(flowNodeInstanceId);
    assertThat(flowNodeMetadata.getFlowNodeId()).isNull();
    assertThat(flowNodeMetadata.getFlowNodeType()).isNull();
    assertThat(flowNodeMetadata.getInstanceMetadata()).isNotNull();
    assertFlowNodeInstanceData(flowNodeMetadata.getInstanceMetadata(), taskId,
        SERVICE_TASK, flowNodeInstanceId, false);
  }

  /**
   * Use case 3.3.
   */
  @Test
  public void shouldReturnInstanceCountInsideMultiInstance() throws Exception {
    //having process with parallel multi-instance subprocess
    final String taskId = "taskA";
    final String workflowId = "testProcess";
    final String subprocessId = "subprocess";
    final BpmnModelInstance testProcess =
        Bpmn.createExecutableProcess(workflowId)
            .startEvent()
            .subProcess(
                subprocessId,
                s -> s.multiInstance(m -> m.zeebeInputCollectionExpression("items").parallel())
                    .embeddedSubProcess()
                    .startEvent()
                    .serviceTask(taskId).zeebeJobType(taskId)
                    .serviceTask("someTask").zeebeJobType(taskId)
                    .endEvent())
            .endEvent()
            .done();
    final long workflowInstanceKey = tester
        .deployWorkflow(testProcess, "testProcess.bpmn")
        .startWorkflowInstance(workflowId, "{\"items\": [0, 1, 2]}")
        .and().waitUntil()
        .flowNodeIsActive(taskId)
        .getWorkflowInstanceKey();

    //when 3.3 - instance count by breadcrump
    FlowNodeMetadataDto flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(workflowInstanceKey), subprocessId, SUB_PROCESS, null);

    //then
    assertThat(flowNodeMetadata.getInstanceCount()).isEqualTo(3);
    assertThat(flowNodeMetadata.getBreadcrumb()).isEmpty();
    assertThat(flowNodeMetadata.getFlowNodeType()).isEqualTo(SUB_PROCESS);
    assertThat(flowNodeMetadata.getFlowNodeId()).isEqualTo(subprocessId);

  }

  private void assertBreadcrumb(final List<FlowNodeInstanceBreadcrumbEntryDto> breadcrumb,
      final FlowNodeInstanceBreadcrumbEntryDto... expectedBreadcrumb) {
    assertThat(breadcrumb).isNotNull();
    assertThat(breadcrumb).isNotEmpty();
    for (int i = 0; i < expectedBreadcrumb.length; i++) {
      assertThat(breadcrumb.get(i)).isEqualTo(expectedBreadcrumb[i]);
    }
  }

  private void assertFlowNodeInstanceData(final FlowNodeInstanceMetadataDto metadata,
      final String flowNodeId, final FlowNodeType flowNodeType, final String flowNodeInstanceId,
      boolean endDateExists) {
    assertThat(metadata.getFlowNodeId()).isEqualTo(flowNodeId);
    assertThat(metadata.getFlowNodeType()).isEqualTo(flowNodeType);
    assertThat(metadata.getFlowNodeInstanceId())
        .isEqualTo(flowNodeInstanceId);
    assertThat(metadata.getStartDate()).isNotNull();
    if (endDateExists) {
      assertThat(metadata.getEndDate()).isNotNull();
      assertThat(metadata.getEndDate()).isAfter(metadata.getStartDate());
    }
  }

  @Test
  public void testMetadataForFinishedWorkflow() throws Exception {
    // having
    final String processId = "processWithGateway";
    final String taskA = "taskA";
    final String taskC = "taskC";
    final String errorMessage = "Some error";
    final Long workflowInstanceKey = tester.deployWorkflow("processWithGateway.bpmn")
        .startWorkflowInstance(processId, "{\"a\": \"b\"}")
        .and()
        .failTask(taskA, errorMessage)
        .waitUntil()
        .incidentIsActive()
        .and()
        .resolveIncident()
        .waitUntil()
        .flowNodeIsActive(taskA)
        .and()
        .completeTask(taskA, "{\"goToTaskC\":true}")
        .waitUntil()
        .flowNodeIsActive(taskC)
        .and()
        .completeTask(taskC, "{\"b\": \"d\"}")
        .waitUntil()
        .workflowInstanceIsCompleted()
        .getWorkflowInstanceKey();

    //get flow node instance tree
    final String workflowInstanceId = String.valueOf(workflowInstanceKey);
    FlowNodeInstanceQueryDto request = new FlowNodeInstanceQueryDto(
        workflowInstanceId, workflowInstanceId);
    List<FlowNodeInstanceDto> instances = tester.getFlowNodeInstanceOneListFromRest(request);

    assertMetadata(instances, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETED,
        workflowInstanceKey, "start");
    try {
      assertMetadata(instances, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETED,
          workflowInstanceKey, "taskA");
    } catch (AssertionError ae) {
      assertMetadata(instances, EventSourceType.JOB, EventType.COMPLETED, workflowInstanceKey,
          "taskA");
    }
    assertMetadata(instances, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETED,
        workflowInstanceKey, "gateway");
    try {
      assertMetadata(instances, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETED,
          workflowInstanceKey, "taskC");
    } catch (AssertionError ae) {
      assertMetadata(instances, EventSourceType.JOB, EventType.COMPLETED, workflowInstanceKey,
          "taskC");
    }
    assertMetadata(instances, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETED,
        workflowInstanceKey, "end1");

  }

  @Test
  public void testMetadataForCanceledOnIncident() throws Exception {
    // having
    String flowNodeId = "taskA";

    String processId = "demoProcess";
    final Long workflowInstanceKey = tester.deployWorkflow("demoProcess_v_1.bpmn")
        .startWorkflowInstance(processId, null)
        .waitUntil()
        .incidentIsActive()
        .and()
        .cancelWorkflowInstanceOperation()
        .waitUntil()
        .operationIsCompleted()
        .and()
        .workflowInstanceIsFinished()
        .getWorkflowInstanceKey();

    //when
    //get flow node instance tree
    final String workflowInstanceId = String.valueOf(workflowInstanceKey);
    FlowNodeInstanceQueryDto request = new FlowNodeInstanceQueryDto(
        workflowInstanceId, workflowInstanceId);
    List<FlowNodeInstanceDto> instances = tester.getFlowNodeInstanceOneListFromRest(request);

    //then
    try {
      assertMetadata(instances, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_TERMINATED,
          workflowInstanceKey, flowNodeId);
    } catch (AssertionError ae) {
      assertMetadata(instances, EventSourceType.INCIDENT, EventType.RESOLVED, workflowInstanceKey,
          flowNodeId);
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

    final Long workflowInstanceKey = tester.deployWorkflow(workflow, processId + ".bpmn")
        .startWorkflowInstance(processId, "{\"a\": \"b\"}")      //wrong payload provokes incident
        .waitUntil()
        .incidentIsActive()
        .getWorkflowInstanceKey();

    //when
    //get flow node instance tree
    final String workflowInstanceId = String.valueOf(workflowInstanceKey);
    FlowNodeInstanceQueryDto request = new FlowNodeInstanceQueryDto(
        workflowInstanceId, workflowInstanceId);
    List<FlowNodeInstanceDto> instances = tester.getFlowNodeInstanceOneListFromRest(request);

    //then last event does not have a jobId
    final FlowNodeMetadataDto flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(workflowInstanceKey),
        null, null, instances.get(instances.size() - 1).getId());
    FlowNodeInstanceMetadataDto flowNodeInstanceMetadata = flowNodeMetadata.getInstanceMetadata();
    assertThat(flowNodeInstanceMetadata.getJobId()).isNull();
    //assert incident fields
    assertThat(flowNodeInstanceMetadata.getIncidentErrorMessage()).isNotNull();
    assertThat(flowNodeInstanceMetadata.getIncidentErrorType()).isNotNull();

  }

  @Test
  public void testFlowNodeMetadataFailsWithWrongRequst() throws Exception {
    FlowNodeMetadataRequestDto request = new FlowNodeMetadataRequestDto();

    MvcResult mvcResult = postRequestThatShouldFail(
        WORKFLOW_INSTANCE_URL + "/111/flow-node-metadata", request);
    assertErrorMessageIsEqualTo(mvcResult,
        "At least flowNodeId or flowNodeInstanceId must be specifies in the request.");

    request = new FlowNodeMetadataRequestDto("flowNodeId", "flowNodeInstanceId", null);
    mvcResult = postRequestThatShouldFail(
        WORKFLOW_INSTANCE_URL + "/111/flow-node-metadata", request);
    assertErrorMessageIsEqualTo(mvcResult,
        "Only one of flowNodeId or flowNodeInstanceId must be specifies in the request.");
  }

  private void assertMetadata(List<FlowNodeInstanceDto> flowNodes, EventSourceType eventSourceType,
      EventType eventType,
      Long workflowInstanceKey, String activityId)
      throws Exception {
    assertMetadata(flowNodes, eventSourceType, eventType, workflowInstanceKey, activityId, null);
  }

  private void assertMetadata(List<FlowNodeInstanceDto> flowNodes, EventSourceType eventSourceType,
      EventType eventType, Long workflowInstanceKey, String flowNodeId, String errorMessage)
      throws Exception {

    final Optional<FlowNodeInstanceDto> flowNodeInstance = flowNodes.stream()
        .filter(fni -> fni.getFlowNodeId().equals(flowNodeId)).findFirst();
    assertThat(flowNodeInstance).isNotEmpty();

    //call REST API to get metadata
    final FlowNodeMetadataDto flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(workflowInstanceKey),
        null, null, flowNodeInstance.get().getId());

    String assertionName = String.format("%s.%s", eventSourceType, eventType);

    final FlowNodeInstanceMetadataDto flowNodeInstanceMetadata = flowNodeMetadata
        .getInstanceMetadata();
    assertThat(flowNodeInstanceMetadata).isNotNull();
    assertThat(flowNodeInstanceMetadata.getFlowNodeId()).as(assertionName + ".flowNodeId")
        .isEqualTo(flowNodeId);
    assertThat(flowNodeInstanceMetadata.getFlowNodeInstanceId())
        .as(assertionName + ".flowNodeInstanceKey")
        .isNotNull();
    if (eventSourceType.equals(EventSourceType.INCIDENT)) {
      if (errorMessage != null) {
        assertThat(flowNodeInstanceMetadata.getIncidentErrorMessage())
            .as(assertionName + ".incidentErrorMessage").isEqualTo(errorMessage);
      } else {
        assertThat(flowNodeInstanceMetadata.getIncidentErrorMessage())
            .as(assertionName + ".incidentErrorMessage").isNotEmpty();
      }
      assertThat(flowNodeInstanceMetadata.getIncidentErrorType())
          .as(assertionName + ".incidentErrorType").isNotNull();
    }

  }

}