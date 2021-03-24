/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;

import org.camunda.operate.entities.FlowNodeInstanceEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.webapp.es.reader.WorkflowInstanceReader;
import org.camunda.operate.webapp.rest.dto.listview.ListViewWorkflowInstanceDto;
import org.camunda.operate.webapp.rest.exception.NotFoundException;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class WorkflowInstanceReaderIT extends OperateZeebeIntegrationTest {

  @Autowired
  private WorkflowInstanceReader workflowInstanceReader;

  private Long workflowInstanceKey;

  private Random random = new Random();

  @Before
  public void setUp() {
    // Given
    workflowInstanceKey = tester.deployWorkflow("demoProcess_v_1.bpmn").waitUntil().workflowIsDeployed()
        .startWorkflowInstance("demoProcess", "{\"a\": \"b\"}")
        .and().failTask("taskA", "Some error").waitUntil().incidentIsActive()
        .getWorkflowInstanceKey();
  }

  // Case: Use workflow instance key ( default case )
  @Test
  public void testGetWorkflowInstanceWithOperationsByKeyWithCorrectKey() {
    // When
    ListViewWorkflowInstanceDto workflowInstance = workflowInstanceReader.getWorkflowInstanceWithOperationsByKey(workflowInstanceKey);
    assertThat(workflowInstance.getId()).isEqualTo(workflowInstanceKey.toString());
  }

  @Test
  public void testGetWorkflowInstanceWithCorrectKey() {
    // When
    WorkflowInstanceForListViewEntity workflowInstance = workflowInstanceReader.getWorkflowInstanceByKey(workflowInstanceKey);
    assertThat(workflowInstance.getId()).isEqualTo(workflowInstanceKey.toString());
  }

  // OPE-667
  // Case: Use (accidently) activity key for workflow instance key
  @Test(expected = NotFoundException.class)
  public void testGetWorkflowInstanceWithOperationsByKeyWithActivityKey() {
    // get a random activity id
    List<FlowNodeInstanceEntity> activities = tester.getAllFlowNodeInstances(workflowInstanceKey);
    Long activityId = activities.get(random.nextInt(activities.size())).getKey();
    // When
    workflowInstanceReader.getWorkflowInstanceWithOperationsByKey(activityId);
    // then throw NotFoundException
  }

  // Case: Use random key for workflow instance key
  @Test(expected = NotFoundException.class)
  public void testGetWorkflowInstanceWithOperationsByKeyWithRandomKey() {
    // get a random
    Long key = random.nextLong();
    while (key.equals(workflowInstanceKey)) {
      key = random.nextLong();
    }
    // When
    workflowInstanceReader.getWorkflowInstanceWithOperationsByKey(key);
    // then throw NotFoundException
  }

}
