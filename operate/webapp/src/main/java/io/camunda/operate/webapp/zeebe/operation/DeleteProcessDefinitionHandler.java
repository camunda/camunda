/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.operate.webapp.zeebe.operation;

import static io.camunda.operate.schema.templates.ListViewTemplate.KEY;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_KEY;

import io.camunda.operate.entities.OperateZeebeEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.ProcessStore;
import io.camunda.operate.util.OperationsManager;
import io.camunda.operate.webapp.reader.ProcessReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Operation handler to delete process definitions and related data */
@Component
public class DeleteProcessDefinitionHandler extends AbstractOperationHandler
    implements OperationHandler {

  private static final Logger logger =
      LoggerFactory.getLogger(DeleteProcessDefinitionHandler.class);

  @Autowired private OperationsManager operationsManager;

  @Autowired private ProcessReader processReader;

  @Autowired private ProcessStore processStore;

  @Autowired private ListViewTemplate listViewTemplate;

  @Override
  public void handleWithException(OperationEntity operation) throws Exception {

    Long processDefinitionKey = operation.getProcessDefinitionKey();
    if (processDefinitionKey == null) {
      failOperation(operation, "No process definition key is provided.");
      return;
    }

    List<ProcessInstanceForListViewEntity> runningInstances =
        processStore.getProcessInstancesByProcessAndStates(
            processDefinitionKey, Set.of(ProcessInstanceState.ACTIVE), 1, null);
    if (!runningInstances.isEmpty()) {
      failOperation(
          operation,
          String.format(
              "Cannot delete process definition with key [%s]. Process instances still running.",
              processDefinitionKey));
      return;
    }

    logger.info(
        String.format(
            "Operation [%s]: Sending Zeebe delete command for processDefinitionKey [%s]...",
            operation.getId(), processDefinitionKey));
    zeebeClient.newDeleteResourceCommand(processDefinitionKey).send().join();
    markAsSent(operation);
    logger.info(
        String.format(
            "Operation [%s]: Delete command sent to Zeebe for processDefinitionKey [%s]",
            operation.getId(), processDefinitionKey));

    cascadeDeleteProcessInstances(processDefinitionKey, operation);

    long deleted = processStore.deleteProcessDefinitionsByKeys(processDefinitionKey);
    logger.info(
        String.format(
            "Operation [%s]: Total process definitions deleted: %s", operation.getId(), deleted));
    completeOperation(operation);
    logger.info(String.format("Operation [%s]: Completed.", operation.getId()));
  }

  @Override
  public Set<OperationType> getTypes() {
    return Set.of(OperationType.DELETE_PROCESS_DEFINITION);
  }

  private void cascadeDeleteProcessInstances(Long processDefinitionKey, OperationEntity operation)
      throws PersistenceException {

    // Delete in blocks (to avoid out of memory) and bottom-up from child to parent (to avoid
    // leaving orphans)
    final int blockSize = operateProperties.getOperationExecutor().getDeletionBatchSize();
    final String[] includeFields = new String[] {KEY, PROCESS_KEY};
    final Set<ProcessInstanceState> states =
        Set.of(ProcessInstanceState.CANCELED, ProcessInstanceState.COMPLETED);

    long totalDeleted = 0;
    while (true) {
      List<ProcessInstanceForListViewEntity> processInstances =
          processStore.getProcessInstancesByProcessAndStates(
              processDefinitionKey, states, blockSize, includeFields);
      if (processInstances.isEmpty()) {
        break;
      }
      List<List<ProcessInstanceForListViewEntity>> treeLevels = new ArrayList<>();
      treeLevels.add(processInstances);
      int currentLevel = 0;
      while (!treeLevels.isEmpty()) {
        List<ProcessInstanceForListViewEntity> currentProcessInstances =
            treeLevels.get(currentLevel);
        Set<Long> currentKeys =
            currentProcessInstances.stream()
                .map(OperateZeebeEntity::getKey)
                .collect(Collectors.toSet());
        List<ProcessInstanceForListViewEntity> children =
            processStore.getProcessInstancesByParentKeys(currentKeys, blockSize, includeFields);
        if (children.isEmpty()) {
          long deleted = processStore.deleteProcessInstancesAndDependants(currentKeys);
          updateInstancesInBatchOperation(operation, currentKeys.size());
          logger.info(
              String.format(
                  "Operation [%s]: Deleted %s documents on level %s",
                  operation.getId(), deleted, currentLevel));
          totalDeleted += deleted;
          processStore.refreshIndices(listViewTemplate.getAlias());
          treeLevels.remove(currentLevel);
          currentLevel--;
        } else {
          treeLevels.add(children);
          currentLevel++;
        }
      }
    }
    logger.info(
        String.format(
            "Operation [%s]: Total process instances and dependants deleted: %s",
            operation.getId(), totalDeleted));
  }

  private void completeOperation(final OperationEntity operation) throws PersistenceException {
    operationsManager.completeOperation(operation);
  }

  private void updateInstancesInBatchOperation(final OperationEntity operation, long increment)
      throws PersistenceException {
    operationsManager.updateInstancesInBatchOperation(operation.getBatchOperationId(), increment);
  }
}
