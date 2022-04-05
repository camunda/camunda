/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.es;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.webapp.es.reader.OperationReader;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.DateUtil;
import io.camunda.operate.util.ElasticsearchTestRule;
import io.camunda.operate.util.OperateIntegrationTest;
import io.camunda.operate.util.TestUtil;
import io.camunda.operate.webapp.zeebe.operation.OperationExecutor;
import io.camunda.operate.webapp.zeebe.operation.OperationHandler;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;
import static io.camunda.operate.util.TestUtil.createOperationEntity;
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

  private OffsetDateTime approxLockExpirationTime;

  @Before
  public void before() {
    super.before();
    approxLockExpirationTime = testStartTime.plus(operateProperties.getOperationExecutor().getLockTimeout(), ChronoUnit.MILLIS);
  }

  /**
   * Test creates process instances in quantity of 0.75*batchSize approx. Each process instance has 3 operations:
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
    assertOperationsLocked(operationReader.getOperationsByProcessInstanceKey(null), batchSize, "lockFirstBatch");

    //when execute 2nd batch
    operationExecutor.executeOneBatch();
    //then
    final int expectedLockedOperations = instancesCount*2;
    assertOperationsLocked(operationReader.getOperationsByProcessInstanceKey(null), expectedLockedOperations, "lockSecondBatch");
  }

  private void assertOperationsLocked(List<OperationEntity> allOperations, int operationCount, String assertionLabel) {
    String workerId = operateProperties.getOperationExecutor().getWorkerId();
    List<OperationEntity> lockedOperations = CollectionUtil.filter(allOperations,
        op -> op.getState().equals(OperationState.LOCKED) && op.getLockOwner().equals(workerId)
    );
    assertThat(lockedOperations).as(assertionLabel + ".operations.size").hasSize(operationCount);
    assertThat(lockedOperations).extracting(OperationTemplate.LOCK_OWNER).as(assertionLabel + "operation.lockOwner").containsOnly(
        workerId);
    assertThat(lockedOperations).filteredOn(op -> op.getLockExpirationTime().isBefore(approxLockExpirationTime)).as(assertionLabel + "operation.lockExpirationTime").isEmpty();
  }

  private void createData(int processInstanceCount) {
    List<OperateEntity> instances = new ArrayList<>();
    for (int i = 0; i<processInstanceCount; i++) {
      instances.addAll(createProcessInstanceAndOperations());
    }
    //persist instances
    elasticsearchTestRule.persistNew(instances.toArray(new OperateEntity[instances.size()]));
  }

  private List<OperateEntity> createProcessInstanceAndOperations() {
    List<OperateEntity> entities = new ArrayList<>();
    ProcessInstanceForListViewEntity processInstance = TestUtil.createProcessInstanceEntityWithIds();
    processInstance.setBpmnProcessId("testProcess" + random.nextInt(10));
    processInstance.setStartDate(DateUtil.getRandomStartDate());
    processInstance.setState(ProcessInstanceState.ACTIVE);
    entities.add(processInstance);
    entities.add(createOperationEntity(processInstance.getProcessInstanceKey(), OperationState.SCHEDULED));
    entities.add(createOperationEntity(processInstance.getProcessInstanceKey(), OperationState.LOCKED, true));
    entities.add(createOperationEntity(processInstance.getProcessInstanceKey(), OperationState.LOCKED, false));
    return entities;
  }

}
