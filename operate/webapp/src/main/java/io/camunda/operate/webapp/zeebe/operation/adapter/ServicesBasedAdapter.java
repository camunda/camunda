/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation.adapter;

import static io.camunda.service.exception.ServiceException.Status.*;

import io.camunda.client.api.command.MigrationPlan;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.ConditionalOnOperateCompatibility;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.ElementInstanceServices;
import io.camunda.service.ElementInstanceServices.SetVariablesRequest;
import io.camunda.service.IncidentServices;
import io.camunda.service.JobServices;
import io.camunda.service.JobServices.UpdateJobChangeset;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCancelRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceMigrateRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceModifyRequest;
import io.camunda.service.ResourceServices;
import io.camunda.service.ResourceServices.ResourceDeletionRequest;
import io.camunda.service.exception.ServiceException;
import io.camunda.spring.utils.ConditionalOnRdbmsDisabled;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationMappingInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationActivateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationTerminateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationVariableInstruction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnOperateCompatibility(enabled = "false", matchIfMissing = true)
@ConditionalOnRdbmsDisabled
public class ServicesBasedAdapter implements OperateServicesAdapter {

  private static final Logger LOGGER = LoggerFactory.getLogger(ServicesBasedAdapter.class);

  private final ProcessInstanceServices processInstanceServices;
  private final ResourceServices resourceServices;
  private final ElementInstanceServices elementInstanceServices;
  private final JobServices<?> jobServices;
  private final IncidentServices incidentServices;
  private final FlowNodeInstanceReader flowNodeInstanceReader;
  private final CamundaAuthenticationProvider authenticationProvider;

  public ServicesBasedAdapter(
      final ProcessInstanceServices processInstanceServices,
      final ResourceServices resourceServices,
      final ElementInstanceServices elementInstanceServices,
      final JobServices<?> jobServices,
      final IncidentServices incidentServices,
      final FlowNodeInstanceReader flowNodeInstanceReader,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.processInstanceServices = processInstanceServices;
    this.resourceServices = resourceServices;
    this.elementInstanceServices = elementInstanceServices;
    this.jobServices = jobServices;
    this.incidentServices = incidentServices;
    this.flowNodeInstanceReader = flowNodeInstanceReader;
    this.authenticationProvider = authenticationProvider;
  }

  @Override
  public void deleteResource(final long resourceKey, final String operationId) {
    executeCamundaServiceAnonymously(
        (authentication) ->
            withOperationReference(
                operationReference ->
                    resourceServices
                        .withAuthentication(authentication)
                        .deleteResource(
                            new ResourceDeletionRequest(resourceKey, operationReference)),
                operationId));
  }

  @Override
  public void migrateProcessInstance(
      final long processInstanceKey, final MigrationPlan migrationPlan, final String operationId) {
    executeCamundaServiceAnonymously(
        (authentication) ->
            withOperationReference(
                operationReference ->
                    processInstanceServices
                        .withAuthentication(authentication)
                        .migrateProcessInstance(
                            new ProcessInstanceMigrateRequest(
                                processInstanceKey,
                                migrationPlan.getTargetProcessDefinitionKey(),
                                migrationPlan.getMappingInstructions().stream()
                                    .map(
                                        instruction ->
                                            new ProcessInstanceMigrationMappingInstruction()
                                                .setSourceElementId(
                                                    instruction.getSourceElementId())
                                                .setTargetElementId(
                                                    instruction.getTargetElementId()))
                                    .toList(),
                                operationReference)),
                operationId));
  }

  @Override
  public void modifyProcessInstance(
      final long processInstanceKey,
      final List<Modification> modifications,
      final String operationId) {
    executeCamundaServiceAnonymously(
        (authentication) ->
            withOperationReference(
                operationReference ->
                    processInstanceServices
                        .withAuthentication(authentication)
                        .modifyProcessInstance(
                            createModifyRequest(
                                processInstanceKey, modifications, operationReference)),
                operationId));
  }

  @Override
  public void cancelProcessInstance(final long processInstanceKey, final String operationId) {
    executeCamundaServiceAnonymously(
        (authentication) ->
            withOperationReference(
                operationReference ->
                    processInstanceServices
                        .withAuthentication(authentication)
                        .cancelProcessInstance(
                            new ProcessInstanceCancelRequest(
                                processInstanceKey, operationReference)),
                operationId));
  }

