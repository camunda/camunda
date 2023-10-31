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

    logger.info(String.format("Operation [%s]: Sending Zeebe delete command for decisionRequirementsKey [%s]...", operation.getId(), decisionRequirementsKey));
    zeebeClient.newDeleteResourceCommand(decisionRequirementsKey).send().join();
    markAsSent(operation);
    logger.info(String.format("Operation [%s]: Delete command sent to Zeebe for decisionRequirementsKey [%s]", operation.getId(), decisionRequirementsKey));

    deleted = decisionWriter.deleteDecisionInstancesFor(decisionRequirementsKey);
    updateInstancesInBatchOperation(operation, deleted);
    logger.info(String.format("Operation [%s]: Deleted %s decision instances", operation.getId(), deleted));

    deleted = decisionWriter.deleteDecisionDefinitionsFor(decisionRequirementsKey);
    logger.info(String.format("Operation [%s]: Deleted %s decision definitions", operation.getId(), deleted));

    deleted = decisionWriter.deleteDecisionRequirements(decisionRequirementsKey);
    completeOperation(operation);
    logger.info(String.format("Operation [%s]: Deleted %s decision requirements", operation.getId(), deleted));
  }

  private void completeOperation(final OperationEntity operation) throws PersistenceException {
    operationsManager.completeOperation(operation);
  }

  private void updateInstancesInBatchOperation(final OperationEntity operation, long increment) throws PersistenceException {
    operationsManager.updateInstancesInBatchOperation(operation.getBatchOperationId(), increment);
  }

  @Override
  public Set<OperationType> getTypes() {
    return Set.of(OperationType.DELETE_DECISION_DEFINITION);
  }
}
