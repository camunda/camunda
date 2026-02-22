/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http;

import io.camunda.gateway.mapping.http.validator.ProcessInstanceRequestValidator;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstructionById;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstructionByKey;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationStartInstruction;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCreateRequest;
import io.camunda.zeebe.util.Either;
import java.util.List;
import org.springframework.http.ProblemDetail;

public class SimpleRequestMapper {

  public static Either<ProblemDetail, ProcessInstanceCreateRequest> toCreateProcessInstance(
      final io.camunda.gateway.protocol.model.simple.ProcessInstanceCreationInstruction request,
      final boolean multiTenancyEnabled) {
    final var validationError =
        ProcessInstanceRequestValidator.validateSimpleCreateProcessInstanceRequest(request);
    if (validationError.isPresent()) {
      return Either.left(validationError.get());
    }

    if (request.getProcessDefinitionId() != null && !request.getProcessDefinitionId().isBlank()) {
      return RequestMapper.toCreateProcessInstance(
          (ProcessInstanceCreationInstruction)
              new ProcessInstanceCreationInstructionById()
                  .processDefinitionId(request.getProcessDefinitionId())
                  .processDefinitionVersion(request.getProcessDefinitionVersion())
                  .variables(request.getVariables())
                  .tenantId(request.getTenantId())
                  .operationReference(request.getOperationReference())
                  .startInstructions(
                      mapCreateProcessInstanceStartInstructions(request.getStartInstructions()))
                  .runtimeInstructions(
                      mapCreateProcessInstanceRuntimeInstructions(request.getRuntimeInstructions()))
                  .awaitCompletion(request.getAwaitCompletion())
                  .fetchVariables(request.getFetchVariables())
                  .requestTimeout(request.getRequestTimeout())
                  .tags(request.getTags())
                  .businessId(request.getBusinessId()),
          multiTenancyEnabled);
    }

    return RequestMapper.toCreateProcessInstance(
        (ProcessInstanceCreationInstruction)
            new ProcessInstanceCreationInstructionByKey()
                .processDefinitionKey(request.getProcessDefinitionKey())
                .variables(request.getVariables())
                .startInstructions(
                    mapCreateProcessInstanceStartInstructions(request.getStartInstructions()))
                .runtimeInstructions(
                    mapCreateProcessInstanceRuntimeInstructions(request.getRuntimeInstructions()))
                .tenantId(request.getTenantId())
                .operationReference(request.getOperationReference())
                .awaitCompletion(request.getAwaitCompletion())
                .requestTimeout(request.getRequestTimeout())
                .fetchVariables(request.getFetchVariables())
                .tags(request.getTags())
                .businessId(request.getBusinessId()),
        multiTenancyEnabled);
  }

  private static List<ProcessInstanceCreationStartInstruction>
      mapCreateProcessInstanceStartInstructions(
          final List<
                  io.camunda.gateway.protocol.model.simple.ProcessInstanceCreationStartInstruction>
              instructions) {
    if (instructions == null) {
      return List.of();
    }
    return instructions.stream()
        .map(
            instruction -> {
              final var mapped =
                  new io.camunda.gateway.protocol.model.ProcessInstanceCreationStartInstruction();
              mapped.setElementId(instruction.getElementId());
              return mapped;
            })
        .toList();
  }

  private static List<io.camunda.gateway.protocol.model.ProcessInstanceCreationTerminateInstruction>
      mapCreateProcessInstanceRuntimeInstructions(
          final List<
                  io.camunda.gateway.protocol.model.simple
                      .ProcessInstanceCreationTerminateInstruction>
              instructions) {
    if (instructions == null) {
      return List.of();
    }
    return instructions.stream()
        .map(
            instruction -> {
              final var mapped =
                  new io.camunda.gateway.protocol.model
                      .ProcessInstanceCreationTerminateInstruction();
              mapped.setType(instruction.getType());
              mapped.setAfterElementId(instruction.getAfterElementId());
              return mapped;
            })
        .toList();
  }
}
