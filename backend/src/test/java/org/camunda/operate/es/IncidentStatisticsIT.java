/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.entities.listview.ActivityInstanceForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceState;
import org.camunda.operate.rest.dto.incidents.IncidentByWorkflowStatisticsDto;
import org.camunda.operate.rest.dto.incidents.IncidentsByErrorMsgStatisticsDto;
import org.camunda.operate.rest.dto.incidents.IncidentsByWorkflowGroupStatisticsDto;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.MockMvcTestRule;
import org.camunda.operate.util.OperateIntegrationTest;
import org.camunda.operate.util.TestUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.rest.IncidentRestService.INCIDENT_URL;
import static org.camunda.operate.util.TestUtil.createActivityInstance;
import static org.camunda.operate.util.TestUtil.createIncident;
import static org.camunda.operate.util.TestUtil.createWorkflowInstanceEntity;
import static org.camunda.operate.util.TestUtil.createWorkflowVersions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class IncidentStatisticsIT extends OperateIntegrationTest {

  private static final String QUERY_INCIDENTS_BY_WORKFLOW_URL = INCIDENT_URL + "/byWorkflow";
  private static final String QUERY_INCIDENTS_BY_ERROR_URL = INCIDENT_URL + "/byError";
  public static final String DEMO_BPMN_PROCESS_ID = "demoProcess";
  public static final String DEMO_PROCESS_NAME = "Demo process";
  public static final String ORDER_BPMN_PROCESS_ID = "orderProcess";
  public static final String ORDER_PROCESS_NAME = "Order process";
  public static final String ERRMSG_OTHER = "Other error message";

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
  public void testIncidentStatisticsByError() throws Exception {
    createData();
    MockHttpServletRequestBuilder request = get(QUERY_INCIDENTS_BY_ERROR_URL);
    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    List<IncidentsByErrorMsgStatisticsDto> response = mockMvcTestRule.listFromResponse(mvcResult, IncidentsByErrorMsgStatisticsDto.class);

    assertThat(response).hasSize(2);

    //assert NO_RETRIES_LEFT
    IncidentsByErrorMsgStatisticsDto incidentsByErrorStat = response.get(0);
    assertThat(incidentsByErrorStat.getErrorMessage()).isEqualTo(TestUtil.ERROR_MSG);
    assertThat(incidentsByErrorStat.getInstancesWithErrorCount()).isEqualTo(3L);
    assertThat(incidentsByErrorStat.getWorkflows()).hasSize(2);

    final Iterator<IncidentByWorkflowStatisticsDto> iterator = incidentsByErrorStat.getWorkflows().iterator();
    IncidentByWorkflowStatisticsDto next = iterator.next();
    assertThat(next.getName()).isEqualTo(DEMO_PROCESS_NAME + 1);
    assertThat(next.getBpmnProcessId()).isEqualTo(DEMO_BPMN_PROCESS_ID);
    assertThat(next.getInstancesWithActiveIncidentsCount()).isEqualTo(2L);
    assertThat(next.getActiveInstancesCount()).isNull();
    assertThat(next.getVersion()).isEqualTo(1);
    assertThat(next.getErrorMessage()).isEqualTo(TestUtil.ERROR_MSG);
    assertThat(next.getWorkflowId()).isNotNull().isNotEmpty();

    next = iterator.next();
    assertThat(next.getName()).isEqualTo(ORDER_PROCESS_NAME + 2);
    assertThat(next.getBpmnProcessId()).isEqualTo(ORDER_BPMN_PROCESS_ID);
    assertThat(next.getInstancesWithActiveIncidentsCount()).isEqualTo(1L);
    assertThat(next.getActiveInstancesCount()).isNull();
    assertThat(next.getVersion()).isEqualTo(2);
    assertThat(next.getErrorMessage()).isEqualTo(TestUtil.ERROR_MSG);
    assertThat(next.getWorkflowId()).isNotNull().isNotEmpty();

    //assert OTHER_ERRMSG
    incidentsByErrorStat = response.get(1);
    assertThat(incidentsByErrorStat.getErrorMessage()).isEqualTo(ERRMSG_OTHER);
    assertThat(incidentsByErrorStat.getInstancesWithErrorCount()).isEqualTo(2L);
    assertThat(incidentsByErrorStat.getWorkflows()).hasSize(2);
    assertThat(incidentsByErrorStat.getWorkflows()).allMatch(
      s ->
        s.getWorkflowId() != null &&
          s.getName().equals(DEMO_PROCESS_NAME + s.getVersion()) &&
          s.getErrorMessage().equals(ERRMSG_OTHER) &&
          s.getInstancesWithActiveIncidentsCount() == 1L &&
          (s.getVersion() == 1 || s.getVersion() == 2)
    );


  }

  @Test
  public void testIncidentStatisticsByWorkflow() throws Exception {
    createData();
    MockHttpServletRequestBuilder request = get(QUERY_INCIDENTS_BY_WORKFLOW_URL);
    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    List<IncidentsByWorkflowGroupStatisticsDto> response = mockMvcTestRule.listFromResponse(mvcResult, IncidentsByWorkflowGroupStatisticsDto.class);

    //assert data
    assertThat(response).hasSize(2);
    //assert Demo process group
    assertThat(response.get(0).getBpmnProcessId()).isEqualTo(DEMO_BPMN_PROCESS_ID);
    assertThat(response.get(0).getWorkflowName()).isEqualTo(DEMO_PROCESS_NAME + "2");
    assertThat(response.get(0).getActiveInstancesCount()).isEqualTo(9);
    assertThat(response.get(0).getInstancesWithActiveIncidentsCount()).isEqualTo(4);
    assertThat(response.get(0).getWorkflows()).hasSize(2);
    //assert Demo process version 1
    final Iterator<IncidentByWorkflowStatisticsDto> workflows = response.get(0).getWorkflows().iterator();
    final IncidentByWorkflowStatisticsDto workflow1 = workflows.next();
    assertThat(workflow1.getName()).isEqualTo(DEMO_PROCESS_NAME + "1");
    assertThat(workflow1.getBpmnProcessId()).isEqualTo(DEMO_BPMN_PROCESS_ID);
    assertThat(workflow1.getVersion()).isEqualTo(1);
    assertThat(workflow1.getActiveInstancesCount()).isEqualTo(3);
    assertThat(workflow1.getInstancesWithActiveIncidentsCount()).isEqualTo(3);
    //assert Demo process version 2
    final IncidentByWorkflowStatisticsDto workflow2 = workflows.next();
    assertThat(workflow2.getName()).isEqualTo(DEMO_PROCESS_NAME + "2");
    assertThat(workflow2.getBpmnProcessId()).isEqualTo(DEMO_BPMN_PROCESS_ID);
    assertThat(workflow2.getVersion()).isEqualTo(2);
    assertThat(workflow2.getActiveInstancesCount()).isEqualTo(6);
    assertThat(workflow2.getInstancesWithActiveIncidentsCount()).isEqualTo(1);

    //assert Order process group
    assertThat(response.get(1).getBpmnProcessId()).isEqualTo(ORDER_BPMN_PROCESS_ID);
    assertThat(response.get(1).getWorkflowName()).isEqualTo(ORDER_PROCESS_NAME + "2");
    assertThat(response.get(1).getActiveInstancesCount()).isEqualTo(8);
    assertThat(response.get(1).getInstancesWithActiveIncidentsCount()).isEqualTo(1);
    assertThat(response.get(1).getWorkflows()).hasSize(1);
    //assert Order process version 2
    final IncidentByWorkflowStatisticsDto workflow3 = response.get(1).getWorkflows().iterator().next();
    assertThat(workflow3.getName()).isEqualTo(ORDER_PROCESS_NAME + "2");
    assertThat(workflow3.getBpmnProcessId()).isEqualTo(ORDER_BPMN_PROCESS_ID);
    assertThat(workflow3.getVersion()).isEqualTo(2);
    assertThat(workflow3.getActiveInstancesCount()).isEqualTo(3);
    assertThat(workflow3.getInstancesWithActiveIncidentsCount()).isEqualTo(1);
  }

  /**
   * Demo process   v1 -                          6 running instances:  3 active incidents,   2 resolved
   * Demo process   v2 -    2 finished instances, 7 running:            2 active in 1 inst,   0 resolved
   * Order process  v1 -                          5 running instances:  no incidents
   * Order process  v2 -                          4 running instances:  2 active in 1 inst,   1 resolved
   * Loan process   v1 -                          5 running instances:  0 active,             6 resolved
   */
  private void createData() {
    List<WorkflowEntity> workflowVersions = createWorkflowVersions(DEMO_BPMN_PROCESS_ID, DEMO_PROCESS_NAME, 2);
    elasticsearchTestRule.persistNew(workflowVersions.toArray(new OperateEntity[workflowVersions.size()]));

    List<OperateEntity> entities = new ArrayList<>();

    //Demo process v1
    String workflowId = workflowVersions.get(0).getId();
    //instance #1
    WorkflowInstanceForListViewEntity workflowInstance = createWorkflowInstanceEntity(WorkflowInstanceState.ACTIVE, workflowId);
    entities.add(workflowInstance);
    entities.addAll(createIncidents(workflowInstance, 1, 1));
    //instance #2
    workflowInstance = createWorkflowInstanceEntity(WorkflowInstanceState.ACTIVE, workflowId);
    entities.add(workflowInstance);
    entities.addAll(createIncidents(workflowInstance, 1, 1, true));
    //instance #3
    workflowInstance = createWorkflowInstanceEntity(WorkflowInstanceState.ACTIVE, workflowId);
    entities.add(workflowInstance);
    entities.addAll(createIncidents(workflowInstance, 1, 0));
    //entities #4,5,6
    for (int i = 4; i<=6; i++) {
      entities.add(createWorkflowInstanceEntity(WorkflowInstanceState.ACTIVE, workflowId));
    }

    //Demo process v2
    workflowId = workflowVersions.get(1).getId();
    //instance #1
    workflowInstance = createWorkflowInstanceEntity(WorkflowInstanceState.ACTIVE, workflowId);
    entities.add(workflowInstance);
    entities.addAll(createIncidents(workflowInstance, 2, 0, true));
    //entities #2-7
    for (int i = 2; i<=7; i++) {
      entities.add(createWorkflowInstanceEntity(WorkflowInstanceState.ACTIVE, workflowId));
    }
    //entities #8-9
    for (int i = 8; i<=9; i++) {
      entities.add(createWorkflowInstanceEntity(WorkflowInstanceState.COMPLETED, workflowId));
    }

    workflowVersions = createWorkflowVersions(ORDER_BPMN_PROCESS_ID, ORDER_PROCESS_NAME, 2);
    elasticsearchTestRule.persistNew(workflowVersions.toArray(new OperateEntity[workflowVersions.size()]));

    //Order process v1
    workflowId = workflowVersions.get(0).getId();
    //entities #1-5
    for (int i = 1; i<=5; i++) {
      entities.add(createWorkflowInstanceEntity(WorkflowInstanceState.ACTIVE, workflowId));
    }

    //Order process v2
    workflowId = workflowVersions.get(1).getId();
    //instance #1
    workflowInstance = createWorkflowInstanceEntity(WorkflowInstanceState.ACTIVE, workflowId);
    entities.add(workflowInstance);
    entities.addAll(createIncidents(workflowInstance, 0, 1));
    //instance #2
    workflowInstance = createWorkflowInstanceEntity(WorkflowInstanceState.ACTIVE, workflowId);
    entities.add(workflowInstance);
    entities.addAll(createIncidents(workflowInstance, 2, 0));
    //entities #3,4
    for (int i = 3; i<=4; i++) {
      entities.add(createWorkflowInstanceEntity(WorkflowInstanceState.ACTIVE, workflowId));
    }

    //Loan process v1
    workflowVersions = createWorkflowVersions("loanProcess", "Loan process", 1);
    elasticsearchTestRule.persistNew(workflowVersions.get(0));
    workflowId = workflowVersions.get(0).getId();
    //entities #1-3
    for (int i = 1; i<=3; i++) {
      workflowInstance = createWorkflowInstanceEntity(WorkflowInstanceState.ACTIVE, workflowId);
      entities.add(workflowInstance);
      entities.addAll(createIncidents(workflowInstance, 0, 2));
    }
    //entities #4-5
    for (int i = 4; i<=5; i++) {
      entities.add(createWorkflowInstanceEntity(WorkflowInstanceState.ACTIVE, workflowId));
    }

    elasticsearchTestRule.persistNew(entities.toArray(new OperateEntity[entities.size()]));
  }

  private List<OperateEntity> createIncidents(WorkflowInstanceForListViewEntity workflowInstance, int activeIncidentsCount, int resolvedIncidentsCount) {
    return createIncidents(workflowInstance, activeIncidentsCount, resolvedIncidentsCount, false);
  }

  private List<OperateEntity> createIncidents(WorkflowInstanceForListViewEntity workflowInstance, int activeIncidentsCount, int resolvedIncidentsCount,
    boolean withOtherMsg) {
    List<OperateEntity> entities = new ArrayList<>();
    for (int i = 0; i < activeIncidentsCount; i++) {
      final ActivityInstanceForListViewEntity activityInstance = createActivityInstance(workflowInstance.getId(), ActivityState.ACTIVE);
      createIncident(activityInstance, withOtherMsg ? ERRMSG_OTHER : null, null);
      entities.add(activityInstance);
    }
    for (int i = 0; i < resolvedIncidentsCount; i++) {
      final ActivityInstanceForListViewEntity activityInstance = createActivityInstance(workflowInstance.getId(), ActivityState.ACTIVE);
      entities.add(activityInstance);
    }
    return entities;
  }

}
