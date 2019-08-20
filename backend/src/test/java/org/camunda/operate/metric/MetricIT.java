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

import org.camunda.operate.entities.OperationState;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.it.OperateTester;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.zeebe.operation.CancelWorkflowInstanceHandler;
import org.camunda.operate.zeebe.operation.ResolveIncidentHandler;
import org.camunda.operate.zeebe.operation.UpdateVariableHandler;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.reflection.FieldSetter;
import org.springframework.beans.factory.annotation.Autowired;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;

public class MetricIT extends OperateZeebeIntegrationTest{
  
  @Autowired
  private CancelWorkflowInstanceHandler cancelWorkflowInstanceHandler;

  @Autowired
  private ResolveIncidentHandler updateRetriesHandler;

  @Autowired
  private UpdateVariableHandler updateVariableHandler;

  @Autowired
  private OperateTester operateTester;
  
  @Before
  public void before() {
    super.before();
    injectZeebeClientIntoOperationHandler();
    operateTester.setZeebeClient(getClient()).setMockMvcTestRule(mockMvcTestRule);
  }

  private void injectZeebeClientIntoOperationHandler() {
    try {
      FieldSetter.setField(cancelWorkflowInstanceHandler, CancelWorkflowInstanceHandler.class.getDeclaredField("zeebeClient"), zeebeClient);
      FieldSetter.setField(updateRetriesHandler, ResolveIncidentHandler.class.getDeclaredField("zeebeClient"), zeebeClient);
      FieldSetter.setField(updateVariableHandler, UpdateVariableHandler.class.getDeclaredField("zeebeClient"), zeebeClient);
    } catch (NoSuchFieldException e) {
      fail("Failed to inject ZeebeClient into some of the beans");
    }
  }

  @Test // OPE-624 
  public void testProcessedEventsDuringImport() throws Exception {
    // Given metrics are enabled
    // When
    operateTester
      .deployWorkflow("demoProcess_v_1.bpmn").waitUntil().workflowIsDeployed()
      .startWorkflowInstance("demoProcess","{\"a\": \"b\"}").waitUntil().workflowInstanceIsFinished();
    // Then
    assertThatMetricsFrom(mockMvc,allOf(
        containsString("operate_events_processed_total{status=\"succeeded\",type=\"DEPLOYMENT\",}"),
        containsString("operate_events_processed_total{status=\"succeeded\",type=\"WORKFLOW_INSTANCE\",}"),
        containsString("operate_events_processed_total{status=\"succeeded\",type=\"VARIABLE\",}"),
        containsString("operate_events_processed_total{status=\"succeeded\",type=\"JOB\",}")
    ));
  }
  
  @Test // OPE-624 
  public void testProcessedEventsDuringImportWithIncidents() throws Exception {
    // Given metrics are enabled
    // When
    operateTester
      .deployWorkflow("demoProcess_v_1.bpmn").waitUntil().workflowIsDeployed()
      .startWorkflowInstance("demoProcess","{\"a\": \"b\"}")
      .and()
      .failTask("taskA","Some error").waitUntil().incidentIsActive();
    // Then
    assertThatMetricsFrom(mockMvc,allOf(
        containsString("operate_events_processed_total{status=\"succeeded\",type=\"DEPLOYMENT\",}"),
        containsString("operate_events_processed_total{status=\"succeeded\",type=\"WORKFLOW_INSTANCE\",}"),
        containsString("operate_events_processed_total{status=\"succeeded\",type=\"INCIDENT\",}")
    ));
  }
  
  @Test // OPE-642
  public void testOperationThatSucceeded() throws Exception {
    // Given metrics are enabled
    // When
    operateTester
      .deployWorkflow("demoProcess_v_2.bpmn").waitUntil().workflowIsDeployed()
      .and()
      .startWorkflowInstance("demoProcess").waitUntil().workflowInstanceIsStarted()
      .and()
      .updateVariableOperation("a","\"newValue\"").waitUntil().operationIsCompleted();
    // Then
    assertThatMetricsFrom(mockMvc,
        containsString("operate_commands_total{status=\""+OperationState.SENT+"\",type=\""+OperationType.UPDATE_VARIABLE+"\",}"));
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
    
    operateTester
      .deployWorkflow(startEndProcess, "startEndProcess.bpmn")
      .and()
      .startWorkflowInstance(bpmnProcessId).waitUntil().workflowInstanceIsCompleted()
      .and()
      .cancelWorkflowInstanceOperation().waitUntil().operationIsCompleted();
    // Then
    assertThatMetricsFrom(mockMvc,
        containsString("operate_commands_total{status=\""+OperationState.FAILED+"\",type=\""+OperationType.CANCEL_WORKFLOW_INSTANCE+"\",}"));
  }
  
}
