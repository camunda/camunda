/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.webapp.reader.BatchOperationReader;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationRequestDto;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DeleteProcessInstanceOperationZeebeIT extends OperateZeebeAbstractIT {

  @Autowired private BatchOperationReader batchOperationReader;

  @Override
  @Before
  public void before() {
    super.before();
    mockMvc = mockMvcTestRule.getMockMvc();
  }

  private void startAndCompleteSimpleProcess() {
    tester
        .deployProcess("single-task.bpmn")
        .waitUntil()
        .processIsDeployed()
        .then()
        .startProcessInstance("process")
        .waitUntil()
        .processInstanceIsStarted()
        .then()
        .completeTask("task")
        .waitUntil()
        .processInstanceIsCompleted();
  }

  private long startDemoProcessInstance() {
    final String processId = "demoProcess";

    return tester
        .startProcessInstance(processId, "{\"a\": \"b\"}")
        .waitUntil()
        .flowNodeIsActive("taskA")
        .getProcessInstanceKey();
  }

  private void startDemoProcessInstanceWithIncidents() {
    final long processInstanceKey = startDemoProcessInstance();
    failTaskWithNoRetriesLeft("taskA", processInstanceKey, "some error");
    failTaskWithNoRetriesLeft("taskD", processInstanceKey, "some error");
  }

  @Test
  public void testDeleteProcessInstanceSucceed() throws Exception {
    // Given
    startAndCompleteSimpleProcess();
    // When
    tester.deleteProcessInstance().and().executeOperations();
    // then
    final List<BatchOperationEntity> operations =
        batchOperationReader.getBatchOperations(new BatchOperationRequestDto().setPageSize(5));
    assertThat(operations.size()).isEqualTo(1);
    final BatchOperationEntity operation = operations.get(0);
    assertThat(operation.getOperationsTotalCount()).isEqualTo(1);
    assertThat(operation.getOperationsFinishedCount()).isEqualTo(1);
  }

  @Test
  public void testDeleteProcessInstanceFailed() throws Exception {
    // Given
    tester.deployProcess("demoProcess_v_2.bpmn").waitUntil().processIsDeployed();
    startDemoProcessInstanceWithIncidents();
    // When
    tester.deleteProcessInstance().and().executeOperations();
    // then
    final List<BatchOperationEntity> operations =
        batchOperationReader.getBatchOperations(new BatchOperationRequestDto().setPageSize(5));
    assertThat(operations.size()).isEqualTo(1);
    final BatchOperationEntity operation = operations.get(0);
    assertThat(operation.getOperationsTotalCount()).isEqualTo(1);
    assertThat(operation.getOperationsFinishedCount()).isEqualTo(1);
  }
}
