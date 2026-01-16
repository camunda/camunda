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
import io.camunda.operate.util.ConditionalOnOperateCompatibility;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import io.camunda.operate.webapp.zeebe.operation.process.modify.ModifyProcessZeebeWrapper;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnOperateCompatibility(enabled = "true")
public class ClientBasedAdapter implements OperateServicesAdapter {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClientBasedAdapter.class);
  private static final List<Status.Code> RETRY_STATUSES =
      Arrays.asList(
          Status.UNAVAILABLE.getCode(),
          Status.RESOURCE_EXHAUSTED.getCode(),
          Status.DEADLINE_EXCEEDED.getCode());

  private CamundaClient camundaClient;
  private final ModifyProcessZeebeWrapper modifyProcessZeebeWrapper;

  public ClientBasedAdapter(
      final CamundaClient camundaClient,
      final ModifyProcessZeebeWrapper modifyProcessZeebeWrapper) {
    this.camundaClient = camundaClient;
    this.modifyProcessZeebeWrapper = modifyProcessZeebeWrapper;
  }

  @Override
  public void deleteResource(
      final long resourceKey, final String operationId, final boolean deleteHistory) {
    final var deleteResourceCommand =
        withOperationReference(
            camundaClient.newDeleteResourceCommand(resourceKey, deleteHistory), operationId);
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
      final String operationId) {
    // Process token (non-variable) modifications
    final ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 lastStep =
        modifyProcessZeebeWrapper.processTokenModifications(processInstanceKey, modifications);

    modifyProcessZeebeWrapper.sendModificationsToZeebe(lastStep, operationId);
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

  @Override
  public boolean isExceptionRetriable(final Throwable ex) {
    final StatusRuntimeException cause = extractStatusRuntimeException(ex);
    return cause != null && RETRY_STATUSES.contains(cause.getStatus().getCode());
  }

  private StatusRuntimeException extractStatusRuntimeException(final Throwable ex) {
    if (ex.getCause() != null) {
      if (ex.getCause() instanceof StatusRuntimeException) {
        return (StatusRuntimeException) ex.getCause();
      } else {
        return extractStatusRuntimeException(ex.getCause());
      }
    }
    return null;
  }

  public void setCamundaClient(final CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  public static <T extends CommandWithOperationReferenceStep<T>> T withOperationReference(
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
}
