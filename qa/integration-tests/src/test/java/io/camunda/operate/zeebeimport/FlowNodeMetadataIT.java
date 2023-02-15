/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport;

import static io.camunda.operate.entities.FlowNodeType.CALL_ACTIVITY;
import static io.camunda.operate.entities.FlowNodeType.MULTI_INSTANCE_BODY;
import static io.camunda.operate.entities.FlowNodeType.SERVICE_TASK;
import static io.camunda.operate.entities.FlowNodeType.SUB_PROCESS;
import static io.camunda.operate.webapp.rest.ProcessInstanceRestService.PROCESS_INSTANCE_URL;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.archiver.ProcessInstancesArchiverJob;
import io.camunda.operate.entities.ErrorType;
import io.camunda.operate.entities.EventSourceType;
import io.camunda.operate.entities.EventType;
import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.util.OperateZeebeIntegrationTest;
import io.camunda.operate.webapp.es.reader.ListViewReader;
import io.camunda.operate.webapp.es.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.rest.dto.ProcessInstanceReferenceDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceQueryDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeInstanceBreadcrumbEntryDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeInstanceMetadataDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataRequestDto;
import io.camunda.operate.webapp.zeebe.operation.CancelProcessInstanceHandler;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

public class FlowNodeMetadataIT extends OperateZeebeIntegrationTest {

  @Autowired
  private CancelProcessInstanceHandler cancelProcessInstanceHandler;

  @Autowired
  private ProcessInstanceReader processInstanceReader;

  @Autowired
  private ListViewReader listViewReader;

  private ProcessInstancesArchiverJob archiverJob;

  @Before
  public void before() {
    super.before();
    cancelProcessInstanceHandler.setZeebeClient(zeebeClient);
    archiverJob = beanFactory.getBean(ProcessInstancesArchiverJob.class, partitionHolder.getPartitionIds());
  }

  /**
   * Use cases 1.1 and 2.1.
   */
  @Test
  public void shouldReturnOneInstanceMetadata() throws Exception {
    //having
    final String taskId = "taskA";
    final String jobType = "taskAJob";
    final String processId = "testProcess";
    final BpmnModelInstance testProcess =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask(taskId).zeebeJobType(jobType)
            .endEvent()
            .done();
    final long processInstanceKey = tester
        .deployProcess(testProcess, "testProcess.bpmn").waitUntil().processIsDeployed()
        .startProcessInstance(processId).waitUntil().processInstanceIsStarted()
        .and()
        .waitUntil()
        .flowNodeIsActive(taskId)
        .eventIsImported(jobType)
        .getProcessInstanceKey();

    //when 1.1
    FlowNodeMetadataDto flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(processInstanceKey), taskId, null, null);

    //then
    assertThat(flowNodeMetadata.getBreadcrumb()).isEmpty();
    assertThat(flowNodeMetadata.getInstanceCount()).isNull();
    final String flowNodeInstanceId1 = flowNodeMetadata.getFlowNodeInstanceId();
    assertThat(flowNodeInstanceId1).isNotNull();
    assertThat(flowNodeMetadata.getFlowNodeId()).isNull();
    assertThat(flowNodeMetadata.getFlowNodeType()).isNull();
    assertThat(flowNodeMetadata.getInstanceMetadata()).isNotNull();
    assertFlowNodeInstanceData(flowNodeMetadata.getInstanceMetadata(), taskId, SERVICE_TASK,
        flowNodeInstanceId1, false, jobType);

