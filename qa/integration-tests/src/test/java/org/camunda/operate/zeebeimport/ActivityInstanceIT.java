/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.webapp.rest.ActivityInstanceRestService.ACTIVITY_INSTANCE_URL;

import java.util.List;

import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.ActivityType;
import org.camunda.operate.webapp.rest.dto.activity.ActivityInstanceDto;
import org.camunda.operate.webapp.rest.dto.activity.ActivityInstanceTreeDto;
import org.camunda.operate.webapp.rest.dto.activity.ActivityInstanceTreeRequestDto;
import org.camunda.operate.util.ConversionUtils;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.ZeebeTestUtil;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.core.type.TypeReference;

public class ActivityInstanceIT extends OperateZeebeIntegrationTest {

  @Test
  public void testActivityInstanceTreeForNonInterruptingBoundaryEvent() throws Exception {
    // having
    String processId = "nonInterruptingBoundaryEvent";
    deployWorkflow("nonInterruptingBoundaryEvent_v_2.bpmn");
    final Long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, null);
    //let the boundary event happen
    //Thread.sleep(1500L);
    elasticsearchTestRule.processAllRecordsAndWait(activityIsActiveCheck, workflowInstanceKey, "task2");

    //when
    ActivityInstanceTreeDto response = getActivityInstanceTreeFromRest(workflowInstanceKey);

