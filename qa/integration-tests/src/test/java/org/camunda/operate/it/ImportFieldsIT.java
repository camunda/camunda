/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.webapp.es.reader.IncidentReader;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ImportFieldsIT extends OperateZeebeIntegrationTest {
  
  @Autowired
  private IncidentReader incidentReader;
  
  @Test // OPE-818
  public void testErrorMessageSizeCanBeHigherThan32KB() {
    // having
    String errorMessageMoreThan32KB = buildStringWithLengthOf(32 * 1024 + 42);

    deployWorkflow("demoProcess_v_1.bpmn");
    final Long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, "demoProcess", "{\"a\": \"b\"}");

    //when
    //create an incident
    failTaskWithNoRetriesLeft("taskA", workflowInstanceKey, errorMessageMoreThan32KB);

    //then
    final List<IncidentEntity> allIncidents = incidentReader.getAllIncidentsByWorkflowInstanceKey(workflowInstanceKey);
    assertThat(allIncidents).hasSize(1);
    IncidentEntity incidentEntity = allIncidents.get(0);   
    assertThat(incidentEntity.getErrorMessage()).isEqualTo(errorMessageMoreThan32KB);
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
