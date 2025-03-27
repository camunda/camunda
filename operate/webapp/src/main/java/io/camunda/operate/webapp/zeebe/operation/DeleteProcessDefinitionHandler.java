/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation;

import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.KEY;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.PROCESS_KEY;

import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.ProcessStore;
import io.camunda.operate.util.OperationsManager;
import io.camunda.operate.webapp.reader.ProcessReader;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
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

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DeleteProcessDefinitionHandler.class);

  @Autowired private OperationsManager operationsManager;

  @Autowired private ProcessReader processReader;

  @Autowired private ProcessStore processStore;

  @Autowired private ListViewTemplate listViewTemplate;

  @Override
  public void handleWithException(final OperationEntity operation) throws Exception {

    final Long processDefinitionKey = operation.getProcessDefinitionKey();
    if (processDefinitionKey == null) {
      failOperation(operation, "No process definition key is provided.");
      return;
    }

    final List<ProcessInstanceForListViewEntity> runningInstances =
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

    LOGGER.info(
        String.format(
            "Operation [%s]: Sending Zeebe delete command for processDefinitionKey [%s]...",
            operation.getId(), processDefinitionKey));
    final var deleteResourceCommand =
        withOperationReference(
            camundaClient.newDeleteResourceCommand(processDefinitionKey), operation.getId());
    deleteResourceCommand.send().join();
    markAsSent(operation);
    LOGGER.info(
        String.format(
            "Operation [%s]: Delete command sent to Zeebe for processDefinitionKey [%s]",
            operation.getId(), processDefinitionKey));

    cascadeDeleteProcessInstances(processDefinitionKey, operation);

    final long deleted = processStore.deleteProcessDefinitionsByKeys(processDefinitionKey);
    LOGGER.info(
        String.format(
            "Operation [%s]: Total process definitions deleted: %s", operation.getId(), deleted));
    completeOperation(operation);
    LOGGER.info(String.format("Operation [%s]: Completed.", operation.getId()));
  }

  @Override
  public Set<OperationType> getTypes() {
    return Set.of(OperationType.DELETE_PROCESS_DEFINITION);
  }

  private void cascadeDeleteProcessInstances(
      final Long processDefinitionKey, final OperationEntity operation)
      throws PersistenceException {

    // Delete in blocks (to avoid out of memory) and bottom-up from child to parent (to avoid
    // leaving orphans)
    final int blockSize = operateProperties.getOperationExecutor().getDeletionBatchSize();
    final String[] includeFields = new String[] {KEY, PROCESS_KEY};
    final Set<ProcessInstanceState> states =
        Set.of(ProcessInstanceState.CANCELED, ProcessInstanceState.COMPLETED);

    long totalDeleted = 0;
    while (true) {
      final List<ProcessInstanceForListViewEntity> processInstances =
          processStore.getProcessInstancesByProcessAndStates(
              processDefinitionKey, states, blockSize, includeFields);
      if (processInstances.isEmpty()) {
        break;
      }
      final List<List<ProcessInstanceForListViewEntity>> treeLevels = new ArrayList<>();
      treeLevels.add(processInstances);
      int currentLevel = 0;
      while (!treeLevels.isEmpty()) {
        final List<ProcessInstanceForListViewEntity> currentProcessInstances =
            treeLevels.get(currentLevel);
        final Set<Long> currentKeys =
            currentProcessInstances.stream()
                .map(ProcessInstanceForListViewEntity::getKey)
                .collect(Collectors.toSet());
        final List<ProcessInstanceForListViewEntity> children =
            processStore.getProcessInstancesByParentKeys(currentKeys, blockSize, includeFields);
        if (children.isEmpty()) {
          final long deleted = processStore.deleteProcessInstancesAndDependants(currentKeys);
          updateInstancesInBatchOperation(operation, currentKeys.size());
          LOGGER.info(
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
    LOGGER.info(
        String.format(
            "Operation [%s]: Total process instances and dependants deleted: %s",
            operation.getId(), totalDeleted));
  }

  private void completeOperation(final OperationEntity operation) throws PersistenceException {
    operationsManager.completeOperation(operation);
  }

  private void updateInstancesInBatchOperation(
      final OperationEntity operation, final long increment) throws PersistenceException {
    operationsManager.updateInstancesInBatchOperation(operation.getBatchOperationId(), increment);
  }
}
