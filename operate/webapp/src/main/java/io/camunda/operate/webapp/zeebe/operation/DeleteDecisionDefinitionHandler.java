/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation;

import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.util.OperationsManager;
import io.camunda.operate.webapp.reader.DecisionReader;
import io.camunda.operate.webapp.writer.DecisionWriter;
import io.camunda.webapps.schema.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Operation handler to delete decision definitions and related data */
@Component
public class DeleteDecisionDefinitionHandler extends AbstractOperationHandler
    implements OperationHandler {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DeleteDecisionDefinitionHandler.class);

  @Autowired private OperationsManager operationsManager;

  @Autowired private DecisionReader decisionReader;

  @Autowired private DecisionWriter decisionWriter;

  @Override
  public void handleWithException(final OperationEntity operation) throws Exception {

    if (operation.getDecisionDefinitionKey() == null) {
      failOperation(operation, "No decision definition key is provided.");
      return;
    }

    final DecisionDefinitionEntity decisionDefinition =
        decisionReader.getDecision(operation.getDecisionDefinitionKey());
    final long decisionRequirementsKey = decisionDefinition.getDecisionRequirementsKey();
    long deleted;

    LOGGER.info(
        String.format(
            "Operation [%s]: Sending Zeebe delete command for decisionRequirementsKey [%s]...",
            operation.getId(), decisionRequirementsKey));
    final var deleteResourceCommand =
        withOperationReference(
            camundaClient.newDeleteResourceCommand(decisionRequirementsKey), operation.getId());
    deleteResourceCommand.send().join();
    markAsSent(operation);
    LOGGER.info(
        String.format(
            "Operation [%s]: Delete command sent to Zeebe for decisionRequirementsKey [%s]",
            operation.getId(), decisionRequirementsKey));

    deleted = decisionWriter.deleteDecisionInstancesFor(decisionRequirementsKey);
    updateInstancesInBatchOperation(operation, deleted);
    LOGGER.info(
        String.format("Operation [%s]: Deleted %s decision instances", operation.getId(), deleted));

    deleted = decisionWriter.deleteDecisionDefinitionsFor(decisionRequirementsKey);
    LOGGER.info(
        String.format(
            "Operation [%s]: Deleted %s decision definitions", operation.getId(), deleted));

    deleted = decisionWriter.deleteDecisionRequirements(decisionRequirementsKey);
    completeOperation(operation);
    LOGGER.info(
        String.format(
            "Operation [%s]: Deleted %s decision requirements", operation.getId(), deleted));
  }

  @Override
  public Set<OperationType> getTypes() {
    return Set.of(OperationType.DELETE_DECISION_DEFINITION);
  }

  private void completeOperation(final OperationEntity operation) throws PersistenceException {
    operationsManager.completeOperation(operation);
  }

  private void updateInstancesInBatchOperation(
      final OperationEntity operation, final long increment) throws PersistenceException {
    operationsManager.updateInstancesInBatchOperation(operation.getBatchOperationId(), increment);
  }
}
