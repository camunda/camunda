/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.metric;

import static org.assertj.core.api.Assertions.fail;
import static org.camunda.operate.util.MetricAssert.assertThatMetricsFrom;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

import java.util.Collections;
import java.util.function.Predicate;

import org.camunda.operate.entities.OperationType;
import org.camunda.operate.rest.dto.listview.ListViewQueryDto;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.zeebe.operation.CancelWorkflowInstanceHandler;
import org.camunda.operate.zeebe.operation.ResolveIncidentHandler;
import org.camunda.operate.zeebe.operation.UpdateVariableHandler;
import org.camunda.operate.zeebeimport.ImportValueType;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.reflection.FieldSetter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;

public class MetricIT extends OperateZeebeIntegrationTest{
  
  @Autowired
  @Qualifier("workflowInstanceIsCompletedCheck")
  Predicate<Object[]> workflowInstanceIsCompletedCheck;
 
  @Autowired
  @Qualifier("activityIsActiveCheck")
  Predicate<Object[]> activityIsActiveCheck;
  
  @Autowired
  @Qualifier("operationsByWorkflowInstanceAreCompleted")
  Predicate<Object[]> operationsByWorkflowInstanceAreCompleted;
  

  @Autowired
  private CancelWorkflowInstanceHandler cancelWorkflowInstanceHandler;

  @Autowired
  private ResolveIncidentHandler updateRetriesHandler;

  @Autowired
  private UpdateVariableHandler updateVariableHandler;

  
  @Before
  public void setUp() {
    super.before();
    try {
      FieldSetter.setField(cancelWorkflowInstanceHandler, CancelWorkflowInstanceHandler.class.getDeclaredField("zeebeClient"), super.getClient());
      FieldSetter.setField(updateRetriesHandler, ResolveIncidentHandler.class.getDeclaredField("zeebeClient"), super.getClient());
      FieldSetter.setField(updateVariableHandler, UpdateVariableHandler.class.getDeclaredField("zeebeClient"), super.getClient());
    } catch (NoSuchFieldException e) {
      fail("Failed to inject ZeebeClient into some of the beans");
    }

    this.mockMvc = mockMvcTestRule.getMockMvc();
    
    deployWorkflow("demoProcess_v_2.bpmn");
  }

  @Test // OPE-624 
  public void testProcessedEventsDuringImport() throws Exception {
    // Given metrics are enabled
    // When
    deployWorkflow("demoProcess_v_1.bpmn");  
    Long workflowInstanceKeyLong = ZeebeTestUtil.startWorkflowInstance(getClient(), "demoProcess", "{\"a\": \"b\"}");
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstanceIsCompletedCheck, workflowInstanceKeyLong);
   
    // Then
    assertThatMetricsFrom(mockMvc,allOf(
        containsString("operate_events_processed_total{type=\"DEPLOYMENT\",}"),
        containsString("operate_events_processed_total{type=\"WORKFLOW_INSTANCE\",}"),
        containsString("operate_events_processed_total{type=\"VARIABLE\",}"),
        containsString("operate_events_processed_total{type=\"JOB\",}")
    ));
  }
  
  @Test // OPE-624 
  public void testProcessedEventsDuringImportWithIncidents() throws Exception {
    // Given metrics are enabled
    // When
    deployWorkflow("demoProcess_v_1.bpmn");
    ZeebeTestUtil.startWorkflowInstance(getClient(), "demoProcess", "{\"a\": \"b\"}");
    // And create an incident
    ZeebeTestUtil.failTask(getClient(), "taskA", getWorkerName(), 3, "Some error");
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    elasticsearchTestRule.processAllEvents(8, ImportValueType.WORKFLOW_INSTANCE);
    elasticsearchTestRule.processAllEvents(1, ImportValueType.INCIDENT);
    
    // Then
    assertThatMetricsFrom(mockMvc,allOf(
        containsString("operate_events_processed_total{type=\"DEPLOYMENT\",}"),
        containsString("operate_events_processed_total{type=\"WORKFLOW_INSTANCE\",}"),
        containsString("operate_events_processed_total{type=\"INCIDENT\",}")
    ));
  }
  
  @Test // OPE-642
  public void testOperationThatSucceeded() throws Exception {
    // Given metrics are enabled
    // When
    final Long workflowInstanceKey = startDemoWorkflowInstance();
    // we call UPDATE_VARIABLE operation on instance
    postUpdateVariableOperation(workflowInstanceKey, "a", "\"newValue\"");

    executeOneBatch();
    elasticsearchTestRule.processAllRecordsAndWait(operationsByWorkflowInstanceAreCompleted, workflowInstanceKey);
    // Then
    assertThatMetricsFrom(mockMvc,containsString("operate_commands_total{status=\"succeeded\",type=\""+OperationType.UPDATE_VARIABLE+"\",}"));
  }
  
  @Test // OPE-642
  public void testOperationThatFailed() throws Exception {
    // given
    final String bpmnProcessId = "startEndProcess";
    final BpmnModelInstance startEndProcess =
      Bpmn.createExecutableProcess(bpmnProcessId)
        .startEvent()
        .endEvent()
        .done();
    deployWorkflow(startEndProcess, "startEndProcess.bpmn");
    final Long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(super.getClient(), bpmnProcessId, null);
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstanceIsCompletedCheck, workflowInstanceKey);

    //when
    //we call CANCEL_WORKFLOW_INSTANCE operation on instance
    final ListViewQueryDto workflowInstanceQuery = createAllQuery();
    workflowInstanceQuery.setIds(Collections.singletonList(workflowInstanceKey.toString()));
    postBatchOperationWithOKResponse(workflowInstanceQuery, OperationType.CANCEL_WORKFLOW_INSTANCE);

    //and execute the operation
    executeOneBatch();
    // Then
    assertThatMetricsFrom(mockMvc,containsString("operate_commands_total{status=\"failed\",type=\""+OperationType.CANCEL_WORKFLOW_INSTANCE+"\",}"));
  }
  
}
