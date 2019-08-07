/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import org.camunda.operate.es.reader.IncidentReader;
import org.camunda.operate.es.reader.ListViewReader;
import org.camunda.operate.rest.dto.listview.ListViewRequestDto;
import org.camunda.operate.rest.dto.listview.ListViewResponseDto;
import org.camunda.operate.rest.dto.listview.ListViewWorkflowInstanceDto;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.zeebe.operation.CancelWorkflowInstanceHandler;
import org.camunda.operate.zeebe.operation.ResolveIncidentHandler;
import org.camunda.operate.zeebe.operation.UpdateVariableHandler;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.reflection.FieldSetter;
import org.springframework.beans.factory.annotation.Autowired;

public class ErrorMessagesIT extends OperateZeebeIntegrationTest{

  @Autowired
  private CancelWorkflowInstanceHandler cancelWorkflowInstanceHandler;

  @Autowired
  private ResolveIncidentHandler updateRetriesHandler;

  @Autowired
  private UpdateVariableHandler updateVariableHandler;
  
  @Autowired
  private IncidentReader incidentReader;
  
  @Autowired
  private ListViewReader listViewReader;

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

  @Test
  public void testErrorMessageIsTrimmedBeforeSave() throws Exception {
    // Given
    String errorMessageWithWhitespaces ="   Error message with white spaces   ";
    String errorMessageWithoutWhiteSpaces = "Error message with white spaces";
  
    // when
    Long workflowInstanceKey = operateTester
    .deployWorkflow("demoProcess_v_1.bpmn").waitUntil().workflowIsDeployed()
    .startWorkflowInstance("demoProcess","{\"a\": \"b\"}").failTask("taskA", errorMessageWithWhitespaces)
    .waitUntil().workflowInstanceIsFinished()
    .updateVariableOperation("a", "b").waitUntil().operationIsCompleted()
    .getWorkflowInstanceKey();
    
    // then
    assertThat(incidentReader.getAllIncidentsByWorkflowInstanceKey(workflowInstanceKey).get(0).getErrorMessage()).isEqualTo(errorMessageWithoutWhiteSpaces);
    ListViewResponseDto response = listViewReader.queryWorkflowInstances(new ListViewRequestDto(), 0, 5);
    ListViewWorkflowInstanceDto workflowInstances  = response.getWorkflowInstances().get(0);
    assertThat(workflowInstances).isNotNull();
    assertThat(workflowInstances.getOperations().get(0).getErrorMessage()).doesNotStartWith(" ").doesNotEndWith(" ");
  }
 
}
