/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.zeebe.operation;

import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.util.OperationsManager;
import io.camunda.operate.webapp.reader.DecisionReader;
import io.camunda.operate.webapp.writer.DecisionWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Operation handler to delete decision definitions and related data
 */
@Component
public class DeleteDecisionDefinitionHandler extends AbstractOperationHandler implements OperationHandler {

  public static final int STEPS_COUNT = 4;

  private static final Logger logger = LoggerFactory.getLogger(DeleteDecisionDefinitionHandler.class);

  @Autowired
  private OperationsManager operationsManager;

  @Autowired
  private DecisionReader decisionReader;

  @Autowired
  private DecisionWriter decisionWriter;

  @Override
  public void handleWithException(OperationEntity operation) throws Exception {

    if (operation.getDecisionDefinitionKey() == null) {
      failOperation(operation, "No decision definition key is provided.");
      return;
    }

    DecisionDefinitionEntity decisionDefinition = decisionReader.getDecision(operation.getDecisionDefinitionKey());
    long decisionRequirementsKey = decisionDefinition.getDecisionRequirementsKey();
    long deleted;

    // Step 1. Send Zeebe command
    zeebeClient.newDeleteResourceCommand(decisionRequirementsKey).send().join();
    markAsSent(operation);
    updateFinishedInBatchOperation(operation);
    logger.info(String.format("Operation %s: Delete command sent to Zeebe for resource key [%s]", operation.getId(), decisionRequirementsKey));

    // Step 2. Delete decision instances
    deleted = decisionWriter.deleteDecisionInstancesFor(decisionRequirementsKey);
    updateInstancesInBatchOperation(operation, deleted);
    updateFinishedInBatchOperation(operation);
    logger.info(String.format("Operation %s: Deleted %s decision instances", operation.getId(), deleted));

    // Step 3. Delete decision definitions
    deleted = decisionWriter.deleteDecisionDefinitionsFor(decisionRequirementsKey);
    updateInstancesInBatchOperation(operation, deleted);
    updateFinishedInBatchOperation(operation);
    logger.info(String.format("Operation %s: Deleted %s decision definitions", operation.getId(), deleted));

    // Step 4. Delete decision requirements
    deleted = decisionWriter.deleteDecisionRequirements(decisionRequirementsKey);
    updateInstancesInBatchOperation(operation, deleted);
    completeOperation(operation);
    logger.info(String.format("Operation %s: Deleted %s decision requirements", operation.getId(), deleted));
  }

  private void completeOperation(final OperationEntity operation) throws PersistenceException {
    operationsManager.completeOperation(operation);
  }

  private void updateFinishedInBatchOperation(final OperationEntity operation) throws PersistenceException {
    operationsManager.updateFinishedInBatchOperation(operation.getBatchOperationId());
  }

  private void updateInstancesInBatchOperation(final OperationEntity operation, long increment) throws PersistenceException {
    operationsManager.updateInstancesInBatchOperation(operation.getBatchOperationId(), increment);
  }

  @Override
  public Set<OperationType> getTypes() {
    return Set.of(OperationType.DELETE_DECISION_DEFINITION);
  }
}
