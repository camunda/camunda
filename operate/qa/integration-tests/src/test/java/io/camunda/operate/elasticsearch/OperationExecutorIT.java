/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.elasticsearch;

import static io.camunda.operate.util.TestUtil.createOperationEntity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.DateUtil;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.util.TestUtil;
import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.zeebe.operation.OperationExecutor;
import io.camunda.operate.webapp.zeebe.operation.OperationHandler;
import io.camunda.webapps.schema.descriptors.operate.template.OperationTemplate;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceState;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class OperationExecutorIT extends OperateAbstractIT {

  @Rule public SearchTestRule searchTestRule = new SearchTestRule();
  private final Random random = new Random();
  @Autowired private OperateProperties operateProperties;

  @Autowired private OperationExecutor operationExecutor;

  @MockBean private Map<OperationType, OperationHandler> handlers;

  @Autowired private OperationReader operationReader;

  private OffsetDateTime approxLockExpirationTime;

  @Override
  @Before
  public void before() {
    super.before();
    approxLockExpirationTime =
        testStartTime.plus(
            operateProperties.getOperationExecutor().getLockTimeout(), ChronoUnit.MILLIS);
  }

  /**
   * Test creates process instances in quantity of 0.75*batchSize approx. Each process instance has
   * 3 operations: 1. scheduled 2. locked with expired lock time -> 3. locked with valid lock time
   *
   * <p>Then test executes two batches sequentially. 1. -> are locked by current workerId 2. -> are
   * locked by current workerId 3. -> stay locked by another worker Id
   *
   * @throws PersistenceException
   */
  @Test
  public void testOperationsAreLocked() throws PersistenceException {
    // given
    final int batchSize = operateProperties.getOperationExecutor().getBatchSize();
    given(handlers.get(any())).willReturn(null);
    final int instancesCount = (int) (batchSize * .75);
    createData(instancesCount);

    // when execute 1st batch
    operationExecutor.executeOneBatch();
    // then
    assertOperationsLocked(
        operationReader.getOperationsByProcessInstanceKey(null), batchSize, "lockFirstBatch");

    // when execute 2nd batch
    operationExecutor.executeOneBatch();
    // then
    final int expectedLockedOperations = instancesCount * 2;
    assertOperationsLocked(
        operationReader.getOperationsByProcessInstanceKey(null),
        expectedLockedOperations,
        "lockSecondBatch");
  }

  private void assertOperationsLocked(
      final List<OperationEntity> allOperations,
      final int operationCount,
      final String assertionLabel) {
    final String workerId = operateProperties.getOperationExecutor().getWorkerId();
    final List<OperationEntity> lockedOperations =
        CollectionUtil.filter(
            allOperations,
            op ->
                op.getState().equals(OperationState.LOCKED) && op.getLockOwner().equals(workerId));
    assertThat(lockedOperations).as(assertionLabel + ".operations.size").hasSize(operationCount);
    assertThat(lockedOperations)
        .extracting(OperationTemplate.LOCK_OWNER)
        .as(assertionLabel + "operation.lockOwner")
        .containsOnly(workerId);
    assertThat(lockedOperations)
        .filteredOn(op -> op.getLockExpirationTime().isBefore(approxLockExpirationTime))
        .as(assertionLabel + "operation.lockExpirationTime")
        .isEmpty();
  }

  private void createData(final int processInstanceCount) {
    final List<ExporterEntity> instances = new ArrayList<>();
    for (int i = 0; i < processInstanceCount; i++) {
      instances.addAll(createProcessInstanceAndOperations());
    }
    // persist instances
    searchTestRule.persistNew(instances.toArray(new ExporterEntity[instances.size()]));
  }

  private List<ExporterEntity> createProcessInstanceAndOperations() {
    final List<ExporterEntity> entities = new ArrayList<>();
    final ProcessInstanceForListViewEntity processInstance =
        TestUtil.createProcessInstanceEntityWithIds();
    processInstance.setBpmnProcessId("testProcess" + random.nextInt(10));
    processInstance.setStartDate(DateUtil.getRandomStartDate());
    processInstance.setState(ProcessInstanceState.ACTIVE);
    entities.add(processInstance);
    entities.add(
        createOperationEntity(processInstance.getProcessInstanceKey(), OperationState.SCHEDULED));
    entities.add(
        createOperationEntity(
            processInstance.getProcessInstanceKey(), OperationState.LOCKED, true));
    entities.add(
        createOperationEntity(
            processInstance.getProcessInstanceKey(), OperationState.LOCKED, false));
    return entities;
  }
}
