/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.util.TestUtil.createFlowNodeInstanceWithIncident;
import static org.camunda.operate.util.TestUtil.createWorkflowInstance;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.camunda.operate.entities.FlowNodeState;
import org.camunda.operate.entities.FlowNodeType;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.entities.listview.FlowNodeInstanceForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceState;
import org.camunda.operate.util.TestUtil;
import org.camunda.operate.webapp.rest.dto.ActivityStatisticsDto;
import org.camunda.operate.webapp.rest.dto.WorkflowInstanceCoreStatisticsDto;
import org.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import org.camunda.operate.util.CollectionUtil;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.OperateIntegrationTest;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Tests Elasticsearch query for workflow statistics.
 */
public class WorkflowStatisticsIT extends OperateIntegrationTest {

  private static final String QUERY_WORKFLOW_STATISTICS_URL = "/api/workflow-instances/statistics";
  private static final String QUERY_WORKFLOW_CORE_STATISTICS_URL = "/api/workflow-instances/core-statistics";

  private static final Long WORKFLOW_KEY_DEMO_PROCESS = 42L;
  private static final Long WORKFLOW_KEY_OTHER_PROCESS = 27L;

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Test
  public void testOneWorkflowStatistics() throws Exception {
    Long workflowKey = WORKFLOW_KEY_DEMO_PROCESS;

    createData(workflowKey);

    getStatisticsAndAssert(createGetAllWorkflowInstancesQuery(workflowKey));
  }

  @Test
  public void testStatisticsWithQueryByActivityId() throws Exception {
    Long workflowKey = WORKFLOW_KEY_DEMO_PROCESS;

    createData(workflowKey);

    final ListViewQueryDto queryRequest = createGetAllWorkflowInstancesQuery(workflowKey);
    queryRequest.setActivityId("taskA");

    final List<ActivityStatisticsDto> activityStatisticsDtos = getActivityStatistics(queryRequest);
    assertThat(activityStatisticsDtos).hasSize(1);
    assertThat(activityStatisticsDtos).filteredOn(ai -> ai.getActivityId().equals("taskA")).allMatch(ai->
      ai.getActive().equals(2L) && ai.getCanceled().equals(0L) && ai.getCompleted().equals(0L) && ai.getIncidents().equals(0L)
    );
  }

  @Test
  public void testStatisticsWithQueryByErrorMessage() throws Exception {
    Long workflowKey = WORKFLOW_KEY_DEMO_PROCESS;

    createData(workflowKey);

    final ListViewQueryDto queryRequest = createGetAllWorkflowInstancesQuery(workflowKey);
    queryRequest.setErrorMessage("error");

    final List<ActivityStatisticsDto> activityStatisticsDtos = getActivityStatistics(queryRequest);
    assertThat(activityStatisticsDtos).hasSize(2);
    assertThat(activityStatisticsDtos).filteredOn(ai -> ai.getActivityId().equals("taskC")).allMatch(ai->
      ai.getActive().equals(0L) && ai.getCanceled().equals(0L) && ai.getCompleted().equals(0L) && ai.getIncidents().equals(2L)
    );
    assertThat(activityStatisticsDtos).filteredOn(ai -> ai.getActivityId().equals("taskE")).allMatch(ai->
      ai.getActive().equals(0L) && ai.getCanceled().equals(0L) && ai.getCompleted().equals(0L) && ai.getIncidents().equals(1L)
    );
  }

  @Test
  public void testFailStatisticsWithNoWorkflowId() throws Exception {
    final ListViewQueryDto query = createGetAllWorkflowInstancesQuery();

    MvcResult mvcResult = postRequestThatShouldFail(QUERY_WORKFLOW_STATISTICS_URL, query);

    assertThat(mvcResult.getResolvedException().getMessage()).contains("Exactly one workflow must be specified in the request");
  }

  @Test
  public void testFailStatisticsWithBpmnProcessIdButNoVersion() throws Exception {

    String bpmnProcessId = "demoProcess";

    final ListViewQueryDto queryRequest = createGetAllWorkflowInstancesQuery();
    queryRequest.setBpmnProcessId(bpmnProcessId);

    MvcResult mvcResult = postRequestThatShouldFail(QUERY_WORKFLOW_STATISTICS_URL, queryRequest);

    assertThat(mvcResult.getResolvedException().getMessage()).contains("Exactly one workflow must be specified in the request");
  }

