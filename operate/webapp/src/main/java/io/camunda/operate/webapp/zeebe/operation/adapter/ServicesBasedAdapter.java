/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation.adapter;

import io.camunda.client.api.command.MigrationPlan;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.ConditionalOnOperateCompatibility;
import io.camunda.operate.webapp.rest.exception.NotAuthorizedException;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.security.auth.Authentication;
import io.camunda.service.ElementInstanceServices;
import io.camunda.service.ElementInstanceServices.SetVariablesRequest;
import io.camunda.service.IncidentServices;
import io.camunda.service.JobServices;
import io.camunda.service.JobServices.UpdateJobChangeset;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCancelRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceMigrateRequest;
import io.camunda.service.ResourceServices;
import io.camunda.service.ResourceServices.ResourceDeletionRequest;
import io.camunda.service.exception.CamundaBrokerException;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationMappingInstruction;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.RejectionType;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnOperateCompatibility(enabled = "false", matchIfMissing = true)
public class ServicesBasedAdapter implements OperateServicesAdapter {

  private static final Logger LOGGER = LoggerFactory.getLogger(ServicesBasedAdapter.class);

  private final ProcessInstanceServices processInstanceServices;
  private final ResourceServices resourceServices;
  private final ElementInstanceServices elementInstanceServices;
  private final JobServices<?> jobServices;
  private final IncidentServices incidentServices;

  public ServicesBasedAdapter(
      final ProcessInstanceServices processInstanceServices,
      final ResourceServices resourceServices,
      final ElementInstanceServices elementInstanceServices,
      final JobServices<?> jobServices,
      final IncidentServices incidentServices) {
    this.processInstanceServices = processInstanceServices;
    this.resourceServices = resourceServices;
    this.elementInstanceServices = elementInstanceServices;
    this.jobServices = jobServices;
    this.incidentServices = incidentServices;
  }

  @Override
  public void deleteResource(final long resourceKey, final String operationId) {
    executeCamundaServiceAuthenticated(
        (authentication) ->
            withOperationReference(
                operationReference ->
                    resourceServices.deleteResource(
                        new ResourceDeletionRequest(resourceKey, operationReference)),
                operationId));
  }

  @Override
  public void migrateProcessInstance(
      final long processInstanceKey, final MigrationPlan migrationPlan, final String operationId) {
    executeCamundaServiceAuthenticated(
        (authentication) ->
            withOperationReference(
                operationReference ->
                    processInstanceServices.migrateProcessInstance(
                        new ProcessInstanceMigrateRequest(
                            processInstanceKey,
                            migrationPlan.getTargetProcessDefinitionKey(),
                            migrationPlan.getMappingInstructions().stream()
                                .map(
                                    instruction ->
                                        new ProcessInstanceMigrationMappingInstruction()
                                            .setSourceElementId(instruction.getSourceElementId())
                                            .setTargetElementId(instruction.getTargetElementId()))
                                .toList(),
                            operationReference)),
                operationId));
  }

  @Override
  public void cancelProcessInstance(final long processInstanceKey, final String operationId) {
    executeCamundaServiceAuthenticated(
        (authentication) ->
            withOperationReference(
                operationReference ->
                    processInstanceServices.cancelProcessInstance(
                        new ProcessInstanceCancelRequest(processInstanceKey, operationReference)),
                operationId));
  }

  @Override
  public void updateJobRetries(final long jobKey, final int retries, final String operationId) {
    // operationId is not used in the updateJob service method
    executeCamundaServiceAuthenticated(
        (authentication) -> jobServices.updateJob(jobKey, new UpdateJobChangeset(retries, null)));
  }

  @Override
  public void resolveIncident(final long incidentKey, final String operationId) {
    // operationId is not used in the updateJob service method
    executeCamundaServiceAuthenticated(
        (authentication) -> incidentServices.resolveIncident(incidentKey));
  }

  @Override
  public long setVariables(
      final long scopeKey,
      final Map<String, Object> variables,
      final boolean local,
      final String operationId) {
    final var variableDocumentRecord =
        // operationId is not used in the updateJob service method
        executeCamundaServiceAuthenticated(
            (authentication) ->
                withOperationReference(
                    operationReference ->
                        elementInstanceServices.setVariables(
                            new SetVariablesRequest(
                                scopeKey, variables, local, operationReference)),
                    operationId));
    return variableDocumentRecord.getScopeKey();
  }

  protected static <T> CompletableFuture<T> withOperationReference(
      final Function<Long, CompletableFuture<T>> command, final String id) {
    try {
      final long operationReference = Long.parseLong(id);
      return command.apply(operationReference);
    } catch (final NumberFormatException e) {
      LOGGER.debug(
          "The operation reference provided is not a number: {}. Ignoring propagating it to zeebe commands.",
          id);
    }
    return null;
  }

  private <T> T executeCamundaServiceAuthenticated(
      final Function<Authentication, CompletableFuture<T>> method) {
    return executeCamundaService(method, RequestMapper.getAuthentication());
  }

  private <T> T executeCamundaService(
      final Function<Authentication, CompletableFuture<T>> method,
      final Authentication authentication) {
    try {
      return method.apply(authentication).join();
    } catch (final Exception e) {
      throw handleException(e);
    }
  }

  private RuntimeException handleException(final Throwable error) {
    return switch (error) {
      case final CompletionException ce -> handleException(ce.getCause());
      case final CamundaBrokerException cbe -> mapCamundaBrokerException(cbe);
      default -> new RuntimeException("Failed to execute request: " + error.getMessage(), error);
    };
  }

  private RuntimeException mapCamundaBrokerException(final CamundaBrokerException exception) {
    if (exception.getCause() instanceof final BrokerRejectionException brokerRejection) {
      final var rejection = brokerRejection.getRejection();
      final String message =
          String.format(
              "Request '%s' rejected with code '%s': %s",
              rejection.intent(), rejection.type(), rejection.reason());
      final var type = rejection.type();
      if (type.equals(RejectionType.NOT_FOUND)) {
        return new NotFoundException(message, exception);
      }
      if (type.equals(RejectionType.UNAUTHORIZED) || type.equals(RejectionType.FORBIDDEN)) {
        return new NotAuthorizedException(message, exception);
      }
    }
    if (exception.getCause() instanceof final BrokerErrorException brokerError) {
      final var errorCode = brokerError.getError().getCode();
      if (errorCode.equals(ErrorCode.PROCESS_NOT_FOUND)) {
        return new NotAuthorizedException("Process not found", exception);
      }
    }
    return new OperateRuntimeException(
        String.format("Failed to execute request with %s", exception.getMessage()), exception);
  }
}