    //when 2.1
    flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(processInstanceKey), null, null, flowNodeInstanceId1);

    //then
    assertThat(flowNodeMetadata.getBreadcrumb()).isEmpty();
    assertThat(flowNodeMetadata.getInstanceCount()).isNull();
    final String flowNodeInstanceId2 = flowNodeMetadata.getFlowNodeInstanceId();
    assertThat(flowNodeInstanceId2).isEqualTo(flowNodeInstanceId1);
    assertThat(flowNodeMetadata.getFlowNodeId()).isNull();
    assertThat(flowNodeMetadata.getFlowNodeType()).isNull();
    assertThat(flowNodeMetadata.getInstanceMetadata()).isNotNull();
    assertFlowNodeInstanceData(flowNodeMetadata.getInstanceMetadata(), taskId, SERVICE_TASK,
        flowNodeInstanceId2, false, jobType);
  }

  /**
   * Use cases 1.1 and 2.1.
   */
  @Test
  public void shouldReturnOneInstanceMetadataForArchivedFinishedProcessInstance() throws Exception {
    //having
    final Instant currentTime = pinZeebeTime();
    pinZeebeTime(currentTime.minus(3, ChronoUnit.DAYS));

    final String taskId = "taskA";
    final String jobType = "taskAJob";
    final String processId = "testProcess";
    final BpmnModelInstance testProcess =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask(taskId).zeebeJobType(jobType)
            .endEvent()
            .done();
    final long processInstanceKey = tester
        .deployProcess(testProcess, "testProcess.bpmn")
        .startProcessInstance(processId)
        .and()
        .waitUntil()
        .flowNodeIsActive(taskId)
        .completeTask(taskId, jobType)
        .waitUntil()
        .processInstanceIsFinished()
        .eventIsImported(jobType)
        .getProcessInstanceKey();

    resetZeebeTime();
    assertThat(archiverJob.archiveNextBatch().join()).isEqualTo(1);
    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when 1.1
    FlowNodeMetadataDto flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(processInstanceKey), taskId, null, null);

    //then
    assertThat(flowNodeMetadata.getBreadcrumb()).isEmpty();
    assertThat(flowNodeMetadata.getInstanceCount()).isNull();
    final String flowNodeInstanceId1 = flowNodeMetadata.getFlowNodeInstanceId();
    assertThat(flowNodeInstanceId1).isNotNull();
    assertThat(flowNodeMetadata.getFlowNodeId()).isNull();
    assertThat(flowNodeMetadata.getFlowNodeType()).isNull();
    assertThat(flowNodeMetadata.getInstanceMetadata()).isNotNull();
    assertFlowNodeInstanceData(flowNodeMetadata.getInstanceMetadata(), taskId, SERVICE_TASK,
        flowNodeInstanceId1, false, jobType);

    //when 2.1
    flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(processInstanceKey), null, null, flowNodeInstanceId1);

    //then
    assertThat(flowNodeMetadata.getBreadcrumb()).isEmpty();
    assertThat(flowNodeMetadata.getInstanceCount()).isNull();
    final String flowNodeInstanceId2 = flowNodeMetadata.getFlowNodeInstanceId();
    assertThat(flowNodeInstanceId2).isEqualTo(flowNodeInstanceId1);
    assertThat(flowNodeMetadata.getFlowNodeId()).isNull();
    assertThat(flowNodeMetadata.getFlowNodeType()).isNull();
    assertThat(flowNodeMetadata.getInstanceMetadata()).isNotNull();
    assertFlowNodeInstanceData(flowNodeMetadata.getInstanceMetadata(), taskId, SERVICE_TASK,
        flowNodeInstanceId2, false, jobType);
  }

  /**
   * Use cases 1.2, 2.2, 3.1.
   */
  @Test
  public void shouldReturnOneInstanceMetadataWithBreadcrumb() throws Exception {
    //having process with sequential multi-instance subprocess
    final String taskId = "taskA";
    final String processId = "testProcess";
    final String subprocessId = "subprocess";
    final BpmnModelInstance testProcess =
        Bpmn.createExecutableProcess(processId)
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
    final long processInstanceKey = tester
        .deployProcess(testProcess, "testProcess.bpmn")
        .startProcessInstance(processId, "{\"items\": [0, 1]}")
        .and().waitUntil()
        .flowNodeIsActive(taskId)
        .getProcessInstanceKey();

    //when 1.2
    FlowNodeMetadataDto flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(processInstanceKey), subprocessId, null, null);

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
        MULTI_INSTANCE_BODY, flowNodeInstanceId1, false, null);

    //when 2.2 (is multi-instance body itself)
    flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(processInstanceKey), null, null, flowNodeInstanceId1);

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
        MULTI_INSTANCE_BODY, flowNodeInstanceId2, false, null);

    //when 3.1 (breadcrumb for multi-instance body)
    final FlowNodeInstanceBreadcrumbEntryDto breadcrumb = flowNodeMetadata
        .getBreadcrumb().get(0);
    flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(processInstanceKey), breadcrumb.getFlowNodeId(),
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
        MULTI_INSTANCE_BODY, flowNodeInstanceId3, false, null);

    //when 3.1 (breadcrumb for subprocess)
    flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(processInstanceKey), subprocessId, SUB_PROCESS, null);

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
        SUB_PROCESS, subprocessInstanceId1, false, null);

    //when 2.2 (is included in multi-instance)
    flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(processInstanceKey), null, null, subprocessInstanceId1);

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
        SUB_PROCESS, subprocessInstanceId2, false, null);
  }

  /**
   * Multi-instance call activity.
   */
  @Test
  public void shouldReturnCalledProcessInstanceIdForMultiInstanceCallActivity() throws Exception {
    //having process with sequential multi-instance call activity
    final String parentProcessId = "parentProcess";
    final String calledProcessId = "process";
    final String callActivityId = "callActivity";
    final BpmnModelInstance testProcess =
        Bpmn.createExecutableProcess(parentProcessId)
            .startEvent()
            .callActivity(callActivityId,
                ca -> ca.multiInstance(m -> m.zeebeInputCollectionExpression("items").sequential()))
            .zeebeProcessId(calledProcessId)
        .done();
    final long calledProcessDefinitionKey = tester
        .deployProcess("single-task.bpmn")
        .getProcessDefinitionKey();

    final long processInstanceKey = tester.deployProcess(testProcess, "testProcess.bpmn")
        .startProcessInstance(parentProcessId, "{\"items\": [0, 1]}")
        .and().waitUntil()
        .conditionIsMet(processInstancesAreStartedByProcessId, calledProcessDefinitionKey, 1)
        .getProcessInstanceKey();

    //when 3.1
    FlowNodeMetadataDto flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(processInstanceKey), callActivityId, CALL_ACTIVITY, null);

    //then
    assertThat(flowNodeMetadata.getInstanceCount()).isNull();
    final String flowNodeInstanceId1 = flowNodeMetadata.getFlowNodeInstanceId();
    assertThat(flowNodeMetadata.getInstanceMetadata()).isNotNull();
    assertThat(flowNodeMetadata.getInstanceMetadata().getFlowNodeType()).isEqualTo(CALL_ACTIVITY);
    final String calledProcessInstanceId1 = flowNodeMetadata.getInstanceMetadata()
        .getCalledProcessInstanceId();
    assertThat(calledProcessInstanceId1).isNotNull();
    //check that calledProcessInstanceId is the right one
    final ProcessInstanceForListViewEntity calledProcessInstance = processInstanceReader
        .getProcessInstanceByKey(Long.valueOf(calledProcessInstanceId1));
    assertThat(calledProcessInstance.getProcessDefinitionKey()).isEqualTo(calledProcessDefinitionKey);

    //when 2.2 (is included in multi-instance)
    flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(processInstanceKey), null, null, flowNodeInstanceId1);

    //then
    assertThat(flowNodeMetadata.getInstanceCount()).isNull();
    final String flowNodeInstanceId2 = flowNodeMetadata.getFlowNodeInstanceId();
    final String calledProcessInstanceId2 = flowNodeMetadata.getInstanceMetadata()
        .getCalledProcessInstanceId();
    assertThat(calledProcessInstanceId2).isNotNull();
    assertThat(calledProcessInstanceId2).isEqualTo(calledProcessInstanceId2);
    assertThat(flowNodeInstanceId2).isNotNull();
    assertThat(flowNodeInstanceId2).isEqualTo(flowNodeInstanceId1);
  }

  /**
   * Call activity.
   */
  @Test
  public void shouldReturnCalledProcessInstanceIdForCallActivity() throws Exception {
    //having process with call activity
    final String parentProcessId = "parentProcess";
    final String calledProcessId = "process";
    final String callActivityId = "callActivity";
    final BpmnModelInstance testProcess =
        Bpmn.createExecutableProcess(parentProcessId)
            .startEvent()
            .callActivity(callActivityId)
            .zeebeProcessId(calledProcessId)
        .done();
    final long calledProcessDefinitionKey = tester
        .deployProcess("single-task.bpmn")
        .getProcessDefinitionKey();

    final long processInstanceKey = tester.deployProcess(testProcess, "testProcess.bpmn")
        .startProcessInstance(parentProcessId, null)
        .and().waitUntil()
        .conditionIsMet(processInstancesAreStartedByProcessId, calledProcessDefinitionKey, 1)
        .getProcessInstanceKey();

    //when 1.1
    FlowNodeMetadataDto flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(processInstanceKey), callActivityId, null, null);

    //then
    assertThat(flowNodeMetadata.getInstanceCount()).isNull();
    assertThat(flowNodeMetadata.getInstanceMetadata()).isNotNull();
    assertThat(flowNodeMetadata.getInstanceMetadata().getFlowNodeType()).isEqualTo(CALL_ACTIVITY);
    assertThat(flowNodeMetadata.getInstanceMetadata().getCalledProcessDefinitionName())
        .isEqualTo(calledProcessId);
    final String calledProcessInstanceId1 = flowNodeMetadata.getInstanceMetadata()
        .getCalledProcessInstanceId();
    assertThat(calledProcessInstanceId1).isNotNull();
    //check that calledProcessInstanceId is the right one
    final ProcessInstanceForListViewEntity calledProcessInstance = processInstanceReader
        .getProcessInstanceByKey(Long.valueOf(calledProcessInstanceId1));
    assertThat(calledProcessInstance.getProcessDefinitionKey()).isEqualTo(calledProcessDefinitionKey);
  }

  /**
   * Use case 1.3 and 3.2.
   */
  @Test
  public void shouldReturnInstanceCountPeterCase() throws Exception {
    //having process with Peter case, two instances of task are active
    final String taskId = "taskA";
    final String processId = "testProcess";
    final BpmnModelInstance testProcess =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .parallelGateway("parallel")
            .exclusiveGateway("exclusive")
            .moveToNode("parallel")
            .connectTo("exclusive")
            .serviceTask(taskId).zeebeJobType(taskId)
            .endEvent()
            .done();
    final long processInstanceKey = tester
        .deployProcess(testProcess, "testProcess.bpmn")
        .startProcessInstance(processId)
        .and().waitUntil()
        .flowNodesAreActive(taskId, 2)
        .getProcessInstanceKey();

    //when 1.3 - instance count by flowNodeId
    FlowNodeMetadataDto flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(processInstanceKey), taskId, null, null);

    //then
    assertThat(flowNodeMetadata.getInstanceCount()).isEqualTo(2);
    assertThat(flowNodeMetadata.getBreadcrumb()).isEmpty();
    assertThat(flowNodeMetadata.getFlowNodeType()).isEqualTo(SERVICE_TASK);
    assertThat(flowNodeMetadata.getFlowNodeId()).isEqualTo(taskId);

    //when 3.2 - instance count by breadcrump
    flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(processInstanceKey), taskId, SERVICE_TASK, null);

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
    final String jobType = "taskAJob";
    final String processId = "testProcess";
    final BpmnModelInstance testProcess =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .parallelGateway("parallel")
            .exclusiveGateway("exclusive")
            .moveToNode("parallel")
            .connectTo("exclusive")
            .serviceTask(taskId).zeebeJobType(jobType)
            .endEvent()
            .done();
    final long processInstanceKey = tester
        .deployProcess(testProcess, "testProcess.bpmn").waitUntil().processIsDeployed()
        .startProcessInstance(processId).waitUntil().processInstanceIsStarted()
        .and().waitUntil()
        .flowNodesAreActive(taskId, 2)
        .eventIsImported(jobType)
        .getProcessInstanceKey();
    final List<FlowNodeInstanceDto> flowNodeInstances = tester.getFlowNodeInstanceOneListFromRest(
        String.valueOf(processInstanceKey));
    final List<FlowNodeInstanceDto> tasks = flowNodeInstances.stream()
        .filter(fni -> fni.getType().equals(SERVICE_TASK)).collect(Collectors.toList());
    final String flowNodeInstanceId = tasks.get(0).getId();

    //when 2.3 - one instance out of several (Peter case)
    FlowNodeMetadataDto flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(processInstanceKey), null, null, flowNodeInstanceId);

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
        SERVICE_TASK, flowNodeInstanceId, false, jobType);
  }

  /**
   * Use case 3.3.
   */
  @Test
  public void shouldReturnInstanceCountInsideMultiInstance() throws Exception {
    //having process with parallel multi-instance subprocess
    final String taskId = "taskA";
    final String processId = "testProcess";
    final String subprocessId = "subprocess";
    final BpmnModelInstance testProcess =
        Bpmn.createExecutableProcess(processId)
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
    final long processInstanceKey = tester
        .deployProcess(testProcess, "testProcess.bpmn")
        .startProcessInstance(processId, "{\"items\": [0, 1, 2]}")
        .and().waitUntil()
        .flowNodeIsActive(taskId)
        .getProcessInstanceKey();

    //when 3.3 - instance count by breadcrump
    FlowNodeMetadataDto flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(processInstanceKey), subprocessId, SUB_PROCESS, null);

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
      boolean endDateExists, final String jobType) {
    assertThat(metadata.getFlowNodeId()).isEqualTo(flowNodeId);
    assertThat(metadata.getFlowNodeType()).isEqualTo(flowNodeType);
    assertThat(metadata.getFlowNodeInstanceId())
        .isEqualTo(flowNodeInstanceId);
    assertThat(metadata.getStartDate()).isNotNull();
    assertThat(metadata.getJobType()).isEqualTo(jobType);
    if (endDateExists) {
      assertThat(metadata.getEndDate()).isNotNull();
      assertThat(metadata.getEndDate()).isAfter(metadata.getStartDate());
    }
  }

  @Test
  public void testMetadataForFinishedProcess() throws Exception {
    // having
    final String processId = "processWithGateway";
    final String taskA = "taskA";
    final String taskC = "taskC";
    final String errorMessage = "Some error";
    final Long processInstanceKey = tester.deployProcess("processWithGateway.bpmn")
        .startProcessInstance(processId, "{\"a\": \"b\"}")
        .and()
        .failTask(taskA, errorMessage)
        .waitUntil()
        .incidentIsActive()
        .and()
        .resolveIncident()
        .waitUntil()
        .flowNodeIsActive(taskA)
        .and()
        .completeTask(taskA, taskA, "{\"goToTaskC\":true}")
        .waitUntil()
        .flowNodeIsActive(taskC)
        .and()
        .completeTask(taskC, taskC, "{\"b\": \"d\"}")
        .waitUntil()
        .processInstanceIsCompleted()
        .getProcessInstanceKey();

    //get flow node instance tree
    final String processInstanceId = String.valueOf(processInstanceKey);
    FlowNodeInstanceQueryDto request = new FlowNodeInstanceQueryDto(
        processInstanceId, processInstanceId);
    List<FlowNodeInstanceDto> instances = tester.getFlowNodeInstanceOneListFromRest(request);

    assertMetadata(instances, EventSourceType.PROCESS_INSTANCE, EventType.ELEMENT_COMPLETED,
        processInstanceKey, "start");
    try {
      assertMetadata(instances, EventSourceType.PROCESS_INSTANCE, EventType.ELEMENT_COMPLETED,
          processInstanceKey, "taskA");
    } catch (AssertionError ae) {
      assertMetadata(instances, EventSourceType.JOB, EventType.COMPLETED, processInstanceKey,
          "taskA");
    }
    assertMetadata(instances, EventSourceType.PROCESS_INSTANCE, EventType.ELEMENT_COMPLETED,
        processInstanceKey, "gateway");
    try {
      assertMetadata(instances, EventSourceType.PROCESS_INSTANCE, EventType.ELEMENT_COMPLETED,
          processInstanceKey, "taskC");
    } catch (AssertionError ae) {
      assertMetadata(instances, EventSourceType.JOB, EventType.COMPLETED, processInstanceKey,
          "taskC");
    }
    assertMetadata(instances, EventSourceType.PROCESS_INSTANCE, EventType.ELEMENT_COMPLETED,
        processInstanceKey, "end1");

  }

  @Test
  public void testMetadataForCanceledOnIncident() throws Exception {
    // having
    String flowNodeId = "taskA";

    String processId = "demoProcess";
    final Long processInstanceKey = tester.deployProcess("demoProcess_v_1.bpmn")
        .startProcessInstance(processId, null)
        .waitUntil()
        .incidentIsActive()
        .and()
        .cancelProcessInstanceOperation()
        .waitUntil()
        .operationIsCompleted()
        .and()
        .processInstanceIsFinished()
        .getProcessInstanceKey();

    //when
    //get flow node instance tree
    final String processInstanceId = String.valueOf(processInstanceKey);
    FlowNodeInstanceQueryDto request = new FlowNodeInstanceQueryDto(
        processInstanceId, processInstanceId);
    List<FlowNodeInstanceDto> instances = tester.getFlowNodeInstanceOneListFromRest(request);

    //then
    try {
      assertMetadata(instances, EventSourceType.PROCESS_INSTANCE, EventType.ELEMENT_TERMINATED,
          processInstanceKey, flowNodeId);
    } catch (AssertionError ae) {
      assertMetadata(instances, EventSourceType.INCIDENT, EventType.RESOLVED, processInstanceKey,
          flowNodeId);
    }

  }

  @Test
  public void testMetadataIncidentOnInputMapping() throws Exception {
    // having
    String processId = "demoProcess";
    BpmnModelInstance process = Bpmn.createExecutableProcess(processId)
        .startEvent("start")
        .serviceTask("task1").zeebeJobType("task1")
        .zeebeInput("=var", "varIn")
        .endEvent()
        .done();

    final Long processInstanceKey = tester.deployProcess(process, processId + ".bpmn")
        .startProcessInstance(processId, "{\"a\": \"b\"}")      //wrong payload provokes incident
        .waitUntil()
        .incidentIsActive()
        .getProcessInstanceKey();

    //when
    //get flow node instance tree
    final String processInstanceId = String.valueOf(processInstanceKey);
    FlowNodeInstanceQueryDto request = new FlowNodeInstanceQueryDto(
        processInstanceId, processInstanceId);
    List<FlowNodeInstanceDto> instances = tester.getFlowNodeInstanceOneListFromRest(request);

    //then last event does not have a jobId
    final FlowNodeMetadataDto flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(processInstanceKey),
        null, null, instances.get(instances.size() - 1).getId());
    FlowNodeInstanceMetadataDto flowNodeInstanceMetadata = flowNodeMetadata.getInstanceMetadata();
    assertThat(flowNodeMetadata.getIncident().getJobId()).isNull();
    //assert incident fields
    assertThat(flowNodeMetadata.getIncident().getErrorMessage()).isNotNull();
    assertThat(flowNodeMetadata.getIncident().getErrorType()).isNotNull();

  }

  /**
   * parentProcess -> calledProcess -> process (has incident)
   * Getting the metadata from parentProcess and calledProcess should return incident information
   * from process (last one called).
   * @throws Exception
   */
  @Test
  public void testSingleIncidentMetadataForCallActivity() throws Exception {
    //having process with call activity
    final String parentProcessId = "parentProcess";
    final String callActivity1Id = "callActivity1";
    final String calledProcess1Id = "calledProcess";
    final String callActivity2Id = "callActivity2";
    final String calledProcess2Id = "process";
    final String failingFlowNodeId = "task";
    final BpmnModelInstance testProcess =
        Bpmn.createExecutableProcess(parentProcessId)
            .startEvent()
            .callActivity(callActivity1Id)
            .zeebeProcessId(calledProcess1Id)
            .done();
    tester.deployProcess(testProcess, "testProcess.bpmn")
        .getProcessDefinitionKey();
    final BpmnModelInstance testProcess2 =
        Bpmn.createExecutableProcess(calledProcess1Id)
            .startEvent()
            .callActivity(callActivity2Id)
            .zeebeProcessId(calledProcess2Id)
            .done();
    final Long calledProcessDefinitionKey1 = tester
        .deployProcess(testProcess2, "testProcess2.bpmn").getProcessDefinitionKey();
    final long calledProcessDefinitionKey2 = tester
        .deployProcess("single-task.bpmn")
        .getProcessDefinitionKey();

    final String errorMsg = "Error in task execution";
    final long parentProcessInstanceKey = tester
        .startProcessInstance(parentProcessId, null)
        .and().waitUntil()
        .conditionIsMet(processInstancesAreStartedByProcessId, calledProcessDefinitionKey2, 1)
        .and()
        .failTask(failingFlowNodeId, errorMsg)
        .waitUntil()
        .incidentsInAnyInstanceAreActive(1)
        .getProcessInstanceKey();

    //when
    //get metadata by flowNodeId from parent process instance
    final String processInstanceId = String.valueOf(parentProcessInstanceKey);
    FlowNodeMetadataDto flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        processInstanceId, callActivity1Id, null, null);

    //then one incident is returned
    assertThat(flowNodeMetadata.getIncidentCount()).isEqualTo(1);
    IncidentDto incident = flowNodeMetadata.getIncident();
    assertSingleIncident(incident, calledProcess2Id, errorMsg);
    String flowNodeInstanceId = flowNodeMetadata.getFlowNodeInstanceId();

    //when
    //get metadata by flowNodeInstanceId from parent process instance
    flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        processInstanceId, null, null, flowNodeInstanceId);

    //then one incident is returned
    assertThat(flowNodeMetadata.getIncidentCount()).isEqualTo(1);
    incident = flowNodeMetadata.getIncident();
    assertSingleIncident(incident, calledProcess2Id, errorMsg);

    //when
    //get metadata by flowNodeId from calledProcess
    final String calledProcessInstanceId1 = tester.getSingleProcessInstanceByBpmnProcessId(
        String.valueOf(calledProcessDefinitionKey1)).getId();
    flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        calledProcessInstanceId1, callActivity2Id, null, null);

    //then one incident is returned
    assertThat(flowNodeMetadata.getIncidentCount()).isEqualTo(1);
    incident = flowNodeMetadata.getIncident();
    assertSingleIncident(incident, calledProcess2Id, errorMsg);
    flowNodeInstanceId = flowNodeMetadata.getFlowNodeInstanceId();

    //when
    //get metadata by flowNodeInstanceId from calledProcess
    flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        calledProcessInstanceId1, null, null, flowNodeInstanceId);

    //then one incident is returned
    assertThat(flowNodeMetadata.getIncidentCount()).isEqualTo(1);
    incident = flowNodeMetadata.getIncident();
    assertSingleIncident(incident, calledProcess2Id, errorMsg);

  }

  /**
   * parentProcess -> calledProcess (has incident) -> process (has incident)
   * Getting the metadata from parentProcess should return incident count = 2.
   * @throws Exception
   */
  @Test
  public void testTwoIncidentsMetadataForCallActivity() throws Exception {
    //having process with call activity
    final String parentProcessId = "parentProcess";
    final String callActivity1Id = "callActivity1";
    final String calledProcess1Id = "calledProcess";
    final String callActivity2Id = "callActivity2";
    final String calledProcess2Id = "process";
    final String lastCalledTaskId = "task";
    final String serviceTaskId = "serviceTask";
    final String errorMsg1 = "Error in called process task";
    final String errorMsg2 = "Error in last called process task";
    final BpmnModelInstance testProcess =
        Bpmn.createExecutableProcess(parentProcessId)
            .startEvent()
            .callActivity(callActivity1Id)
            .zeebeProcessId(calledProcess1Id)
            .done();
    final BpmnModelInstance testProcess2 =
        Bpmn.createExecutableProcess(calledProcess1Id)
            .startEvent()
            .parallelGateway("parallel")
            .serviceTask(serviceTaskId)
            .zeebeJobType(serviceTaskId)
            .moveToNode("parallel")
            .callActivity(callActivity2Id)
            .zeebeProcessId(calledProcess2Id)
            .done();
    final long calledProcessDefinitionKey2 = tester
        .deployProcess("single-task.bpmn")
        .getProcessDefinitionKey();

    final long parentProcessInstanceKey = tester
        .deployProcess(testProcess, "testProcess.bpmn")
        .deployProcess(testProcess2, "testProcess2.bpmn")
        .startProcessInstance(parentProcessId, null)
        .and().waitUntil()
        .conditionIsMet(processInstancesAreStartedByProcessId, calledProcessDefinitionKey2, 1)
        .and()
        .failTask(lastCalledTaskId, errorMsg2)
        .and()
        .failTask(serviceTaskId, errorMsg1)
        .waitUntil()
        .incidentsInAnyInstanceAreActive(2)
        .getProcessInstanceKey();

    //when
    //get metadata by flowNodeId from parent process instance
    final String processInstanceId = String.valueOf(parentProcessInstanceKey);
    FlowNodeMetadataDto flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        processInstanceId, callActivity1Id, null, null);

    //then two incidents are returned
    assertThat(flowNodeMetadata.getIncidentCount()).isEqualTo(2);
    assertThat(flowNodeMetadata.getIncident()).isNull();

  }

  /**
   * parentProcess -> calledProcess (Peter case: 2 instances of call activity) -> process (each of two instances has incident)
   * Getting the metadata from parentProcess should return incident count = 2.
   * Getting the metadata from calledProcess and flowNodeId should return instance count = incident count = 2.
   * @throws Exception
   */
  @Test
  public void testTwoIncidentsMetadataForCallActivityPeterCase() throws Exception {
    //having process with call activity
    final String parentProcessId = "parentProcess";
    final String callActivity1Id = "callActivity1";
    final String calledProcess1Id = "calledProcess";
    final String callActivity2Id = "callActivity2";
    final String calledProcess2Id = "process";
    final String lastCalledTaskId = "task";
    final String errorMsg2 = "Error in last called process task";
    final BpmnModelInstance testProcess =
        Bpmn.createExecutableProcess(parentProcessId)
            .startEvent()
            .callActivity(callActivity1Id)
            .zeebeProcessId(calledProcess1Id)
            .done();
    final BpmnModelInstance testProcess2 =
        Bpmn.createExecutableProcess(calledProcess1Id)
            .startEvent()
            .parallelGateway("parallel")
            .exclusiveGateway("exclusive")
            .moveToNode("parallel")
            .connectTo("exclusive")
            .callActivity(callActivity2Id)
            .zeebeProcessId(calledProcess2Id)
            .done();
    final Long calledProcessDefinitionKey1 = tester
        .deployProcess(testProcess2, "testProcess2.bpmn").getProcessDefinitionKey();
    final long calledProcessDefinitionKey2 = tester
        .deployProcess("single-task.bpmn")
        .getProcessDefinitionKey();

    final long parentProcessInstanceKey = tester
        .deployProcess(testProcess, "testProcess.bpmn")
        .startProcessInstance(parentProcessId, null)
        .and().waitUntil()
        .conditionIsMet(processInstancesAreStartedByProcessId, calledProcessDefinitionKey2, 2)
        .and()
        .failTask(lastCalledTaskId, errorMsg2)
        .failTask(lastCalledTaskId, errorMsg2)
        .waitUntil()
        .incidentsInAnyInstanceAreActive(2)
        .getProcessInstanceKey();

    //when
    //get metadata by flowNodeId from parent process instance
    final String processInstanceId = String.valueOf(parentProcessInstanceKey);
    FlowNodeMetadataDto flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        processInstanceId, callActivity1Id, null, null);

    //then two incidents are returned
    assertThat(flowNodeMetadata.getIncidentCount()).isEqualTo(2);
    assertThat(flowNodeMetadata.getIncident()).isNull();

    //when
    //get metadata by flowNodeId from called process instance
    final String calledProcessInstanceId1 = tester.getSingleProcessInstanceByBpmnProcessId(
        String.valueOf(calledProcessDefinitionKey1)).getId();
    flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        calledProcessInstanceId1, callActivity2Id, null, null);

    //then two incidents are returned
    assertThat(flowNodeMetadata.getIncidentCount()).isEqualTo(2);
    assertThat(flowNodeMetadata.getIncident()).isNull();
    //two flow node instances
    assertThat(flowNodeMetadata.getInstanceCount()).isEqualTo(2);
    assertThat(flowNodeMetadata.getInstanceMetadata()).isNull();

  }


  /**
   * parentProcess -> calledProcess (multi-instance: 2 instances of call activity) -> process (each of two instances has incident)
   * Getting the metadata from parentProcess should return incident count = 2.
   * Getting the metadata from calledProcess and flowNodeId should return instance count = incident count = 2.
   * @throws Exception
   */
  @Test
  public void testTwoIncidentsMetadataForCallActivityMultiInstance() throws Exception {
    //having process with call activity
    final String parentProcessId = "parentProcess";
    final String callActivity1Id = "callActivity1";
    final String calledProcess1Id = "calledProcess";
    final String callActivity2Id = "callActivity2";
    final String calledProcess2Id = "process";
    final String lastCalledTaskId = "task";
    final String errorMsg2 = "Error in last called process task";
    final BpmnModelInstance testProcess =
        Bpmn.createExecutableProcess(parentProcessId)
            .startEvent()
            .callActivity(callActivity1Id)
            .zeebeProcessId(calledProcess1Id)
            .zeebeInput("=items", "items")
            .done();
    final BpmnModelInstance testProcess2 =
        Bpmn.createExecutableProcess(calledProcess1Id)
            .startEvent()
            .callActivity(callActivity2Id,
                ca -> ca.multiInstance(m -> m.zeebeInputCollectionExpression("items").parallel()))
            .zeebeProcessId(calledProcess2Id)
            .done();
    final Long calledProcessDefinitionKey1 = tester
        .deployProcess(testProcess2, "testProcess2.bpmn").getProcessDefinitionKey();
    final long calledProcessDefinitionKey2 = tester
        .deployProcess("single-task.bpmn")
        .getProcessDefinitionKey();

    final long parentProcessInstanceKey = tester
        .deployProcess(testProcess, "testProcess.bpmn")
        .startProcessInstance(parentProcessId, "{\"items\": [0, 1]}")
        .and().waitUntil()
        .conditionIsMet(processInstancesAreStartedByProcessId, calledProcessDefinitionKey2, 2)
        .and()
        .failTask(lastCalledTaskId, errorMsg2)
        .failTask(lastCalledTaskId, errorMsg2)
        .waitUntil()
        .incidentsInAnyInstanceAreActive(2)
        .getProcessInstanceKey();

    //when
    //get metadata by flowNodeId from parent process instance
    final String processInstanceId = String.valueOf(parentProcessInstanceKey);
    FlowNodeMetadataDto flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        processInstanceId, callActivity1Id, null, null);

    //then two incidents are returned
    assertThat(flowNodeMetadata.getIncidentCount()).isEqualTo(2);
    assertThat(flowNodeMetadata.getIncident()).isNull();

    //when
    //get metadata by flowNodeId and flowNodeType=CALL_ACTIVITY from called process instance
    final String calledProcessInstanceId1 = tester.getSingleProcessInstanceByBpmnProcessId(
        String.valueOf(calledProcessDefinitionKey1)).getId();
    flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        calledProcessInstanceId1, callActivity2Id, CALL_ACTIVITY, null);

    //then two incidents are returned
    assertThat(flowNodeMetadata.getIncidentCount()).isEqualTo(2);
    assertThat(flowNodeMetadata.getIncident()).isNull();
    //two flow node instances
    assertThat(flowNodeMetadata.getInstanceCount()).isEqualTo(2);
    assertThat(flowNodeMetadata.getInstanceMetadata()).isNull();
  }


  @Test
  public void testMessageData() throws Exception {
    // having
    final OffsetDateTime testStartTime = OffsetDateTime.now();

    String processId = "eventProcess";
    final String correlationKey = "5";
    final String messageName = "clientMessage";
    deployProcess("messageEventProcess_v_1.bpmn");
    Long processInstanceKey = tester.deployProcess("messageEventProcess_v_1.bpmn")
        .waitUntil().processIsDeployed()
        .and()
        .startProcessInstance(processId,"{\"clientId\": \"5\"}")
        .waitUntil().processInstanceIsStarted()
        .then()
        .getProcessInstanceKey();

    // when
    tester.waitUntil()
        .flowNodeIsActive("IntermediateCatchEvent_1ds9kwv");

    // then
    // assert flow node instances
    final List<FlowNodeInstanceEntity> allFlowNodeInstances = tester
        .getAllFlowNodeInstances(processInstanceKey);
    assertThat(allFlowNodeInstances).hasSize(2);
    final String messageEventId = allFlowNodeInstances.get(1).getId();
    final FlowNodeMetadataDto flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(processInstanceKey), null, null, messageEventId);
    assertThat(flowNodeMetadata.getInstanceMetadata()).isNotNull();
    assertFlowNodeInstanceMessageData(flowNodeMetadata.getInstanceMetadata(), messageName,
        correlationKey);
  }

  private void assertFlowNodeInstanceMessageData(final FlowNodeInstanceMetadataDto metadata,
      final String messageName, final String correlationKey) {
    assertThat(metadata.getMessageName()).isEqualTo(messageName);
    assertThat(metadata.getCorrelationKey()).isEqualTo(correlationKey);
  }

  private void assertSingleIncident(final IncidentDto incident, final String bpmnProcessId,
      final String errorMsg) {
    assertThat(incident).isNotNull();
    assertThat(incident.getErrorType().getId())
        .isEqualTo(ErrorType.JOB_NO_RETRIES.name());
    assertThat(incident.getErrorMessage()).isEqualTo(errorMsg);
    final ProcessInstanceReferenceDto rootCauseInstance = incident
        .getRootCauseInstance();
    assertThat(rootCauseInstance).isNotNull();
    assertThat(rootCauseInstance.getProcessDefinitionName())
        .isEqualTo(bpmnProcessId);
    final ProcessInstanceForListViewEntity rootProcessInstance = processInstanceReader
        .getProcessInstanceByKey(Long.valueOf(rootCauseInstance.getInstanceId()));
    assertThat(rootProcessInstance.getBpmnProcessId()).isEqualTo(bpmnProcessId);
    assertThat(rootProcessInstance.getProcessDefinitionKey())
        .isEqualTo(Long.valueOf(rootCauseInstance.getProcessDefinitionId()));
  }

  @Test
  public void testFlowNodeMetadataFailsWithWrongRequst() throws Exception {
    FlowNodeMetadataRequestDto request = new FlowNodeMetadataRequestDto();

    MvcResult mvcResult = postRequestThatShouldFail(
        PROCESS_INSTANCE_URL + "/111/flow-node-metadata", request);
    assertErrorMessageIsEqualTo(mvcResult,
        "At least flowNodeId or flowNodeInstanceId must be specifies in the request.");

    request = new FlowNodeMetadataRequestDto("flowNodeId", "flowNodeInstanceId", null);
    mvcResult = postRequestThatShouldFail(
        PROCESS_INSTANCE_URL + "/111/flow-node-metadata", request);
    assertErrorMessageIsEqualTo(mvcResult,
        "Only one of flowNodeId or flowNodeInstanceId must be specifies in the request.");
  }

  private void assertMetadata(List<FlowNodeInstanceDto> flowNodes, EventSourceType eventSourceType,
      EventType eventType,
      Long processInstanceKey, String activityId)
      throws Exception {
    assertMetadata(flowNodes, eventSourceType, eventType, processInstanceKey, activityId, null);
  }

  private void assertMetadata(List<FlowNodeInstanceDto> flowNodes, EventSourceType eventSourceType,
      EventType eventType, Long processInstanceKey, String flowNodeId, String errorMessage)
      throws Exception {

    final Optional<FlowNodeInstanceDto> flowNodeInstance = flowNodes.stream()
        .filter(fni -> fni.getFlowNodeId().equals(flowNodeId)).findFirst();
    assertThat(flowNodeInstance).isNotEmpty();

    //call REST API to get metadata
    final FlowNodeMetadataDto flowNodeMetadata = tester.getFlowNodeMetadataFromRest(
        String.valueOf(processInstanceKey),
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
      final IncidentDto incident = flowNodeMetadata.getIncident();
      if (errorMessage != null) {
        assertThat(incident.getErrorMessage())
            .as(assertionName + ".incidentErrorMessage").isEqualTo(errorMessage);
      } else {
        assertThat(incident.getErrorMessage())
            .as(assertionName + ".incidentErrorMessage").isNotEmpty();
      }
      assertThat(incident.getErrorType())
          .as(assertionName + ".incidentErrorType").isNotNull();
    }

  }

}
