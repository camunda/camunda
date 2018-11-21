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
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.OperationState;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.es.writer.BatchOperationWriter;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.property.OperateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractOperationHandler implements OperationHandler {

  private static final Logger logger = LoggerFactory.getLogger(AbstractOperationHandler.class);

  @Autowired
  protected BatchOperationWriter batchOperationWriter;
  @Autowired
  protected OperateProperties operateProperties;

  protected void failOperationsOfCurrentType(WorkflowInstanceEntity workflowInstance, String errorMsg) throws PersistenceException {
    for (OperationEntity operation: workflowInstance.getOperations()) {
      if (operation.getState().equals(OperationState.LOCKED) && operation.getLockOwner().equals(operateProperties.getOperationExecutor().getWorkerId())
        && operation.getType().equals(getType())) {
        operation.setState(OperationState.FAILED);
        operation.setLockExpirationTime(null);
        operation.setLockOwner(null);
        operation.setEndDate(OffsetDateTime.now());
        operation.setErrorMessage(errorMsg);
        batchOperationWriter.updateOperation(workflowInstance.getId(), operation);
        logger.debug("Operation {} failed with message: {} ", operation.getId(), operation.getErrorMessage());
        break;
      }
    }
  }

  protected void markAsSentOperationsOfCurrentType(WorkflowInstanceEntity workflowInstance) throws PersistenceException {
    for (OperationEntity operation: workflowInstance.getOperations()) {
      if (operation.getState().equals(OperationState.LOCKED) && operation.getLockOwner().equals(operateProperties.getOperationExecutor().getWorkerId())
        && operation.getType().equals(getType())) {
        operation.setState(OperationState.SENT);
        operation.setLockExpirationTime(null);
        operation.setLockOwner(null);
        batchOperationWriter.updateOperation(workflowInstance.getId(), operation);
        logger.debug("Operation {} was sent to Zeebe", operation.getId());
        break;
      }
    }
  }
}
