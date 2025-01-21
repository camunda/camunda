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
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.util.ZeebeTestUtil;
import io.camunda.operate.webapp.reader.IncidentReader;
import io.camunda.operate.webapp.reader.ListViewReader;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import io.camunda.operate.webapp.rest.dto.listview.ProcessInstanceStateDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataDto;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.webapps.schema.entities.operate.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.operate.FlowNodeState;
import io.camunda.webapps.schema.entities.operate.IncidentEntity;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class BasicZeebeImportIT extends OperateZeebeAbstractIT {

  @Autowired private PartitionHolder partitionHolder;

  @Autowired private ListViewReader listViewReader;

  @Autowired private IncidentReader incidentReader;

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
        ZeebeTestUtil.startProcessInstance(camundaClient, processId, "{\"a\": \"b\"}");

    // create an incident
    final Long jobKey =
        ZeebeTestUtil.failTask(getClient(), activityId, getWorkerName(), 3, "Some error");
    searchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, processInstanceKey);
    final long incidentKey = getOnlyIncidentKey(processInstanceKey);

    // when update retries
    ZeebeTestUtil.resolveIncident(camundaClient, jobKey, incidentKey);
    ZeebeTestUtil.completeTask(getClient(), activityId, getWorkerName(), "{}");

    searchTestRule.processAllRecordsAndWait(
        processInstancesAreFinishedCheck, List.of(processInstanceKey));
    searchTestRule.processAllRecordsAndWait(incidentsAreResolved, processInstanceKey, 1);

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
        ZeebeTestUtil.startProcessInstance(camundaClient, processId, "{\"a\": \"b\"}");

    // create an incident
    final Long jobKey =
        ZeebeTestUtil.failTask(getClient(), activityId, getWorkerName(), 3, "Some error");
    searchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, processInstanceKey);
    final long incidentKey = getOnlyIncidentKey(processInstanceKey);

    // when update retries
    ZeebeTestUtil.resolveIncident(camundaClient, jobKey, incidentKey);

    ZeebeTestUtil.cancelProcessInstance(getClient(), processInstanceKey);

    searchTestRule.processAllRecordsAndWait(processInstanceIsCanceledCheck, processInstanceKey);
    searchTestRule.processAllRecordsAndWait(noActivitiesHaveIncident, processInstanceKey);

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
    searchTestRule.processAllRecordsAndWait(
        processInstanceIsCreatedCheck, parentProcessInstanceKey);
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
        camundaClient.newTopologyRequest().send().join().getPartitionsCount();
    assertThat(operatePartitions).hasSize(zeebePartitionsCount);
    assertThat(operatePartitions).allMatch(id -> id <= zeebePartitionsCount && id >= 1);
  }

  private void assertStartActivityCompleted(final FlowNodeInstanceEntity activity) {
    assertFlowNodeIsCompleted(activity, "start");
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
}
