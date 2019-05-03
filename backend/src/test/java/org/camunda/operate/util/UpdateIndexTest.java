/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.operate.TestApplication;
import org.camunda.operate.es.reader.IncidentReader;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.es.reader.WorkflowReader;
import org.camunda.operate.es.writer.IncidentWriter;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.property.OperateProperties;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes =    {TestApplication.class},
    properties = {
        OperateProperties.PREFIX + ".startLoadingDataOnStartup = false",
        OperateProperties.PREFIX + ".elasticsearch.rolloverEnabled = false"
    }
)
public class UpdateIndexTest {
  
  @Autowired
  IncidentWriter incidentWriter;
  
  @Autowired
  IncidentReader incidentReader;
  
  @Autowired
  WorkflowReader workflowReader;
  
  @Autowired
  WorkflowInstanceReader workflowInstanceReader;
  
  @Ignore @Test
  public void testUpdateIncidentsWithWorkflowId() throws PersistenceException{
    long incidents = incidentReader.getIncidentsCount();
    long updated = 0;
    for(String workflowId: workflowReader.getAllWorkflowIds()) {
      updated +=  incidentWriter.updateWorkflowIds(workflowId,workflowInstanceReader.getWorkflowInstanceIdsByWorkflowId(workflowId));
    };
    assertThat(updated).isEqualTo(incidents);
  }

}
