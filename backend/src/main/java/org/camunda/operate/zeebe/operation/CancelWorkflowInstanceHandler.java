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

import org.camunda.operate.entities.OperationType;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.exceptions.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.cmd.ClientException;

/**
 * Operation handler to cancel workflow instances.
 */
@Component
public class CancelWorkflowInstanceHandler extends AbstractOperationHandler implements OperationHandler {

  private static final Logger logger = LoggerFactory.getLogger(CancelWorkflowInstanceHandler.class);

  @Autowired
  private WorkflowInstanceReader workflowInstanceReader;

  @Autowired
  private ZeebeClient zeebeClient;

  @Override
  public void handle(String workflowInstanceId) throws PersistenceException {
    final WorkflowInstanceEntity workflowInstance = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);

    if (!workflowInstance.getState().equals(WorkflowInstanceState.ACTIVE)) {
      //fail operation
      failOperationsOfCurrentType(workflowInstance, String.format("Unable to cancel %s workflow instance. Instance must be in ACTIVE state.", workflowInstance.getState()));
      return;
    }

    try {
      zeebeClient.newCancelInstanceCommand(workflowInstance.getKey()).send().join();
      //mark operation as sent
      markAsSentOperationsOfCurrentType(workflowInstance);
    } catch (ClientException ex) {
      logger.error("Zeebe command rejected: " + ex.getMessage(), ex);
      //fail operation
      failOperationsOfCurrentType(workflowInstance, ex.getMessage());
    }


  }

  @Override
  public OperationType getType() {
    return OperationType.CANCEL;
  }
}
