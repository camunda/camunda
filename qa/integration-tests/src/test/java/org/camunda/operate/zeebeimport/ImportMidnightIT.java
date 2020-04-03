/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.camunda.operate.util.TestApplication;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceState;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.webapp.es.reader.ActivityInstanceReader;
import org.camunda.operate.webapp.es.reader.ListViewReader;
import org.camunda.operate.webapp.es.reader.WorkflowInstanceReader;
import org.camunda.operate.webapp.rest.dto.activity.ActivityInstanceDto;
import org.camunda.operate.webapp.rest.dto.activity.ActivityInstanceTreeDto;
import org.camunda.operate.webapp.rest.dto.activity.ActivityInstanceTreeRequestDto;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.util.ThreadUtil.sleepFor;

@SpringBootTest(
    classes = { TestApplication.class},
    properties = { OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
        OperateProperties.PREFIX + ".importer.threadsCount = 1",
        OperateProperties.PREFIX + ".archiver.rolloverEnabled = false"})
public class ImportMidnightIT extends OperateZeebeIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(ImportMidnightIT.class);

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

  @Test
  public void testWorkflowInstancesCompletedNextDay() {
    // having
    String processId = "demoProcess";
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
        .startEvent("start")
          .serviceTask("task1").zeebeJobType("task1")
          .serviceTask("task2").zeebeJobType("task2")
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
    sleepFor(5000);
    //complete instances next day
    Instant secondDate = firstDate.plus(1, ChronoUnit.DAYS);
    brokerRule.getClock().setCurrentTime(secondDate);
    completeTask(workflowInstanceKey, "task2", null, false);
    //let Zeebe export data
    sleepFor(5000);

    //when
    //refresh 2nd date index and load all data
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstanceIsCompletedCheck, () -> {
      zeebeRule.refreshIndices(secondDate);
      return null;
    }, workflowInstanceKey);

    //then internally previous index will also be refreshed and full data will be loaded
    WorkflowInstanceForListViewEntity wi = workflowInstanceReader.getWorkflowInstanceByKey(workflowInstanceKey);
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
    sleepFor(2000);
    zeebeRule.refreshIndices(firstDate);
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstanceIsCanceledCheck, workflowInstanceKey);
    workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    cancelWorkflowInstance(workflowInstanceKey, false);
    sleepFor(2000);
    zeebeRule.refreshIndices(firstDate);
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstanceIsCanceledCheck, workflowInstanceKey);
  }

}