  @Override
  public void updateJobRetries(final long jobKey, final int retries, final String operationId) {
    executeCamundaServiceAnonymously(
        (authentication) ->
            withOperationReference(
                operationReference ->
                    jobServices
                        .withAuthentication(authentication)
                        .updateJob(
                            jobKey, operationReference, new UpdateJobChangeset(retries, null)),
                operationId));
  }

  @Override
  public void resolveIncident(final long incidentKey, final String operationId) {
    executeCamundaServiceAnonymously(
        (authentication) ->
            withOperationReference(
                operationReference ->
                    incidentServices
                        .withAuthentication(authentication)
                        .resolveIncident(incidentKey, operationReference),
                operationId));
  }

  @Override
  public long setVariables(
      final long scopeKey,
      final Map<String, Object> variables,
      final boolean local,
      final String operationId) {
    final var variableDocumentRecord =
        executeCamundaServiceAnonymously(
            (authentication) ->
                withOperationReference(
                    operationReference ->
                        elementInstanceServices
                            .withAuthentication(authentication)
                            .setVariables(
                                new SetVariablesRequest(
                                    scopeKey, variables, local, operationReference)),
                    operationId));
    return variableDocumentRecord.getScopeKey();
  }

  /*
   * Code is based on the error mapping code in io.camunda.zeebe.gateway.rest.RestErrorMapper.
   */
  @Override
  public boolean isExceptionRetriable(final Throwable error) {
    if (error == null) {
      return false;
    }
    return switch (error) {
      case final CompletionException ce -> isExceptionRetriable(ce.getCause());
      case final ExecutionException ee -> isExceptionRetriable(ee.getCause());
      case final ServiceException cse -> isServiceExceptionRetriable(cse);
      default -> false;
    };
  }

  private boolean isServiceExceptionRetriable(final ServiceException error) {
    return switch (error.getStatus()) {
      case RESOURCE_EXHAUSTED, UNAVAILABLE, DEADLINE_EXCEEDED -> true;
      default -> false;
    };
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

  private <T> T executeCamundaServiceAnonymously(
      final Function<CamundaAuthentication, CompletableFuture<T>> method) {
    return executeCamundaService(
        method, authenticationProvider.getAnonymousCamundaAuthentication());
  }

  private <T> T executeCamundaService(
      final Function<CamundaAuthentication, CompletableFuture<T>> method,
      final CamundaAuthentication authentication) {
    return method.apply(authentication).join();
  }

  private ProcessInstanceModifyRequest createModifyRequest(
      final Long processInstanceKey,
      final List<Modification> modifications,
      final Long operationId) {

    final List<ProcessInstanceModificationActivateInstruction> activateInstructions =
        new ArrayList<>();
    final List<ProcessInstanceModificationTerminateInstruction> terminateInstructions =
        new ArrayList<>();

    modifications.stream()
        .forEach(
            modification -> {
              switch (modification.getModification()) {
                case ADD_TOKEN -> addActivationInstruction(modification, activateInstructions);
                case CANCEL_TOKEN ->
                    addTerminateInstruction(
                        processInstanceKey, modification, terminateInstructions);
                case MOVE_TOKEN -> {
                  final var newTokensCount =
                      calculateNewTokensCount(modification, processInstanceKey);
                  if (newTokensCount > 0) {
                    addTerminateInstruction(
                        processInstanceKey, modification, terminateInstructions);
                    addActivationInstruction(modification, activateInstructions);
                  } else {
                    LOGGER.info(
                        "Skipping MOVE_TOKEN processing for flowNode {} and process instance {} since newTokensCount is {}",
                        modification.getFromFlowNodeId(),
                        processInstanceKey,
                        newTokensCount);
                  }
                }
                default ->
                    LOGGER.warn(
                        "ModifyProcessInstanceHandler encountered a modification type that should have been filtered out: {}",
                        modification.getModification());
              }
            });

    return new ProcessInstanceModifyRequest(
        processInstanceKey,
        activateInstructions,
        Collections.emptyList(),
        terminateInstructions,
        operationId);
  }

  private void addActivationInstruction(
      final Modification modification,
      final List<ProcessInstanceModificationActivateInstruction> activateInstructions) {

    // create a new activation instruction
    final ProcessInstanceModificationActivateInstruction activateInstruction =
        new ProcessInstanceModificationActivateInstruction();
    final var flowNodeId = modification.getToFlowNodeId();
    activateInstruction.setElementId(flowNodeId);

    var ancestorKey = modification.getAncestorElementInstanceKey();
    ancestorKey = ancestorKey != null ? ancestorKey : -1L;
    activateInstruction.setAncestorScopeKey(ancestorKey);

    // add variables to the instruction
    // Map structure: [scopeId => List of variables [variableKey, variableValue]]
    final Map<String, List<Map<String, Object>>> flowNodesVariables =
        modification.variablesForAddToken();
    if (flowNodesVariables != null) {
      for (final Map.Entry<String, List<Map<String, Object>>> flowNodeVars :
          flowNodesVariables.entrySet()) {
        for (final Map<String, Object> vars : flowNodeVars.getValue()) {
          final var scopeId = flowNodeVars.getKey();
          final var variableInstruction =
              new ProcessInstanceModificationVariableInstruction()
                  .setElementId(scopeId)
                  .setVariables(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(vars)));
          activateInstruction.addVariableInstruction(variableInstruction);
        }
      }
    }

    // log
    LOGGER.debug("Add token to flowNodeId {} with variables: {}", flowNodeId, flowNodesVariables);
    activateInstructions.add(activateInstruction);
  }

