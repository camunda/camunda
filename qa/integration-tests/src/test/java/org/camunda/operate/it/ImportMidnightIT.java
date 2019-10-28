/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.it;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceState;
import org.camunda.operate.es.reader.ActivityInstanceReader;
import org.camunda.operate.es.reader.ListViewReader;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.TestUtil;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.webapp.rest.dto.activity.ActivityInstanceDto;
import org.camunda.operate.webapp.rest.dto.activity.ActivityInstanceTreeDto;
import org.camunda.operate.webapp.rest.dto.activity.ActivityInstanceTreeRequestDto;
import org.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import org.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import org.camunda.operate.webapp.rest.dto.listview.ListViewWorkflowInstanceDto;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import static org.assertj.core.api.Assertions.assertThat;

public class ImportMidnightIT extends OperateZeebeIntegrationTest {

  @Autowired
  private WorkflowInstanceReader workflowInstanceReader;

  @Autowired
  private ActivityInstanceReader activityInstanceReader;

  @Autowired
  private ListViewReader listViewReader;

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule  = new ElasticsearchTestRule() {
    @Override
    public void refreshZeebeESIndices() {
      //do nothing
    }
  };

  @Override
  public void before() {
    super.before();
  }

  private ActivityInstanceTreeDto getActivityInstanceTree(Long workflowInstanceKey) {
    return activityInstanceReader.getActivityInstanceTree(new ActivityInstanceTreeRequestDto(workflowInstanceKey.toString()));
  }

  private ListViewWorkflowInstanceDto getSingleWorkflowInstanceForListView(ListViewRequestDto request) {
    final ListViewResponseDto listViewResponse = listViewReader.queryWorkflowInstances(request, 0, 100);
    assertThat(listViewResponse.getTotalCount()).isEqualTo(1);
    assertThat(listViewResponse.getWorkflowInstances()).hasSize(1);
    return listViewResponse.getWorkflowInstances().get(0);
  }

  @Test
  public void testWorkflowInstancesCompletedNextDay() {
    // having
    String processId = "demoProcess";
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
        .startEvent("start")
          .serviceTask("task1").zeebeTaskType("task1")
          .serviceTask("task2").zeebeTaskType("task2")
        .endEvent().done();
    deployWorkflow(workflow, "demoProcess_v_1.bpmn");

    //disable automatic index refreshes
    zeebeRule.updateRefreshInterval("-1");

    final Instant firstDate = brokerRule.getClock().getCurrentTime();
    fillIndicesWithData(processId, firstDate);

    //start workflow instance
    long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    completeTask(workflowInstanceKey, "task1", null, false);
    //let Zeebe export data
    try {
      Thread.sleep(2000L);
    } catch (InterruptedException e) {
    }
    //complete instances next day
    Instant secondDate = firstDate.plus(1, ChronoUnit.DAYS);
    brokerRule.getClock().setCurrentTime(secondDate);
    completeTask(workflowInstanceKey, "task2", null, false);
    //let Zeebe export data
    try {
      Thread.sleep(2000L);
    } catch (InterruptedException e) {
    }

    //refresh 2nd date index and load all data
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstanceIsCompletedCheck, () -> {
      zeebeRule.refreshIndices(secondDate);
      return null;
    }, workflowInstanceKey);
    WorkflowInstanceForListViewEntity wi = workflowInstanceReader.getWorkflowInstanceByKey(workflowInstanceKey);
    assertThat(wi.getState()).isEqualTo(WorkflowInstanceState.COMPLETED);

    //when
    //refresh 1st date index and try to load data
    elasticsearchTestRule.processAllRecordsAndWait(activityIsCompletedCheck, () -> {
      zeebeRule.refreshIndices(firstDate);
      return null;
    }, workflowInstanceKey, "task1");

    //then
    wi = workflowInstanceReader.getWorkflowInstanceByKey(workflowInstanceKey);
    assertThat(wi.getState()).isEqualTo(WorkflowInstanceState.COMPLETED);

    ActivityInstanceTreeDto tree = getActivityInstanceTree(workflowInstanceKey);
    assertThat(tree.getChildren()).hasSize(4);
    ActivityInstanceDto activity = tree.getChildren().get(1);
    assertThat(activity.getActivityId()).isEqualTo("task1");
    assertThat(activity.getState()).isEqualTo(ActivityState.COMPLETED);
    assertThat(activity.getEndDate()).isAfterOrEqualTo(OffsetDateTime.ofInstant(firstDate, ZoneOffset.systemDefault()));

    activity = tree.getChildren().get(2);
    assertThat(activity.getActivityId()).isEqualTo("task2");
    assertThat(activity.getState()).isEqualTo(ActivityState.COMPLETED);
    assertThat(activity.getEndDate()).isAfterOrEqualTo(OffsetDateTime.ofInstant(secondDate, ZoneOffset.systemDefault()));

  }

  public void fillIndicesWithData(String processId, Instant firstDate) {
    //two instances for two partitions
    long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    cancelWorkflowInstance(workflowInstanceKey, false);
    try {
      Thread.sleep(2000L);
    } catch (InterruptedException e) {
    }
    zeebeRule.refreshIndices(firstDate);
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstanceIsCanceledCheck, workflowInstanceKey);
    workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    cancelWorkflowInstance(workflowInstanceKey, false);
    try {
      Thread.sleep(2000L);
    } catch (InterruptedException e) {
    }
    zeebeRule.refreshIndices(firstDate);
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstanceIsCanceledCheck, workflowInstanceKey);
  }

}