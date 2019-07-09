/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebe.operation;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.OperationState;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceState;
import org.camunda.operate.es.reader.OperationReader;
import org.camunda.operate.es.schema.templates.OperationTemplate;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.DateUtil;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.OperateIntegrationTest;
import org.camunda.operate.util.TestUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

public class OperationExecutorIT extends OperateIntegrationTest {

  private Random random = new Random();

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private OperationExecutor operationExecutor;

  @MockBean
  private Map<OperationType, OperationHandler> handlers;

  @Autowired
  private OperationReader operationReader;

  private OffsetDateTime testStartTime;
  private OffsetDateTime approxLockExpirationTime;

  @Before
  public void init() {
    testStartTime = OffsetDateTime.now();
    approxLockExpirationTime = testStartTime.plus(operateProperties.getOperationExecutor().getLockTimeout(), ChronoUnit.MILLIS);
  }

  /**
   * Test creates workflow instances in quantity of 0.75*batchSize approx. Each workflow instance has 3 operations:
   * 1. scheduled
   * 2. locked with expired lock time ->
   * 3. locked with valid lock time
   *
   * Then test executes two batches sequentially.
   * 1. -> are locked by current workerId
   * 2. -> are locked by current workerId
   * 3. -> stay locked by another worker Id
   * @throws PersistenceException
   */
  @Test
  public void testOperationsAreLocked() throws PersistenceException {
    //given
    final int batchSize = operateProperties.getOperationExecutor().getBatchSize();
    given(handlers.get(any())).willReturn(null);
    int instancesCount = (int)(batchSize * .75);
    createData(instancesCount);

    //when execute 1st batch
    operationExecutor.executeOneBatch();
    //then
    assertOperationsLocked(operationReader.getOperations(null), batchSize, "lockFirstBatch");

    //when execute 2nd batch
    operationExecutor.executeOneBatch();
    //then
    final int expectedLockedOperations = instancesCount*2;
    assertOperationsLocked(operationReader.getOperations(null), expectedLockedOperations, "lockSecondBatch");
  }

  private void assertOperationsLocked(List<OperationEntity> allOperations, int operationCount, String assertionLabel) {
    String workerId = operateProperties.getOperationExecutor().getWorkerId();
    List<OperationEntity> lockedOperations = allOperations.stream()
        .filter(op -> op.getState().equals(OperationState.LOCKED) && op.getLockOwner().equals(workerId))
        .collect(Collectors.toList());
    assertThat(lockedOperations).as(assertionLabel + ".operations.size").hasSize(operationCount);
    assertThat(lockedOperations).extracting(OperationTemplate.LOCK_OWNER).as(assertionLabel + "operation.lockOwner").containsOnly(
        workerId);
    assertThat(lockedOperations).filteredOn(op -> op.getLockExpirationTime().isBefore(approxLockExpirationTime)).as(assertionLabel + "operation.lockExpirationTime").isEmpty();
  }

  private void createData(int processInstanceCount) {
    List<OperateEntity> instances = new ArrayList<>();
    for (int i = 0; i<processInstanceCount; i++) {
      instances.addAll(createWorkflowInstanceAndOperations());
    }
    //persist instances
    elasticsearchTestRule.persistNew(instances.toArray(new OperateEntity[instances.size()]));
  }

  private List<OperateEntity> createWorkflowInstanceAndOperations() {
    List<OperateEntity> entities = new ArrayList<>();
    WorkflowInstanceForListViewEntity workflowInstance = TestUtil.createWorkflowInstanceEntityWithIds();
    workflowInstance.setBpmnProcessId("testProcess" + random.nextInt(10));
    workflowInstance.setStartDate(DateUtil.getRandomStartDate());
    workflowInstance.setState(WorkflowInstanceState.ACTIVE);
    entities.add(workflowInstance);
    entities.add(createOperation(workflowInstance.getWorkflowInstanceKey(), OperationState.SCHEDULED));
    entities.add(createOperation(workflowInstance.getWorkflowInstanceKey(), OperationState.LOCKED, true));
    entities.add(createOperation(workflowInstance.getWorkflowInstanceKey(), OperationState.LOCKED, false));
    return entities;
  }

  private OperationEntity createOperation(Long workflowInstanceId, OperationState state, boolean lockExpired) {
    final OperationEntity operation = createOperation(workflowInstanceId, state);
    if (lockExpired) {
      operation.setLockExpirationTime(OffsetDateTime.now().minus(1, ChronoUnit.MILLIS));
      operation.setLockOwner("otherWorkerId");
    }
    return operation;
  }

  private OperationEntity createOperation(Long workflowInstanceId, OperationState state) {
    OperationEntity operation = new OperationEntity();
    operation.setWorkflowInstanceKey(workflowInstanceId);
    operation.generateId();
    operation.setState(state);
    operation.setStartDate(OffsetDateTime.now());
    operation.setType(OperationType.RESOLVE_INCIDENT);
    if (state.equals(OperationState.LOCKED)) {
      operation.setLockOwner("otherWorkerId");
      operation.setLockExpirationTime(OffsetDateTime.now().plus(operateProperties.getOperationExecutor().getLockTimeout(), ChronoUnit.MILLIS));
    }
    return operation;
  }

}
