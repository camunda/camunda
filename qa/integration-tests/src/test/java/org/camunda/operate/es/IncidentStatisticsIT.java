/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.webapp.rest.IncidentRestService.INCIDENT_URL;
import static org.camunda.operate.util.TestUtil.createIncident;
import static org.camunda.operate.util.TestUtil.createWorkflowInstanceEntity;
import static org.camunda.operate.util.TestUtil.createWorkflowVersions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.camunda.operate.entities.FlowNodeState;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.entities.listview.FlowNodeInstanceForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceState;
import org.camunda.operate.webapp.rest.dto.incidents.IncidentByWorkflowStatisticsDto;
import org.camunda.operate.webapp.rest.dto.incidents.IncidentsByErrorMsgStatisticsDto;
import org.camunda.operate.webapp.rest.dto.incidents.IncidentsByWorkflowGroupStatisticsDto;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.OperateIntegrationTest;
import org.camunda.operate.util.TestUtil;
import org.junit.Rule;
import org.junit.Test;

public class IncidentStatisticsIT extends OperateIntegrationTest {

  private static final String QUERY_INCIDENTS_BY_WORKFLOW_URL = INCIDENT_URL + "/byWorkflow";
  private static final String QUERY_INCIDENTS_BY_ERROR_URL = INCIDENT_URL + "/byError";

  public static final String LOAN_BPMN_PROCESS_ID = "loanProcess";
  public static final String LOAN_PROCESS_NAME = "Loan process";
  public static final String DEMO_BPMN_PROCESS_ID = "demoProcess";
  public static final String DEMO_PROCESS_NAME = "Demo process";
  public static final String ORDER_BPMN_PROCESS_ID = "orderProcess";
  public static final String ORDER_PROCESS_NAME = "Order process";
  public static final String NO_INSTANCES_PROCESS_ID = "noInstancesProcess";
  public static final String NO_INSTANCES_PROCESS_NAME = "No Instances Process";
  
  public static final String ERRMSG_OTHER = "Other error message";

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();
  
  @Test
  public void testAbsentWorkflowDoesntThrowExceptions() throws Exception {
    List<OperateEntity> entities = new ArrayList<>();
    
    //Create a workflowInstance that has no matching workflow 
    Long workflowKey = 0L;
    WorkflowInstanceForListViewEntity workflowInstance = createWorkflowInstanceEntity(WorkflowInstanceState.ACTIVE, workflowKey);
    entities.add(workflowInstance);
    entities.addAll(createIncidents(workflowInstance, 1, 0));
    elasticsearchTestRule.persistNew(entities.toArray(new OperateEntity[entities.size()]));

    List<IncidentsByErrorMsgStatisticsDto> response = requestIncidentsByError();

    assertThat(response).hasSize(1);
  }
 