  @Test
  public void testFailStatisticsWithMoreThanOneWorkflowKey() throws Exception {
    Long workflowKey = WORKFLOW_KEY_DEMO_PROCESS;

    createData(workflowKey);

    final ListViewQueryDto query = createGetAllWorkflowInstancesQuery(workflowKey, WORKFLOW_KEY_OTHER_PROCESS);

    MvcResult mvcResult = postRequestThatShouldFail(QUERY_WORKFLOW_STATISTICS_URL, query);

    assertThat(mvcResult.getResolvedException().getMessage()).contains("Exactly one workflow must be specified in the request");
  }


  @Test
  public void testFailStatisticsWithWorkflowKeyAndBpmnProcessId() throws Exception {
    Long workflowKey = 1L;
    String bpmnProcessId = "demoProcess";
    final ListViewQueryDto queryRequest = createGetAllWorkflowInstancesQuery(workflowKey);
    queryRequest
      .setBpmnProcessId(bpmnProcessId)
      .setWorkflowVersion(1);

    MvcResult mvcResult = postRequestThatShouldFail(QUERY_WORKFLOW_STATISTICS_URL,queryRequest);

    assertThat(mvcResult.getResolvedException().getMessage()).contains("Exactly one workflow must be specified in the request");
  }

  private ListViewQueryDto createGetAllWorkflowInstancesQuery(Long... workflowKeys) {
    ListViewQueryDto q = TestUtil.createGetAllWorkflowInstancesQuery();
    if (workflowKeys != null && workflowKeys.length > 0) {
      q.setWorkflowIds(CollectionUtil.toSafeListOfStrings(workflowKeys));
    }
    return q;
  }

  private void getStatisticsAndAssert(ListViewQueryDto query) throws Exception {
    final List<ActivityStatisticsDto> activityStatisticsDtos = getActivityStatistics(query);

    assertThat(activityStatisticsDtos).hasSize(5);
    assertThat(activityStatisticsDtos).filteredOn(ai -> ai.getActivityId().equals("taskA")).allMatch(ai->
      ai.getActive().equals(2L) && ai.getCanceled().equals(0L) && ai.getCompleted().equals(0L) && ai.getIncidents().equals(0L)
    );
    assertThat(activityStatisticsDtos).filteredOn(ai -> ai.getActivityId().equals("taskC")).allMatch(ai->
      ai.getActive().equals(0L) && ai.getCanceled().equals(2L) && ai.getCompleted().equals(0L) && ai.getIncidents().equals(2L)
    );
    assertThat(activityStatisticsDtos).filteredOn(ai -> ai.getActivityId().equals("taskD")).allMatch(ai->
      ai.getActive().equals(0L) && ai.getCanceled().equals(1L) && ai.getCompleted().equals(0L) && ai.getIncidents().equals(0L)
    );
    assertThat(activityStatisticsDtos).filteredOn(ai -> ai.getActivityId().equals("taskE")).allMatch(ai->
      ai.getActive().equals(1L) && ai.getCanceled().equals(0L) && ai.getCompleted().equals(0L) && ai.getIncidents().equals(1L)
    );
    assertThat(activityStatisticsDtos).filteredOn(ai -> ai.getActivityId().equals("end")).allMatch(ai->
      ai.getActive().equals(0L) && ai.getCanceled().equals(0L) && ai.getCompleted().equals(2L) && ai.getIncidents().equals(0L)
    );
  }

  private List<ActivityStatisticsDto> getActivityStatistics(ListViewQueryDto query) throws Exception {
    return mockMvcTestRule.listFromResponse(postRequest(QUERY_WORKFLOW_STATISTICS_URL, query), ActivityStatisticsDto.class);
  }

