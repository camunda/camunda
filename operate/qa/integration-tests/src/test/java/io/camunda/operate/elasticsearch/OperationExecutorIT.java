/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.elasticsearch;

import static io.camunda.operate.util.TestUtil.createOperationEntity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.DateUtil;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.util.TestUtil;
import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.zeebe.operation.OperationExecutor;
import io.camunda.operate.webapp.zeebe.operation.OperationHandler;
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
  private Random random = new Random();
  @Autowired private OperateProperties operateProperties;

  @Autowired private OperationExecutor operationExecutor;

  @MockBean private Map<OperationType, OperationHandler> handlers;

  @Autowired private OperationReader operationReader;

  private OffsetDateTime approxLockExpirationTime;

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
      List<OperationEntity> allOperations, int operationCount, String assertionLabel) {
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

  private void createData(int processInstanceCount) {
    final List<OperateEntity> instances = new ArrayList<>();
    for (int i = 0; i < processInstanceCount; i++) {
      instances.addAll(createProcessInstanceAndOperations());
    }
    // persist instances
    searchTestRule.persistNew(instances.toArray(new OperateEntity[instances.size()]));
  }

  private List<OperateEntity> createProcessInstanceAndOperations() {
    final List<OperateEntity> entities = new ArrayList<>();
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
