/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.zeebe.operation;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.camunda.operate.schema.templates.ListViewTemplate.KEY;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_KEY;

/**
 * Operation handler to delete process definitions and related data
 */
@Component
public class DeleteProcessDefinitionHandler extends AbstractOperationHandler implements OperationHandler {

  private static final Logger logger = LoggerFactory.getLogger(DeleteProcessDefinitionHandler.class);

  @Autowired
  private OperationsManager operationsManager;

  @Autowired
  private ProcessReader processReader;

  @Autowired
  private ProcessStore processStore;

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Override
  public void handleWithException(OperationEntity operation) throws Exception {

    Long processDefinitionKey = operation.getProcessDefinitionKey();
    if (processDefinitionKey == null) {
      failOperation(operation, "No process definition key is provided.");
      return;
    }

    List<ProcessInstanceForListViewEntity> runningInstances = processStore.getProcessInstancesByProcessAndStates(processDefinitionKey,
            Set.of(ProcessInstanceState.ACTIVE), 1, null);
    if (!runningInstances.isEmpty()) {
      failOperation(operation, String.format("Cannot delete process definition with key [%s]. Process instances still running.", processDefinitionKey));
      return;
    }

    logger.info(String.format("Operation [%s]: Sending Zeebe delete command for processDefinitionKey [%s]...", operation.getId(), processDefinitionKey));
    zeebeClient.newDeleteResourceCommand(processDefinitionKey).send().join();
    markAsSent(operation);
    logger.info(String.format("Operation [%s]: Delete command sent to Zeebe for processDefinitionKey [%s]", operation.getId(), processDefinitionKey));

    cascadeDeleteProcessInstances(processDefinitionKey, operation);

    long deleted = processStore.deleteProcessDefinitionsByKeys(processDefinitionKey);
    logger.info(String.format("Operation [%s]: Total process definitions deleted: %s", operation.getId(), deleted));
    updateInstancesInBatchOperation(operation, deleted);
    completeOperation(operation);
    logger.info(String.format("Operation [%s]: Completed.", operation.getId()));
  }

  private void cascadeDeleteProcessInstances(Long processDefinitionKey, OperationEntity operation) throws PersistenceException {

    // Delete in blocks (to avoid out of memory) and bottom-up from child to parent (to avoid leaving orphans)
    final int blockSize = operateProperties.getOperationExecutor().getDeletionBatchSize();
    final String[] includeFields = new String[]{KEY, PROCESS_KEY};
    final Set<ProcessInstanceState> states = Set.of(ProcessInstanceState.CANCELED, ProcessInstanceState.COMPLETED);

    long totalDeleted = 0;
    while (true) {
      List<ProcessInstanceForListViewEntity> processInstances = processStore.getProcessInstancesByProcessAndStates(processDefinitionKey, states,
              blockSize, includeFields);
      if (processInstances.isEmpty()) {
        break;
      }
      List<List<ProcessInstanceForListViewEntity>> treeLevels = new ArrayList<>();
      treeLevels.add(processInstances);
      int currentLevel = 0;
      while (!treeLevels.isEmpty()) {
        List<ProcessInstanceForListViewEntity> currentProcessInstances = treeLevels.get(currentLevel);
        Set<Long> currentKeys = currentProcessInstances.stream().map(OperateZeebeEntity::getKey).collect(Collectors.toSet());
        List<ProcessInstanceForListViewEntity> children = processStore.getProcessInstancesByParentKeys(currentKeys, blockSize, includeFields);
        if (children.isEmpty()) {
          long deleted = processStore.deleteProcessInstancesAndDependants(currentKeys);
          updateInstancesInBatchOperation(operation, currentKeys.size());
          logger.info(String.format("Operation [%s]: Deleted %s documents on level %s", operation.getId(), deleted, currentLevel));
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
    logger.info(String.format("Operation [%s]: Total process instances and dependants deleted: %s", operation.getId(), totalDeleted));
  }

  private void completeOperation(final OperationEntity operation) throws PersistenceException {
    operationsManager.completeOperation(operation);
  }

  private void updateInstancesInBatchOperation(final OperationEntity operation, long increment) throws PersistenceException {
    operationsManager.updateInstancesInBatchOperation(operation.getBatchOperationId(), increment);
  }

  @Override
  public Set<OperationType> getTypes() {
    return Set.of(OperationType.DELETE_PROCESS_DEFINITION);
  }
}