  private void addTerminateInstruction(
      final Long processInstanceKey,
      final Modification modification,
      final List<ProcessInstanceModificationTerminateInstruction> terminateInstructions) {
    // Build the list of instances to cancel
    final var flowNodeInstanceKey = modification.getFromFlowNodeInstanceKey();
    final List<Long> flowNodeInstanceKeys;
    if (StringUtils.hasText(flowNodeInstanceKey)) {
      LOGGER.debug("Cancel token from flowNodeInstanceKey {} ", flowNodeInstanceKey);
      flowNodeInstanceKeys = List.of(Long.parseLong(flowNodeInstanceKey));
    } else {
      flowNodeInstanceKeys =
          flowNodeInstanceReader.getFlowNodeInstanceKeysByIdAndStates(
              processInstanceKey, modification.getFromFlowNodeId(), List.of(FlowNodeState.ACTIVE));
    }

    if (flowNodeInstanceKeys.isEmpty()) {
      throw new OperateRuntimeException(
          String.format(
              "Abort CANCEL_TOKEN: Can't find not finished flowNodeInstance keys for process instance %s and flowNode id %s",
              processInstanceKey, modification.getFromFlowNodeId()));
    }

    flowNodeInstanceKeys.stream()
        .forEach(
            instanceKey ->
                terminateInstructions.add(
                    new ProcessInstanceModificationTerminateInstruction()
                        .setElementInstanceKey(instanceKey)));
    LOGGER.debug("Cancel token from flowNodeInstanceKeys {} ", flowNodeInstanceKeys);
  }

  private int calculateNewTokensCount(
      final Modification modification, final Long processInstanceKey) {
    Integer newTokensCount = modification.getNewTokensCount();

    if (newTokensCount == null) {
      if (modification.getFromFlowNodeInstanceKey() != null) {
        // If a flow node instance key was specified, assume that flow node is valid. Zeebe
        // will correctly fail attempts to migrate off an invalid flow node
        newTokensCount = 1;
      } else if (modification.getFromFlowNodeId() != null) {
        newTokensCount =
            flowNodeInstanceReader
                .getFlowNodeInstanceKeysByIdAndStates(
                    processInstanceKey,
                    modification.getFromFlowNodeId(),
                    List.of(FlowNodeState.ACTIVE))
                .size();
      } else {
        LOGGER.warn(
            "MOVE_TOKEN attempted with no flowNodeId, flowNodeInstanceKey, or newTokenCount specified");
        newTokensCount = 0;
      }
    }

    LOGGER.info("MOVE_TOKEN has a newTokensCount value of {}", newTokensCount);
    return newTokensCount;
  }
}
