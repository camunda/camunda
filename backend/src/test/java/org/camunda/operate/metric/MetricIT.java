/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.metric;

import static org.camunda.operate.util.MetricAssert.assertThatMetricsFrom;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

import java.util.function.Predicate;

import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.zeebeimport.ImportValueType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class MetricIT extends OperateZeebeIntegrationTest{
  
  @Autowired
  @Qualifier("workflowInstanceIsCompletedCheck")
  Predicate<Object[]> workflowInstanceIsCompletedCheck;
 
  @Before
  public void init() {
    super.before();
    mockMvc = mockMvcTestRule.getMockMvc();
  }

  @After
  public void after() {
    super.after();
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
}
