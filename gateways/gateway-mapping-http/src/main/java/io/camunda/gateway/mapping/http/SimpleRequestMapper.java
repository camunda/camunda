/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http;

import io.camunda.gateway.mapping.http.mapper.ProcessInstanceMapper;
import io.camunda.gateway.mapping.http.validator.ProcessInstanceRequestValidator;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstructionBase;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstructionById;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstructionByKey;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstructionByVersionTag;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationStartInstruction;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCreateRequest;
import io.camunda.zeebe.util.Either;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ProblemDetail;

public class SimpleRequestMapper {

  private static final ProcessInstanceMapper PROCESS_INSTANCE_MAPPER = new ProcessInstanceMapper();

  public static Either<ProblemDetail, ProcessInstanceCreateRequest> toCreateProcessInstance(
      final io.camunda.gateway.protocol.model.simple.ProcessInstanceCreationInstruction request,
      final boolean multiTenancyEnabled) {
    final var validationError =
        ProcessInstanceRequestValidator.validateSimpleCreateProcessInstanceRequest(request);
    if (validationError.isPresent()) {
      return Either.left(validationError.get());
    }

    final ProcessInstanceCreationInstructionBase instruction;
    if (StringUtils.isNotBlank(request.getProcessDefinitionId())) {
      instruction =
          request.getProcessDefinitionVersionTag() != null
              ? ProcessInstanceCreationInstructionByVersionTag.Builder.create()
                  .processDefinitionId(request.getProcessDefinitionId())
                  .processDefinitionVersionTag(request.getProcessDefinitionVersionTag())
                  .build()
              : ProcessInstanceCreationInstructionById.Builder.create()
                  .processDefinitionId(request.getProcessDefinitionId())
                  .processDefinitionVersion(request.getProcessDefinitionVersion())
                  .build();
    } else {
      instruction =
          ProcessInstanceCreationInstructionByKey.Builder.create()
              .processDefinitionKey(request.getProcessDefinitionKey())
              .build();
    }

    instruction.setVariables(request.getVariables());
    instruction.setTenantId(request.getTenantId());
    instruction.setOperationReference(request.getOperationReference());
    instruction.setStartInstructions(
        mapCreateProcessInstanceStartInstructions(request.getStartInstructions()));
    instruction.setRuntimeInstructions(
        mapCreateProcessInstanceRuntimeInstructions(request.getRuntimeInstructions()));
    instruction.setAwaitCompletion(request.getAwaitCompletion());
    instruction.setFetchVariables(request.getFetchVariables());
    instruction.setRequestTimeout(request.getRequestTimeout());
    instruction.setTags(request.getTags());
    instruction.setBusinessId(request.getBusinessId());

    return PROCESS_INSTANCE_MAPPER.toCreateProcessInstance(
        (ProcessInstanceCreationInstruction) instruction, multiTenancyEnabled);
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
            instruction ->
                ProcessInstanceCreationStartInstruction.Builder.create()
                    .elementId(instruction.getElementId())
                    .build())
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
            instruction ->
                io.camunda.gateway.protocol.model.ProcessInstanceCreationTerminateInstruction
                    .Builder.create()
                    .afterElementId(instruction.getAfterElementId())
                    .type(instruction.getType())
                    .build())
        .toList();
  }
}