    //then
    assertThat(response.getChildren()).hasSize(4);
    assertThat(response.getChildren()).hasSize(4);
    assertChild(response.getChildren(), 0, "startEvent", ActivityState.COMPLETED, workflowInstanceKey, ActivityType.START_EVENT, 0);
    assertChild(response.getChildren(), 1, "task1", ActivityState.ACTIVE, workflowInstanceKey, ActivityType.SERVICE_TASK, 0);
    assertChild(response.getChildren(), 2, "boundaryEvent", ActivityState.COMPLETED, workflowInstanceKey, ActivityType.BOUNDARY_EVENT, 0);
    assertChild(response.getChildren(), 3, "task2", ActivityState.ACTIVE, workflowInstanceKey, ActivityType.SERVICE_TASK, 0);
  }

  @Test
  public void testActivityInstanceTreeIsBuild() throws Exception {
    // having
    String processId = "prWithSubprocess";
    deployWorkflow("subProcess_old.bpmn");
    final Long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, null);
    ZeebeTestUtil.completeTask(zeebeClient, "taskA", getWorkerName(), null);
    elasticsearchTestRule.processAllRecordsAndWait(activityIsActiveCheck, workflowInstanceKey, "taskB");
    ZeebeTestUtil.completeTask(zeebeClient, "taskB", getWorkerName(), null);
    elasticsearchTestRule.processAllRecordsAndWait(activityIsActiveCheck, workflowInstanceKey, "taskC");
    ZeebeTestUtil.failTask(zeebeClient, "taskC", getWorkerName(), 3, "some error");
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, workflowInstanceKey);

    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when
    ActivityInstanceTreeDto response = getActivityInstanceTreeFromRest(workflowInstanceKey);

    //then
    assertThat(response.getChildren()).hasSize(3);

    assertThat(response.getChildren()).hasSize(3);
    assertChild(response.getChildren(), 0, "startEvent", ActivityState.COMPLETED, workflowInstanceKey, ActivityType.START_EVENT, 0);
    assertChild(response.getChildren(), 1, "taskA", ActivityState.COMPLETED, workflowInstanceKey, ActivityType.SERVICE_TASK, 0);
    ActivityInstanceDto subprocess = assertChild(response.getChildren(), 2, "subprocess", ActivityState.INCIDENT, workflowInstanceKey, ActivityType.SUB_PROCESS, 3);

    assertThat(subprocess.getChildren()).hasSize(3);
    assertChild(subprocess.getChildren(), 0, "startEventSubprocess", ActivityState.COMPLETED, ConversionUtils.toLongOrNull(subprocess.getId()), ActivityType.START_EVENT, 0);
    final ActivityInstanceDto innerSubprocess = assertChild(subprocess.getChildren(), 1, "innerSubprocess", ActivityState.COMPLETED,
        ConversionUtils.toLongOrNull(subprocess.getId()), ActivityType.SUB_PROCESS, 3);
    assertChild(subprocess.getChildren(), 2, "taskC", ActivityState.INCIDENT, ConversionUtils.toLongOrNull(subprocess.getId()), ActivityType.SERVICE_TASK, 0);

    assertThat(innerSubprocess.getChildren()).hasSize(3);
    assertChild(innerSubprocess.getChildren(), 0, "startEventInnerSubprocess", ActivityState.COMPLETED, ConversionUtils.toLongOrNull(innerSubprocess.getId()), ActivityType.START_EVENT, 0);
    assertChild(innerSubprocess.getChildren(), 1, "taskB", ActivityState.COMPLETED, ConversionUtils.toLongOrNull(innerSubprocess.getId()), ActivityType.SERVICE_TASK, 0);
    assertChild(innerSubprocess.getChildren(), 2, "endEventInnerSubprocess", ActivityState.COMPLETED, ConversionUtils.toLongOrNull(innerSubprocess.getId()), ActivityType.END_EVENT, 0);

  }

  protected ActivityInstanceDto assertChild(List<ActivityInstanceDto> children, int childPosition, String activityId, ActivityState state, Long parentId, ActivityType type, int numberOfChildren) {
    final ActivityInstanceDto ai = children.get(childPosition);
    assertThat(ai.getActivityId()).isEqualTo(activityId);
    assertThat(ai.getId()).isNotNull();
    assertThat(ai.getState()).isEqualTo(state);
    assertThat(ai.getParentId()).isEqualTo(ConversionUtils.toStringOrNull(parentId));
    assertThat(ai.getStartDate()).isNotNull();
    if (state.equals(ActivityState.COMPLETED) || state.equals(ActivityState.TERMINATED)) {
      assertThat(ai.getEndDate()).isNotNull();
      assertThat(ai.getStartDate()).isBeforeOrEqualTo(ai.getEndDate());
    } else {
      assertThat(ai.getEndDate()).isNull();
    }
    assertThat(ai.getType()).isEqualTo(type);
    assertThat(ai.getChildren()).hasSize(numberOfChildren);
    return ai;
  }

  @Test
  public void testActivityInstanceTreeIncidentStatePropagated() throws Exception {

    // having
    String processId = "prWithSubprocess";
    deployWorkflow("subProcess_old.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, null);
    ZeebeTestUtil.completeTask(zeebeClient, "taskA", getWorkerName(), null);
    elasticsearchTestRule.processAllRecordsAndWait(activityIsActiveCheck, workflowInstanceKey, "taskB");
    ZeebeTestUtil.failTask(zeebeClient, "taskB", getWorkerName(), 3, "some error");
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, workflowInstanceKey);

    //when
    ActivityInstanceTreeDto response = getActivityInstanceTreeFromRest(workflowInstanceKey);

    //then
    assertThat(response.getChildren()).filteredOn("activityId", "subprocess").hasSize(1);
    final ActivityInstanceDto subprocess = response.getChildren().stream().filter(ai -> ai.getActivityId().equals("subprocess"))
      .findFirst().get();
    assertThat(subprocess.getState()).isEqualTo(ActivityState.INCIDENT);
    assertThat(response.getChildren()).filteredOn(ai -> !ai.getActivityId().equals("subprocess")).allMatch(ai -> !ai.getState().equals(ActivityState.INCIDENT));

    assertThat(subprocess.getChildren()).filteredOn("activityId", "innerSubprocess").hasSize(1);
    final ActivityInstanceDto innerSuprocess = subprocess.getChildren().stream().filter(ai -> ai.getActivityId().equals("innerSubprocess"))
      .findFirst().get();
    assertThat(innerSuprocess.getState()).isEqualTo(ActivityState.INCIDENT);
    assertThat(subprocess.getChildren()).filteredOn(ai -> !ai.getActivityId().equals("innerSubprocess")).allMatch(ai -> !ai.getState().equals(ActivityState.INCIDENT));

    assertThat(innerSuprocess.getChildren()).filteredOn("activityId", "taskB").allMatch(ai -> ai.getState().equals(ActivityState.INCIDENT));
    assertThat(innerSuprocess.getChildren()).filteredOn(ai -> !ai.getActivityId().equals("taskB")).allMatch(ai -> !ai.getState().equals(ActivityState.INCIDENT));

  }

  @Test
  public void testActivityInstanceTreeFails() throws Exception {
    ActivityInstanceTreeRequestDto treeRequest = new ActivityInstanceTreeRequestDto();

    MvcResult mvcResult = postRequestThatShouldFail(ACTIVITY_INSTANCE_URL,treeRequest);

    assertErrorMessageIsEqualTo(mvcResult, "Workflow instance id must be provided when requesting for activity instance tree.");
  }

  protected ActivityInstanceTreeDto getActivityInstanceTreeFromRest(Long workflowInstanceKey) throws Exception {
    ActivityInstanceTreeRequestDto treeRequest = new ActivityInstanceTreeRequestDto(workflowInstanceKey.toString());

    MvcResult mvcResult =  postRequest(ACTIVITY_INSTANCE_URL,treeRequest);

    return mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ActivityInstanceTreeDto>() { });
  }

}
