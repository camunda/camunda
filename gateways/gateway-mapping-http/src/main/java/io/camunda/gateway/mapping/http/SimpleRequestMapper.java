/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http;

import static io.camunda.gateway.mapping.http.validator.MultiTenancyValidator.validateTenantId;

import io.camunda.gateway.mapping.http.validator.ProcessInstanceRequestValidator;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstructionById;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstructionByKey;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationStartInstruction;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCreateRequest;
import io.camunda.service.ResourceServices.DeployResourcesRequest;
import io.camunda.zeebe.util.Either;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
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
                  .tags(request.getTags()),
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
                .tags(request.getTags()),
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

  public static Either<ProblemDetail, DeployResourcesRequest> toDeployResourceRequest(
      final Map<String, String> resources,
      final String tenantId,
      final boolean multiTenancyEnabled) {
    try {
      if (resources == null || resources.isEmpty()) {
        return Either.left(
            ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Resources must not be empty."));
      }

      final Either<ProblemDetail, String> validationResponse =
          validateTenantId(tenantId, multiTenancyEnabled, "Deploy Resources");
      if (validationResponse.isLeft()) {
        return Either.left(validationResponse.getLeft());
      }

      final Map<String, byte[]> resourceMap = new HashMap<>();
      for (final Map.Entry<String, String> entry : resources.entrySet()) {
        final String resourceName = entry.getKey();
        final String base64Content = entry.getValue();

        if (resourceName == null || resourceName.isBlank()) {
          return Either.left(
              ProblemDetail.forStatusAndDetail(
                  HttpStatus.BAD_REQUEST, "Resource name must not be blank."));
        }

        if (base64Content == null || base64Content.isBlank()) {
          return Either.left(
              ProblemDetail.forStatusAndDetail(
                  HttpStatus.BAD_REQUEST,
                  "Resource content must not be blank for resource: " + resourceName));
        }

        try {
          final byte[] decodedContent = Base64.getDecoder().decode(base64Content);
          resourceMap.put(resourceName, decodedContent);
        } catch (final IllegalArgumentException e) {
          return Either.left(
              ProblemDetail.forStatusAndDetail(
                  HttpStatus.BAD_REQUEST, "Invalid base64 encoding for resource: " + resourceName));
        }
      }

      return Either.right(new DeployResourcesRequest(resourceMap, validationResponse.get()));
    } catch (final Exception e) {
      return Either.left(
          ProblemDetail.forStatusAndDetail(
              HttpStatus.INTERNAL_SERVER_ERROR, "Failed to process resources: " + e.getMessage()));
    }
  }
}
