/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.operate.zeebe.operation;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.OperationState;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.es.types.WorkflowInstanceType;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.DateUtil;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.OperateIntegrationTest;
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

  private OffsetDateTime testStartTime;
  private OffsetDateTime approxLockExpirationTime;

  @Before
  public void init() {
    testStartTime = OffsetDateTime.now();
    approxLockExpirationTime = testStartTime.plus(operateProperties.getOperationExecutor().getLockTimeout(), ChronoUnit.MILLIS);
  }

  /**
   * Test creates workflow instances in quantity of 1.5*batchSize approx. Each workflow instance has 3 operations:
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
    int instancesCount = (int)(batchSize * 1.5);
    createData(instancesCount);

    //when execute 1st batch
    Map<String, List<OperationEntity>> lockedOperations = operationExecutor.executeOneBatch();
    //then
    assertOperationsLocked(lockedOperations, batchSize, batchSize*2, "lockFirstBatch");

    //when execute 2nd batch
    lockedOperations = operationExecutor.executeOneBatch();
    //then
    final int expectedLockedInstances = instancesCount - batchSize;
    assertOperationsLocked(lockedOperations, expectedLockedInstances, expectedLockedInstances*2, "lockSecondBatch");
  }

  private void assertOperationsLocked(Map<String, List<OperationEntity>> lockedOperations, int workflowInstanceCount, int operationCount, String assertionLabel) {
    assertThat(lockedOperations).as(assertionLabel + ".workflowInstances.size").hasSize(workflowInstanceCount);
    final List<OperationEntity> allOperations = lockedOperations.values().stream().flatMap(entry -> entry.stream()).collect(Collectors.toList());
    assertThat(allOperations).as(assertionLabel + ".operations.size").hasSize(operationCount);
    assertThat(allOperations).extracting(WorkflowInstanceType.STATE).as(assertionLabel + "operation.state").containsOnly(OperationState.LOCKED);
    assertThat(allOperations).extracting(WorkflowInstanceType.LOCK_OWNER).as(assertionLabel + "operation.lockOwner").containsOnly(operateProperties.getOperationExecutor().getWorkerId());
    assertThat(allOperations).filteredOn(op -> op.getLockExpirationTime().isBefore(approxLockExpirationTime)).as(assertionLabel + "operation.lockExpirationTime").isEmpty();
  }

  private void createData(int processInstanceCount) {
    List<WorkflowInstanceEntity> instances = new ArrayList<>();
    for (int i = 0; i<processInstanceCount; i++) {
      instances.add(createWorkflowInstance());
    }
    //persist instances
    elasticsearchTestRule.persist(instances.toArray(new WorkflowInstanceEntity[instances.size()]));
  }

  private WorkflowInstanceEntity createWorkflowInstance() {
    WorkflowInstanceEntity workflowInstance = new WorkflowInstanceEntity();
    workflowInstance.setId(UUID.randomUUID().toString());
    workflowInstance.setBusinessKey("testProcess" + random.nextInt(10));
    workflowInstance.setStartDate(DateUtil.getRandomStartDate());
    workflowInstance.setState(WorkflowInstanceState.ACTIVE);
    workflowInstance.getOperations().add(createOperation(OperationState.SCHEDULED));
    workflowInstance.getOperations().add(createOperation(OperationState.LOCKED, true));
    workflowInstance.getOperations().add(createOperation(OperationState.LOCKED, false));
    return workflowInstance;
  }

  private OperationEntity createOperation(OperationState state, boolean lockExpired) {
    final OperationEntity operation = createOperation(state);
    if (lockExpired) {
      operation.setLockExpirationTime(OffsetDateTime.now().minus(1, ChronoUnit.MILLIS));
      operation.setLockOwner("otherWorkerId");
    }
    return operation;
  }

  private OperationEntity createOperation(OperationState state) {
    OperationEntity operation = new OperationEntity();
    operation.generateId();
    operation.setState(state);
    operation.setStartDate(OffsetDateTime.now());
    operation.setType(OperationType.UPDATE_RETRIES);
    if (state.equals(OperationState.LOCKED)) {
      operation.setLockOwner("otherWorkerId");
      operation.setLockExpirationTime(OffsetDateTime.now().plus(operateProperties.getOperationExecutor().getLockTimeout(), ChronoUnit.MILLIS));
    }
    return operation;
  }

}