  @Test
  public void testTwoWorkflowsStatistics() throws Exception {
    Long workflowKey1 = WORKFLOW_KEY_DEMO_PROCESS;
    Long workflowKey2 = WORKFLOW_KEY_OTHER_PROCESS;

    createData(workflowKey1);
    createData(workflowKey2);

    getStatisticsAndAssert(createGetAllWorkflowInstancesQuery(workflowKey1));
    getStatisticsAndAssert(createGetAllWorkflowInstancesQuery(workflowKey2));
  }

  /**
   * start
   * taskA  - 2 active
   * taskB
   * taskC  -           - 2 canceled  - 2 with incident
   * taskD  -           - 1 canceled
   * taskE  - 1 active  -             - 1 with incident
   * end    -           -             -                   - 2 finished
   */
  protected void createData(Long workflowKey) {

    List<OperateEntity> entities = new ArrayList<>();

    WorkflowInstanceForListViewEntity inst = createWorkflowInstance(WorkflowInstanceState.ACTIVE, workflowKey);
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.ACTIVE, "taskA", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.ACTIVE, "taskA", null));    //duplicated on purpose, to be sure, that we count workflow instances, but not activity instances
    entities.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.ACTIVE, workflowKey);
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.ACTIVE, "taskA", null));
    entities.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.CANCELED, workflowKey);
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.TERMINATED, "taskC", null));
    entities.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.CANCELED, workflowKey);
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.TERMINATED, "taskC", null));
    entities.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.ACTIVE, workflowKey);
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    String error = "error";
    FlowNodeInstanceForListViewEntity task = createFlowNodeInstanceWithIncident(inst.getWorkflowInstanceKey(), FlowNodeState.ACTIVE, error, null);
    task.setActivityId("taskC");
    entities.add(task);
    entities.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.ACTIVE, workflowKey);
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    task = createFlowNodeInstanceWithIncident(inst.getWorkflowInstanceKey(), FlowNodeState.ACTIVE, error, null);
    task.setActivityId("taskC");
    entities.add(task);
    entities.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.CANCELED, workflowKey);
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "taskC", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.TERMINATED, "taskD", null));
    entities.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.ACTIVE, workflowKey);
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "taskC", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "taskD", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.ACTIVE, "taskE", null));
    entities.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.ACTIVE, workflowKey);
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "taskC", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "taskD", null));
    task = createFlowNodeInstanceWithIncident(inst.getWorkflowInstanceKey(), FlowNodeState.ACTIVE, error, null);
    task.setActivityId("taskE");
    entities.add(task);
    entities.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.COMPLETED, workflowKey);
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "taskC", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "taskD", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "taskE", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "end", FlowNodeType.END_EVENT));
    entities.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.COMPLETED, workflowKey);
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "taskC", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "taskD", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "taskE", null));
    entities.add(TestUtil
        .createFlowNodeInstance(inst.getWorkflowInstanceKey(), FlowNodeState.COMPLETED, "end", FlowNodeType.END_EVENT));
    entities.add(inst);

    elasticsearchTestRule.persistNew(entities.toArray(new OperateEntity[entities.size()]));

  }

  @Test
  public void testGetCoreStatistics() throws Exception {
    // when request core-statistics
    WorkflowInstanceCoreStatisticsDto coreStatistics = mockMvcTestRule.fromResponse( getRequest(QUERY_WORKFLOW_CORE_STATISTICS_URL), WorkflowInstanceCoreStatisticsDto.class);
    // then return zero statistics
    assertEquals(coreStatistics.getActive().longValue(), 0L);
    assertEquals(coreStatistics.getRunning().longValue(), 0L);
    assertEquals(coreStatistics.getWithIncidents().longValue(), 0L);

    // given test data
    createData(WORKFLOW_KEY_DEMO_PROCESS);
    createData(WORKFLOW_KEY_OTHER_PROCESS);

    // when request core-statistics
    coreStatistics = mockMvcTestRule.fromResponse(getRequest(QUERY_WORKFLOW_CORE_STATISTICS_URL), WorkflowInstanceCoreStatisticsDto.class);
    // then return non-zero statistics
    assertEquals(coreStatistics.getActive().longValue(), 6L);
    assertEquals(coreStatistics.getRunning().longValue(), 12L);
    assertEquals(coreStatistics.getWithIncidents().longValue(), 6L);
  }
}
