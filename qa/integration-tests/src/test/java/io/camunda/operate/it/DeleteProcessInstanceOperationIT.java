/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.util.OperateZeebeIntegrationTest;
import io.camunda.operate.webapp.es.reader.BatchOperationReader;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationRequestDto;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DeleteProcessInstanceOperationIT extends OperateZeebeIntegrationTest {

  @Autowired
  private BatchOperationReader batchOperationReader;

  @Before
  public void before() {
    super.before();
    mockMvc = mockMvcTestRule.getMockMvc();
  }

  private void startAndCompleteSimpleProcess() {
    tester
        .deployProcess("single-task.bpmn")
        .waitUntil().processIsDeployed()
        .then()
        .startProcessInstance("process")
        .waitUntil().processInstanceIsStarted()
        .then()
        .completeTask("task")
        .waitUntil().processInstanceIsCompleted();
  }

  private long startDemoProcessInstance() {
    String processId = "demoProcess";

    return tester.startProcessInstance(processId, "{\"a\": \"b\"}")
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
    List<BatchOperationEntity> operations = batchOperationReader
        .getBatchOperations(new BatchOperationRequestDto().setPageSize(5));
    assertThat(operations.size()).isEqualTo(1);
    BatchOperationEntity operation = operations.get(0);
    assertThat(operation.getOperationsTotalCount()).isEqualTo(1);
    assertThat(operation.getOperationsFinishedCount()).isEqualTo(1);
  }

  @Test
  public void testDeleteProcessInstanceFailed() throws Exception {
    // Given
    tester.deployProcess("demoProcess_v_2.bpmn")
            .waitUntil().processIsDeployed();
    startDemoProcessInstanceWithIncidents();
    // When
    tester.deleteProcessInstance().and().executeOperations();
    // then
    List<BatchOperationEntity> operations = batchOperationReader
        .getBatchOperations(new BatchOperationRequestDto().setPageSize(5));
    assertThat(operations.size()).isEqualTo(1);
    BatchOperationEntity operation = operations.get(0);
    assertThat(operation.getOperationsTotalCount()).isEqualTo(1);
    assertThat(operation.getOperationsFinishedCount()).isZero();
  }
}
