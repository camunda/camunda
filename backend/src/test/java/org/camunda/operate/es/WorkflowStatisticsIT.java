/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.util.TestUtil.createActivityInstance;
import static org.camunda.operate.util.TestUtil.createActivityInstanceWithIncident;
import static org.camunda.operate.util.TestUtil.createWorkflowInstance;
import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.ActivityType;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.entities.listview.ActivityInstanceForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceState;
import org.camunda.operate.rest.dto.ActivityStatisticsDto;
import org.camunda.operate.rest.dto.WorkflowInstanceCoreStatisticsDto;
import org.camunda.operate.rest.dto.listview.ListViewQueryDto;
import org.camunda.operate.rest.dto.listview.ListViewRequestDto;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.MockMvcTestRule;
import org.camunda.operate.util.OperateIntegrationTest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Tests Elasticsearch query for workflow statistics.
 */
public class WorkflowStatisticsIT extends OperateIntegrationTest {

  private static final String QUERY_WORKFLOW_STATISTICS_URL = "/api/workflow-instances/statistics";
  private static final String QUERY_WORKFLOW_CORE_STATISTICS_URL = "/api/workflow-instances/core-statistics";

  private Random random = new Random();

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Rule
  public MockMvcTestRule mockMvcTestRule = new MockMvcTestRule();

  private MockMvc mockMvc;

  @Before
  public void starting() {
    this.mockMvc = mockMvcTestRule.getMockMvc();
  }

  @Test
  public void testOneWorkflowStatistics() throws Exception {
    String workflowId = "demoProcess";

    createData(workflowId);

    getStatisticsAndAssert(createGetAllWorkflowInstancesQuery(workflowId));
  }

  @Test
  public void testStatisticsWithQuery() throws Exception {
    String workflowId = "demoProcess";

    createData(workflowId);

    final ListViewRequestDto query = createGetAllWorkflowInstancesQuery(workflowId);
    query.getQueries().get(0).setActivityId("taskA");

    final List<ActivityStatisticsDto> activityStatisticsDtos = getActivityStatistics(query);
    assertThat(activityStatisticsDtos).hasSize(1);
    assertThat(activityStatisticsDtos).filteredOn(ai -> ai.getActivityId().equals("taskA")).allMatch(ai->
      ai.getActive().equals(2L) && ai.getCanceled().equals(0L) && ai.getCompleted().equals(0L) && ai.getIncidents().equals(0L)
    );
  }

  @Test
  public void testFailStatisticsWithNoWorkflowId() throws Exception {
    final ListViewRequestDto query = createGetAllWorkflowInstancesQuery(null);

    MockHttpServletRequestBuilder request = post(QUERY_WORKFLOW_STATISTICS_URL)
      .content(mockMvcTestRule.json(query))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isBadRequest())
      .andReturn();

