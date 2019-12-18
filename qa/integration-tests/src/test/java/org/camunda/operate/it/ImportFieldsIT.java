/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.it;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.junit.Test;

public class ImportFieldsIT extends OperateZeebeIntegrationTest {
  
  @Test // OPE-818
  public void testErrorMessageSizeCanBeHigherThan32KB() {
    // having
    String errorMessageMoreThan32KB = buildStringWithLengthOf(32 * 1024 + 42);
    
    // when
    tester
      .deployWorkflow("demoProcess_v_1.bpmn")
      .and()
      .startWorkflowInstance("demoProcess", "{\"a\": \"b\"}")
      .waitUntil().workflowInstanceIsStarted()
      .and()
      .failTask("taskA", errorMessageMoreThan32KB)
      .waitUntil().incidentIsActive();
    
    // then
    assertThat(tester.hasIncidentWithErrorMessage(errorMessageMoreThan32KB)).isTrue();   
  }
  
  @Test
  public void testVariableValuesSizeCanBeUpTo32KB() {
    // having
    String varValue = buildStringWithLengthOf(32 * 1024 - 4); // 2 quotes
    
    // when
    tester
     .deployWorkflow("demoProcess_v_1.bpmn")
     .and()
     .startWorkflowInstance("demoProcess",  "{\"a\": \""+varValue+"\"}")
     .waitUntil().workflowInstanceIsStarted()
     .and()
     .waitUntil().variableExists("a");
  
    // then
    assertThat(tester.hasVariable("a","\""+varValue+"\"")).isTrue();
  }

  protected String buildStringWithLengthOf(int length) {
    StringBuilder result = new StringBuilder();
    char fillChar = 'a';
    for(int i=0;i < length;i++) {
      result.append(fillChar);
    }
    return result.toString();
  }

}