  @Test
  public void testIncidentStatisticsByError() throws Exception {
    createData();
  
    List<IncidentsByErrorMsgStatisticsDto> response = requestIncidentsByError();
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
    assertThat(next.getActiveInstancesCount()).isEqualTo(0);
    assertThat(next.getVersion()).isEqualTo(1);
    assertThat(next.getErrorMessage()).isEqualTo(TestUtil.ERROR_MSG);
    assertThat(next.getWorkflowId()).isNotNull();

    next = iterator.next();
    assertThat(next.getName()).isEqualTo(ORDER_PROCESS_NAME + 2);
    assertThat(next.getBpmnProcessId()).isEqualTo(ORDER_BPMN_PROCESS_ID);
    assertThat(next.getInstancesWithActiveIncidentsCount()).isEqualTo(1L);
    assertThat(next.getActiveInstancesCount()).isEqualTo(0);
    assertThat(next.getVersion()).isEqualTo(2);
    assertThat(next.getErrorMessage()).isEqualTo(TestUtil.ERROR_MSG);
    assertThat(next.getWorkflowId()).isNotNull();

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
  public void testWorkflowAndIncidentStatistics() throws Exception {
    createData();
    
    List<IncidentsByWorkflowGroupStatisticsDto> workflowGroups = requestIncidentsByWorkflow();
    
    assertThat(workflowGroups).hasSize(3);
    assertDemoWorkflow(workflowGroups.get(0));
    assertOrderWorkflow(workflowGroups.get(1));
    assertLoanWorkflow(workflowGroups.get(2));
  }
  
  @Test
  public void testWorkflowWithoutInstancesIsSortedByVersionAscending() throws Exception {
    createNoInstancesWorkflowData(3);
    
    List<IncidentsByWorkflowGroupStatisticsDto> workflowGroups = requestIncidentsByWorkflow();
   
    assertThat(workflowGroups).hasSize(1);
    Collection<IncidentByWorkflowStatisticsDto> workflows = workflowGroups.get(0).getWorkflows();
    assertThat(workflows).hasSize(3);
    
    Iterator<IncidentByWorkflowStatisticsDto> workflowIterator = workflows.iterator();
    assertNoInstancesWorkflow(workflowIterator.next(),1);
    assertNoInstancesWorkflow(workflowIterator.next(),2);
    assertNoInstancesWorkflow(workflowIterator.next(),3);
  }

  private void assertNoInstancesWorkflow(IncidentByWorkflowStatisticsDto workflow,int version) {
    assertThat(workflow.getVersion()).isEqualTo(version);
    assertThat(workflow.getActiveInstancesCount()).isEqualTo(0);
    assertThat(workflow.getInstancesWithActiveIncidentsCount()).isEqualTo(0);
    assertThat(workflow.getBpmnProcessId()).isEqualTo(NO_INSTANCES_PROCESS_ID);
    assertThat(workflow.getName()).isEqualTo(NO_INSTANCES_PROCESS_NAME + version);
  }

  private void assertLoanWorkflow(IncidentsByWorkflowGroupStatisticsDto loanWorkflowGroup) {
    assertThat(loanWorkflowGroup.getBpmnProcessId()).isEqualTo(LOAN_BPMN_PROCESS_ID);
    assertThat(loanWorkflowGroup.getWorkflowName()).isEqualTo(LOAN_PROCESS_NAME + "1");
    assertThat(loanWorkflowGroup.getActiveInstancesCount()).isEqualTo(5);
    assertThat(loanWorkflowGroup.getInstancesWithActiveIncidentsCount()).isEqualTo(0);
    assertThat(loanWorkflowGroup.getWorkflows()).hasSize(1);
    
    //assert Loan process version 1
    assertThat(loanWorkflowGroup.getWorkflows()).hasSize(1);
    IncidentByWorkflowStatisticsDto loanProcessWorkflowStatistic = loanWorkflowGroup.getWorkflows().iterator().next();
    assertThat(loanProcessWorkflowStatistic.getName()).isEqualTo(LOAN_PROCESS_NAME + "1");
    assertThat(loanProcessWorkflowStatistic.getBpmnProcessId()).isEqualTo(LOAN_BPMN_PROCESS_ID);
    assertThat(loanProcessWorkflowStatistic.getVersion()).isEqualTo(1);
    assertThat(loanProcessWorkflowStatistic.getActiveInstancesCount()).isEqualTo(5);
    assertThat(loanProcessWorkflowStatistic.getInstancesWithActiveIncidentsCount()).isEqualTo(0);
  }

  private void assertOrderWorkflow(IncidentsByWorkflowGroupStatisticsDto orderWorkflowGroup) {
    //assert Order process group
    assertThat(orderWorkflowGroup.getBpmnProcessId()).isEqualTo(ORDER_BPMN_PROCESS_ID);
    assertThat(orderWorkflowGroup.getWorkflowName()).isEqualTo(ORDER_PROCESS_NAME + "2");
    assertThat(orderWorkflowGroup.getActiveInstancesCount()).isEqualTo(8);
    assertThat(orderWorkflowGroup.getInstancesWithActiveIncidentsCount()).isEqualTo(1);
    assertThat(orderWorkflowGroup.getWorkflows()).hasSize(2);
    //assert Order process version 2
    final IncidentByWorkflowStatisticsDto orderWorkflow = orderWorkflowGroup.getWorkflows().iterator().next();
    assertThat(orderWorkflow.getName()).isEqualTo(ORDER_PROCESS_NAME + "2");
    assertThat(orderWorkflow.getBpmnProcessId()).isEqualTo(ORDER_BPMN_PROCESS_ID);
    assertThat(orderWorkflow.getVersion()).isEqualTo(2);
    assertThat(orderWorkflow.getActiveInstancesCount()).isEqualTo(3);
    assertThat(orderWorkflow.getInstancesWithActiveIncidentsCount()).isEqualTo(1);
  }

  private void assertDemoWorkflow(IncidentsByWorkflowGroupStatisticsDto demoWorkflowGroup) {
    //assert Demo process group
    assertThat(demoWorkflowGroup.getBpmnProcessId()).isEqualTo(DEMO_BPMN_PROCESS_ID);
    assertThat(demoWorkflowGroup.getWorkflowName()).isEqualTo(DEMO_PROCESS_NAME + "2");
    assertThat(demoWorkflowGroup.getActiveInstancesCount()).isEqualTo(9);
    assertThat(demoWorkflowGroup.getInstancesWithActiveIncidentsCount()).isEqualTo(4);
    assertThat(demoWorkflowGroup.getWorkflows()).hasSize(2);
    //assert Demo process version 1
    final Iterator<IncidentByWorkflowStatisticsDto> workflows = demoWorkflowGroup.getWorkflows().iterator();
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
  }
  
  private void createDemoWorkflowData() {
    List<WorkflowEntity> workflowVersions = createWorkflowVersions(DEMO_BPMN_PROCESS_ID, DEMO_PROCESS_NAME, 2);
    elasticsearchTestRule.persistNew(workflowVersions.toArray(new OperateEntity[workflowVersions.size()]));

    List<OperateEntity> entities = new ArrayList<>();
    
    //Demo process v1
    Long workflowKey = workflowVersions.get(0).getKey();
    //instance #1
    WorkflowInstanceForListViewEntity workflowInstance = createWorkflowInstanceEntity(WorkflowInstanceState.ACTIVE, workflowKey);
    entities.add(workflowInstance);
    entities.addAll(createIncidents(workflowInstance, 1, 1));
    //instance #2
    workflowInstance = createWorkflowInstanceEntity(WorkflowInstanceState.ACTIVE, workflowKey);
    entities.add(workflowInstance);
    entities.addAll(createIncidents(workflowInstance, 1, 1, true));
    //instance #3
    workflowInstance = createWorkflowInstanceEntity(WorkflowInstanceState.ACTIVE, workflowKey);
    entities.add(workflowInstance);
    entities.addAll(createIncidents(workflowInstance, 1, 0));
    //entities #4,5,6
    for (int i = 4; i<=6; i++) {
      entities.add(createWorkflowInstanceEntity(WorkflowInstanceState.ACTIVE, workflowKey));
    }

    //Demo process v2
    workflowKey = workflowVersions.get(1).getKey();
    //instance #1
    workflowInstance = createWorkflowInstanceEntity(WorkflowInstanceState.ACTIVE, workflowKey);
    entities.add(workflowInstance);
    entities.addAll(createIncidents(workflowInstance, 2, 0, true));
    //entities #2-7
    for (int i = 2; i<=7; i++) {
      entities.add(createWorkflowInstanceEntity(WorkflowInstanceState.ACTIVE, workflowKey));
    }
    //entities #8-9
    for (int i = 8; i<=9; i++) {
      entities.add(createWorkflowInstanceEntity(WorkflowInstanceState.COMPLETED, workflowKey));
    }
    
    elasticsearchTestRule.persistNew(entities.toArray(new OperateEntity[entities.size()]));
  }
  
  private void createOrderWorkflowData() {
    List<WorkflowEntity> workflowVersions = createWorkflowVersions(ORDER_BPMN_PROCESS_ID, ORDER_PROCESS_NAME, 2);
    elasticsearchTestRule.persistNew(workflowVersions.toArray(new OperateEntity[workflowVersions.size()]));

    List<OperateEntity> entities = new ArrayList<>();
    //Order process v1
    Long workflowKey = workflowVersions.get(0).getKey(); 
    //entities #1-5
    for (int i = 1; i<=5; i++) {
      entities.add(createWorkflowInstanceEntity(WorkflowInstanceState.ACTIVE, workflowKey));
    }

    //Order process v2
    workflowKey = workflowVersions.get(1).getKey();
    //instance #1
    WorkflowInstanceForListViewEntity workflowInstance = createWorkflowInstanceEntity(WorkflowInstanceState.ACTIVE, workflowKey);
    entities.add(workflowInstance);
    entities.addAll(createIncidents(workflowInstance, 0, 1));
    //instance #2
    workflowInstance = createWorkflowInstanceEntity(WorkflowInstanceState.ACTIVE, workflowKey);
    entities.add(workflowInstance);
    entities.addAll(createIncidents(workflowInstance, 2, 0));
    //entities #3,4
    for (int i = 3; i<=4; i++) {
      entities.add(createWorkflowInstanceEntity(WorkflowInstanceState.ACTIVE, workflowKey));
    }
    
    elasticsearchTestRule.persistNew(entities.toArray(new OperateEntity[entities.size()]));
  }

  private void createLoanWorkflowData() {
    //Loan process v1
    List<WorkflowEntity> workflowVersions = createWorkflowVersions(LOAN_BPMN_PROCESS_ID, LOAN_PROCESS_NAME, 1);
    elasticsearchTestRule.persistNew(workflowVersions.get(0));
    
    List<OperateEntity> entities = new ArrayList<>();
    Long workflowKey = workflowVersions.get(0).getKey();
    //entities #1-3
    for (int i = 1; i<=3; i++) {
      WorkflowInstanceForListViewEntity workflowInstance = createWorkflowInstanceEntity(WorkflowInstanceState.ACTIVE, workflowKey);
      entities.add(workflowInstance);
      entities.addAll(createIncidents(workflowInstance, 0, 2));
    }
    //entities #4-5
    for (int i = 4; i<=5; i++) {
      entities.add(createWorkflowInstanceEntity(WorkflowInstanceState.ACTIVE, workflowKey));
    }

    elasticsearchTestRule.persistNew(entities.toArray(new OperateEntity[entities.size()]));
  }
  
  private void createNoInstancesWorkflowData(int versionCount) {
    createWorkflowVersions(NO_INSTANCES_PROCESS_ID, NO_INSTANCES_PROCESS_NAME, versionCount)
      .forEach( workflowVersion -> elasticsearchTestRule.persistNew(workflowVersion));
  }

  private List<IncidentsByWorkflowGroupStatisticsDto> requestIncidentsByWorkflow() throws Exception {
    return mockMvcTestRule.listFromResponse(getRequest(QUERY_INCIDENTS_BY_WORKFLOW_URL), IncidentsByWorkflowGroupStatisticsDto.class);
  }
  
  private List<IncidentsByErrorMsgStatisticsDto> requestIncidentsByError() throws Exception {
    return mockMvcTestRule.listFromResponse(getRequest(QUERY_INCIDENTS_BY_ERROR_URL), IncidentsByErrorMsgStatisticsDto.class);
  }
 
  /**
   * Demo process   v1 -                          6 running instances:  3 active incidents,   2 resolved
   * Demo process   v2 -    2 finished instances, 7 running:            2 active in 1 inst,   0 resolved
   * Order process  v1 -                          5 running instances:  no incidents
   * Order process  v2 -                          4 running instances:  2 active in 1 inst,   1 resolved
   * Loan process   v1 -                          5 running instances:  0 active,             6 resolved
   */
  private void createData() {
    createDemoWorkflowData();
    createOrderWorkflowData();
    createLoanWorkflowData();
  }

  private List<OperateEntity> createIncidents(WorkflowInstanceForListViewEntity workflowInstance, int activeIncidentsCount, int resolvedIncidentsCount) {
    return createIncidents(workflowInstance, activeIncidentsCount, resolvedIncidentsCount, false);
  }

  private List<OperateEntity> createIncidents(WorkflowInstanceForListViewEntity workflowInstance, int activeIncidentsCount, int resolvedIncidentsCount,
    boolean withOtherMsg) {
    List<OperateEntity> entities = new ArrayList<>();
    for (int i = 0; i < activeIncidentsCount; i++) {
      final FlowNodeInstanceForListViewEntity activityInstance = TestUtil
          .createFlowNodeInstance(workflowInstance.getWorkflowInstanceKey(), FlowNodeState.ACTIVE);
      createIncident(activityInstance, withOtherMsg ? ERRMSG_OTHER : null, null);
      entities.add(activityInstance);
      IncidentEntity incidentEntity = TestUtil.createIncident(IncidentState.ACTIVE,activityInstance.getActivityId(), Long.valueOf(activityInstance.getId()),activityInstance.getErrorMessage());
      incidentEntity.setWorkflowKey(workflowInstance.getWorkflowKey());
      incidentEntity.setWorkflowInstanceKey(workflowInstance.getWorkflowInstanceKey());
      entities.add(incidentEntity);
    }
    for (int i = 0; i < resolvedIncidentsCount; i++) {
      final FlowNodeInstanceForListViewEntity activityInstance = TestUtil
          .createFlowNodeInstance(workflowInstance.getWorkflowInstanceKey(), FlowNodeState.ACTIVE);
      entities.add(activityInstance);
    }
    return entities;
  }

}