    assertThat(mvcResult.getResolvedException().getMessage()).contains("Exactly one workflow must be specified in the request");
  }

  @Test
  public void testFailStatisticsWithBpmnProcessIdButNoVersion() throws Exception {

    String bpmnProcessId = "demoProcess";

    final ListViewRequestDto query = createGetAllWorkflowInstancesQuery(null);
    query.getQueries().get(0).setBpmnProcessId(bpmnProcessId);

    MockHttpServletRequestBuilder request = post(QUERY_WORKFLOW_STATISTICS_URL)
      .content(mockMvcTestRule.json(query))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isBadRequest())
      .andReturn();

    assertThat(mvcResult.getResolvedException().getMessage()).contains("Exactly one workflow must be specified in the request");
  }

  @Test
  public void testFailStatisticsWithMoreThanOneWorkflowId() throws Exception {
    String workflowId = "demoProcess";

    createData(workflowId);

    final ListViewRequestDto query = createGetAllWorkflowInstancesQuery(workflowId);
    query.getQueries().get(0).setWorkflowIds(Arrays.asList(workflowId, "otherWorkflowId"));

    MockHttpServletRequestBuilder request = post(QUERY_WORKFLOW_STATISTICS_URL)
      .content(mockMvcTestRule.json(query))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isBadRequest())
      .andReturn();

    assertThat(mvcResult.getResolvedException().getMessage()).contains("Exactly one workflow must be specified in the request");
  }


  @Test
  public void testFailStatisticsWithWorkflowIdAndBpmnProcessId() throws Exception {
    String workflowId = "1";
    String bpmnProcessId = "demoProcess";
    Integer version = 1;
    final ListViewRequestDto query = createGetAllWorkflowInstancesQuery(workflowId);
    query.getQueries().get(0).setBpmnProcessId(bpmnProcessId);
    query.getQueries().get(0).setWorkflowVersion(1);

    MockHttpServletRequestBuilder request = post(QUERY_WORKFLOW_STATISTICS_URL)
      .content(mockMvcTestRule.json(query))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isBadRequest())
      .andReturn();

    assertThat(mvcResult.getResolvedException().getMessage()).contains("Exactly one workflow must be specified in the request");
  }

  @Test
  public void testFailStatisticsWithNoQuery() throws Exception {
    String workflowId = "demoProcess";

    createData(workflowId);

    final ListViewRequestDto query = new ListViewRequestDto();

    MockHttpServletRequestBuilder request = post(QUERY_WORKFLOW_STATISTICS_URL)
      .content(mockMvcTestRule.json(query))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isBadRequest())
      .andReturn();

    assertThat(mvcResult.getResolvedException().getMessage()).isEqualTo("Exactly one query must be specified in the request.");
  }

  @Test
  public void testFailStatisticsWithMoreThanOneQuery() throws Exception {
    String workflowId = "demoProcess";

    createData(workflowId);

    final ListViewRequestDto query = createGetAllWorkflowInstancesQuery(workflowId);
    query.getQueries().add(new ListViewQueryDto());


    MockHttpServletRequestBuilder request = post(QUERY_WORKFLOW_STATISTICS_URL)
      .content(mockMvcTestRule.json(query))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isBadRequest())
      .andReturn();

    assertThat(mvcResult.getResolvedException().getMessage()).isEqualTo("Exactly one query must be specified in the request.");
  }

  private ListViewRequestDto createGetAllWorkflowInstancesQuery(String workflowId) {
    ListViewQueryDto q = new ListViewQueryDto();
    q.setRunning(true);
    q.setActive(true);
    q.setIncidents(true);
    q.setFinished(true);
    q.setCompleted(true);
    q.setCanceled(true);
    if (workflowId != null) {
      q.setWorkflowIds(Arrays.asList(workflowId));
    }
    ListViewRequestDto request = new ListViewRequestDto();
    request.getQueries().add(q);
    return request;
  }

  private void getStatisticsAndAssert(ListViewRequestDto query) throws Exception {
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

  private List<ActivityStatisticsDto> getActivityStatistics(ListViewRequestDto query) throws Exception {
    MockHttpServletRequestBuilder request = post(QUERY_WORKFLOW_STATISTICS_URL)
      .content(mockMvcTestRule.json(query))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    return mockMvcTestRule.listFromResponse(mvcResult, ActivityStatisticsDto.class);
  }

  @Test
  public void testTwoWorkflowsStatistics() throws Exception {
    String workflowId1 = "demoProcess";
    String workflowId2 = "sampleProcess";

    createData(workflowId1);
    createData(workflowId2);

    getStatisticsAndAssert(createGetAllWorkflowInstancesQuery(workflowId1));
    getStatisticsAndAssert(createGetAllWorkflowInstancesQuery(workflowId2));
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
  private void createData(String workflowId) {

    List<OperateEntity> entities = new ArrayList<>();

    WorkflowInstanceForListViewEntity inst = createWorkflowInstance(WorkflowInstanceState.ACTIVE, workflowId);
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "start", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.ACTIVE, "taskA", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.ACTIVE, "taskA", null));    //duplicated on purpose, to be sure, that we count workflow instances, but not activity instances
    entities.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.ACTIVE, workflowId);
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "start", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.ACTIVE, "taskA", null));
    entities.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.CANCELED, workflowId);
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "start", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "taskA", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "taskB", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.TERMINATED, "taskC", null));
    entities.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.CANCELED, workflowId);
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "start", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "taskA", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "taskB", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.TERMINATED, "taskC", null));
    entities.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.ACTIVE, workflowId);
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "start", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "taskA", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "taskB", null));
    ActivityInstanceForListViewEntity task = createActivityInstanceWithIncident(inst.getId(), ActivityState.ACTIVE, "error", null);
    task.setActivityId("taskC");
    entities.add(task);
    entities.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.ACTIVE, workflowId);
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "start", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "taskA", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "taskB", null));
    task = createActivityInstanceWithIncident(inst.getId(), ActivityState.ACTIVE, "error", null);
    task.setActivityId("taskC");
    entities.add(task);
    entities.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.CANCELED, workflowId);
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "start", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "taskA", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "taskB", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "taskC", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.TERMINATED, "taskD", null));
    entities.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.ACTIVE, workflowId);
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "start", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "taskA", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "taskB", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "taskC", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "taskD", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.ACTIVE, "taskE", null));
    entities.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.ACTIVE, workflowId);
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "start", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "taskA", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "taskB", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "taskC", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "taskD", null));
    task = createActivityInstanceWithIncident(inst.getId(), ActivityState.ACTIVE, "error", null);
    task.setActivityId("taskE");
    entities.add(task);
    entities.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.COMPLETED, workflowId);
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "start", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "taskA", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "taskB", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "taskC", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "taskD", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "taskE", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "end", ActivityType.END_EVENT));
    entities.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.COMPLETED, workflowId);
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "start", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "taskA", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "taskB", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "taskC", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "taskD", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "taskE", null));
    entities.add(createActivityInstance(inst.getId(), ActivityState.COMPLETED, "end", ActivityType.END_EVENT));
    entities.add(inst);

    elasticsearchTestRule.persistNew(entities.toArray(new OperateEntity[entities.size()]));

  }

  @Test
  public void testGetCoreStatistics() throws Exception {
    // given no data
    MockHttpServletRequestBuilder request = MockMvcRequestBuilders.get(QUERY_WORKFLOW_CORE_STATISTICS_URL);
    // when request core-statistics
    MvcResult mvcResult = mockMvc.perform(request).andExpect(status().isOk()).andExpect(content().contentType(mockMvcTestRule.getContentType())).andReturn();

    WorkflowInstanceCoreStatisticsDto coreStatistics = mockMvcTestRule.fromResponse(mvcResult, WorkflowInstanceCoreStatisticsDto.class);
    // then return zero statistics
    assertEquals(coreStatistics.getActive().longValue(), 0L);
    assertEquals(coreStatistics.getRunning().longValue(), 0L);
    assertEquals(coreStatistics.getWithIncidents().longValue(), 0L);
    
    // given test data
    createData("demoProcess");
    createData("sampleProcess");
    
    // when request core-statistics
    request = MockMvcRequestBuilders.get(QUERY_WORKFLOW_CORE_STATISTICS_URL);
    mvcResult = mockMvc.perform(request).andExpect(status().isOk()).andExpect(content().contentType(mockMvcTestRule.getContentType())).andReturn();

    coreStatistics = mockMvcTestRule.fromResponse(mvcResult, WorkflowInstanceCoreStatisticsDto.class);
    // then return non-zero statistics
    assertEquals(coreStatistics.getActive().longValue(), 6L);
    assertEquals(coreStatistics.getRunning().longValue(), 12L);
    assertEquals(coreStatistics.getWithIncidents().longValue(), 6L);
  }
}
