/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation.adapter;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CommandWithOperationReferenceStep;
import io.camunda.client.api.command.MigrationPlan;
import io.camunda.client.api.command.ModifyProcessInstanceCommandStep1;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.util.ConditionalOnOperateCompatibility;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import io.camunda.operate.webapp.zeebe.operation.process.modify.AddTokenHandler;
import io.camunda.operate.webapp.zeebe.operation.process.modify.CancelTokenHandler;
import io.camunda.operate.webapp.zeebe.operation.process.modify.MoveTokenHandler;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnOperateCompatibility(enabled = "true")
public class ClientBasedAdapter implements OperateServicesAdapter {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClientBasedAdapter.class);

  private CamundaClient camundaClient;
  private final AddTokenHandler addTokenHandler;
  private final CancelTokenHandler cancelTokenHandler;
  private final MoveTokenHandler moveTokenHandler;

  public ClientBasedAdapter(
      final CamundaClient camundaClient,
      final AddTokenHandler addTokenHandler,
      final CancelTokenHandler cancelTokenHandler,
      final MoveTokenHandler moveTokenHandler) {
    this.camundaClient = camundaClient;
    this.addTokenHandler = addTokenHandler;
    this.cancelTokenHandler = cancelTokenHandler;
    this.moveTokenHandler = moveTokenHandler;
  }

  @Override
  public void deleteResource(final long resourceKey, final String operationId) {
    final var deleteResourceCommand =
        withOperationReference(camundaClient.newDeleteResourceCommand(resourceKey), operationId);
    deleteResourceCommand.send().join();
  }

  @Override
  public void migrateProcessInstance(
      final long processInstanceKey, final MigrationPlan migrationPlan, final String operationId) {
    final var migrateProcessInstanceCommand =
        withOperationReference(
            camundaClient
                .newMigrateProcessInstanceCommand(processInstanceKey)
                .migrationPlan(migrationPlan),
            operationId);
    migrateProcessInstanceCommand.send().join();
  }

  @Override
  public void modifyProcessInstance(
      final long processInstanceKey,
      final List<Modification> modifications,
      final String operationId)
      throws Exception {
    // Process token (non-variable) modifications
    final ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 lastStep =
        processTokenModifications(processInstanceKey, modifications);

    if (lastStep != null) {
      withOperationReference(lastStep, operationId).send().join();
    }
  }

  @Override
  public void cancelProcessInstance(final long processInstanceKey, final String operationId) {
    final var cancelInstanceCommand =
        withOperationReference(
            camundaClient.newCancelInstanceCommand(processInstanceKey), operationId);
    cancelInstanceCommand.send().join();
  }

  @Override
  public void updateJobRetries(final long jobKey, final int retries, final String operationId) {
    final var updateRetriesJobCommand =
        withOperationReference(
            camundaClient.newUpdateRetriesCommand(jobKey).retries(retries), operationId);
    updateRetriesJobCommand.send().join();
  }

  @Override
  public void resolveIncident(final long incidentKey, final String operationId) {
    final var resolveIncidentCommand =
        withOperationReference(camundaClient.newResolveIncidentCommand(incidentKey), operationId);
    resolveIncidentCommand.send().join();
  }

  @Override
  public long setVariables(
      final long scopeKey,
      final Map<String, Object> variables,
      final boolean local,
      final String operationId) {
    final var setVariablesCommand =
        withOperationReference(
            camundaClient.newSetVariablesCommand(scopeKey).variables(variables).local(true),
            operationId);

    final var response = setVariablesCommand.send().join();
    return response.getKey();
  }

  public void setCamundaClient(final CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  protected static <T extends CommandWithOperationReferenceStep<T>> T withOperationReference(
      final T command, final String id) {
    try {
      final long operationReference = Long.parseLong(id);
      command.operationReference(operationReference);
    } catch (final NumberFormatException e) {
      LOGGER.debug(
          "The operation reference provided is not a number: {}. Ignoring propagating it to zeebe commands.",
          id);
    }
    return command;
  }

  private ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2
      processTokenModifications(
          final Long processInstanceKey, final List<Modification> tokenModifications)
          throws PersistenceException {
    ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 lastStep = null;

    ModifyProcessInstanceCommandStep1 currentStep =
        camundaClient.newModifyProcessInstanceCommand(processInstanceKey);

    for (final var iter = tokenModifications.iterator(); iter.hasNext(); ) {
      final Modification modification = iter.next();
      ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 nextStep = null;
      switch (modification.getModification()) {
        case ADD_TOKEN:
          nextStep = addTokenHandler.addToken(currentStep, modification);
          break;
        case CANCEL_TOKEN:
          nextStep = cancelTokenHandler.cancelToken(currentStep, processInstanceKey, modification);
          break;
        case MOVE_TOKEN:
          nextStep = moveTokenHandler.moveToken(currentStep, processInstanceKey, modification);
          break;
        default:
          LOGGER.warn(
              "ModifyProcessInstanceHandler encountered a modification type that should have been filtered out: {}",
              modification.getModification());
          break;
      }

      // Append 'and' if there is at least one more operation to process
      if (nextStep != null) {
        lastStep = nextStep;
        if (iter.hasNext()) {
          currentStep = nextStep.and();
        }
      }
    }

    return lastStep;
  }
}
