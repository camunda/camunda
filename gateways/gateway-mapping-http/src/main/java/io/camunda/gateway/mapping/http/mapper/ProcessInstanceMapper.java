/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.mapper;

import static io.camunda.gateway.mapping.http.RequestMapper.getIntOrDefault;
import static io.camunda.gateway.mapping.http.RequestMapper.getKeyOrDefault;
import static io.camunda.gateway.mapping.http.RequestMapper.getMapOrEmpty;
import static io.camunda.gateway.mapping.http.RequestMapper.getStringOrEmpty;
import static io.camunda.gateway.mapping.http.util.KeyUtil.keyToLong;
import static io.camunda.gateway.mapping.http.validator.MultiTenancyValidator.validateTenantId;
import static io.camunda.gateway.mapping.http.validator.ProcessInstanceRequestValidator.validateAssignProcessInstanceBusinessIdRequest;
import static io.camunda.gateway.mapping.http.validator.ProcessInstanceRequestValidator.validateCancelProcessInstanceRequest;
import static io.camunda.gateway.mapping.http.validator.ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest;
import static io.camunda.gateway.mapping.http.validator.ProcessInstanceRequestValidator.validateMigrateProcessInstanceBatchOperationRequest;
import static io.camunda.gateway.mapping.http.validator.ProcessInstanceRequestValidator.validateMigrateProcessInstanceRequest;
import static io.camunda.gateway.mapping.http.validator.ProcessInstanceRequestValidator.validateModifyProcessInstanceBatchOperationRequest;
import static io.camunda.gateway.mapping.http.validator.ProcessInstanceRequestValidator.validateModifyProcessInstanceRequest;
import static io.camunda.gateway.mapping.http.validator.ProcessInstanceRequestValidator.validateResumeProcessInstanceRequest;
import static io.camunda.gateway.mapping.http.validator.ProcessInstanceRequestValidator.validateSuspendProcessInstanceRequest;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.createProblemDetail;

import io.camunda.gateway.mapping.http.GatewayErrorMapper;
import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryFilterMapper;
import io.camunda.gateway.protocol.model.CancelProcessInstanceRequest;
import io.camunda.gateway.protocol.model.DirectAncestorKeyInstruction;
import io.camunda.gateway.protocol.model.ModifyProcessInstanceVariableInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceBusinessIdAssignmentInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstructionById;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstructionByKey;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationTerminateInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceMigrationBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceMigrationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationMoveBatchOperationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationTerminateByIdInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationTerminateByKeyInstruction;
import io.camunda.gateway.protocol.model.ResumeProcessInstanceRequest;
import io.camunda.gateway.protocol.model.SourceElementIdInstruction;
import io.camunda.gateway.protocol.model.SourceElementInstanceKeyInstruction;
import io.camunda.gateway.protocol.model.SuspendProcessInstanceRequest;
import io.camunda.gateway.protocol.model.UseSourceParentKeyInstruction;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.service.ProcessInstanceServices.AssignProcessInstanceBusinessIdRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCancelRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCreateRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceMigrateBatchOperationRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceMigrateRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceModifyBatchOperationRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceModifyRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceResumeRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceSuspendRequest;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRuntimeInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationStartInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationMappingInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationActivateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationMoveInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationTerminateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationVariableInstruction;
import io.camunda.zeebe.protocol.record.value.RuntimeInstructionType;
import io.camunda.zeebe.util.Either;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public class ProcessInstanceMapper {

  public Either<ProblemDetail, ProcessInstanceCreateRequest> toCreateProcessInstance(
      final ProcessInstanceCreationInstruction request, final boolean multiTenancyEnabled) {
    return switch (request) {
      case final ProcessInstanceCreationInstructionById req ->
          toCreateProcessInstance(req, multiTenancyEnabled);
      case final ProcessInstanceCreationInstructionByKey req ->
          toCreateProcessInstance(req, multiTenancyEnabled);
      default ->
          Either.left(
              GatewayErrorMapper.createProblemDetail(
                  HttpStatus.BAD_REQUEST,
                  "Unsupported process instance creation instruction type: "
                      + request.getClass().getSimpleName(),
                  "Only process instance creation by id or key is supported."));
    };
  }

  public Either<ProblemDetail, ProcessInstanceCreateRequest> toCreateProcessInstance(
      final ProcessInstanceCreationInstructionById request, final boolean multiTenancyEnabled) {
    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(request.getTenantId(), multiTenancyEnabled, "Create Process Instance")
            .flatMap(
                tenant ->
                    validateCreateProcessInstanceRequest(request)
                        .map(Either::<ProblemDetail, String>left)
                        .orElseGet(() -> Either.right(tenant)));

    return validationResponse.map(
        tenantId ->
            new ProcessInstanceCreateRequest(
                -1L,
                getStringOrEmpty(
                    request, ProcessInstanceCreationInstructionById::getProcessDefinitionId),
                getIntOrDefault(
                    request,
                    ProcessInstanceCreationInstructionById::getProcessDefinitionVersion,
                    -1),
                getMapOrEmpty(request, ProcessInstanceCreationInstructionById::getVariables),
                tenantId,
                request.getAwaitCompletion(),
                request.getRequestTimeout(),
                request.getOperationReference(),
                request.getStartInstructions().stream()
                    .map(
                        instruction ->
                            new ProcessInstanceCreationStartInstruction()
                                .setElementId(instruction.getElementId()))
                    .toList(),
                request.getRuntimeInstructions().stream()
                    .map(
                        instruction -> {
                          final var instructionCasted =
                              (ProcessInstanceCreationTerminateInstruction) instruction;
                          return new ProcessInstanceCreationRuntimeInstruction()
                              .setType(RuntimeInstructionType.TERMINATE_PROCESS_INSTANCE)
                              .setAfterElementId(instructionCasted.getAfterElementId());
                        })
                    .toList(),
                request.getFetchVariables(),
                request.getTags(),
                request.getBusinessId()));
  }

  public Either<ProblemDetail, ProcessInstanceCreateRequest> toCreateProcessInstance(
      final ProcessInstanceCreationInstructionByKey request, final boolean multiTenancyEnabled) {
    final Either<ProblemDetail, String> validationResponse =
        validateTenantId(request.getTenantId(), multiTenancyEnabled, "Create Process Instance")
            .flatMap(
                tenant ->
                    validateCreateProcessInstanceRequest(request)
                        .map(Either::<ProblemDetail, String>left)
                        .orElseGet(() -> Either.right(tenant)));

    return validationResponse.map(
        tenantId ->
            new ProcessInstanceCreateRequest(
                getKeyOrDefault(
                    request, ProcessInstanceCreationInstructionByKey::getProcessDefinitionKey, -1L),
                "",
                -1,
                getMapOrEmpty(request, ProcessInstanceCreationInstructionByKey::getVariables),
                tenantId,
                request.getAwaitCompletion(),
                request.getRequestTimeout(),
                request.getOperationReference(),
                request.getStartInstructions().stream()
                    .map(
                        instruction ->
                            new ProcessInstanceCreationStartInstruction()
                                .setElementId(instruction.getElementId()))
                    .toList(),
                request.getRuntimeInstructions().stream()
                    .map(
                        instruction -> {
                          final var instructionCasted =
                              (ProcessInstanceCreationTerminateInstruction) instruction;
                          return new ProcessInstanceCreationRuntimeInstruction()
                              .setType(RuntimeInstructionType.TERMINATE_PROCESS_INSTANCE)
                              .setAfterElementId(instructionCasted.getAfterElementId());
                        })
                    .toList(),
                request.getFetchVariables(),
                request.getTags(),
                request.getBusinessId()));
  }

  public Either<ProblemDetail, ProcessInstanceSuspendRequest> toSuspendProcessInstance(
      final long processInstanceKey, final SuspendProcessInstanceRequest request) {
    final Long operationReference = request != null ? request.getOperationReference() : null;
    return RequestMapper.getResult(
        validateSuspendProcessInstanceRequest(request),
        () -> new ProcessInstanceSuspendRequest(processInstanceKey, operationReference));
  }

  public Either<ProblemDetail, ProcessInstanceResumeRequest> toResumeProcessInstance(
      final long processInstanceKey, final ResumeProcessInstanceRequest request) {
    final Long operationReference = request != null ? request.getOperationReference() : null;
    return RequestMapper.getResult(
        validateResumeProcessInstanceRequest(request),
        () -> new ProcessInstanceResumeRequest(processInstanceKey, operationReference));
  }

  public Either<ProblemDetail, ProcessInstanceCancelRequest> toCancelProcessInstance(
      final long processInstanceKey, final CancelProcessInstanceRequest request) {
    final Long operationReference = request != null ? request.getOperationReference() : null;
    return RequestMapper.getResult(
        validateCancelProcessInstanceRequest(request),
        () -> new ProcessInstanceCancelRequest(processInstanceKey, operationReference));
  }

  public Either<ProblemDetail, ProcessInstanceMigrateRequest> toMigrateProcessInstance(
      final long processInstanceKey, final ProcessInstanceMigrationInstruction request) {
    return RequestMapper.getResult(
        validateMigrateProcessInstanceRequest(request),
        () ->
            new ProcessInstanceMigrateRequest(
                processInstanceKey,
                keyToLong(request.getTargetProcessDefinitionKey()),
                request.getMappingInstructions().stream()
                    .map(
                        instruction ->
                            new ProcessInstanceMigrationMappingInstruction()
                                .setSourceElementId(instruction.getSourceElementId())
                                .setTargetElementId(instruction.getTargetElementId()))
                    .toList(),
                request.getOperationReference()));
  }

  public Either<ProblemDetail, AssignProcessInstanceBusinessIdRequest>
      toAssignProcessInstanceBusinessId(
          final long processInstanceKey,
          final ProcessInstanceBusinessIdAssignmentInstruction request) {
    return RequestMapper.getResult(
        validateAssignProcessInstanceBusinessIdRequest(request),
        () ->
            new AssignProcessInstanceBusinessIdRequest(
                processInstanceKey, request.getBusinessId()));
  }

  public Either<ProblemDetail, ProcessInstanceFilter> toRequiredProcessInstanceFilter(
      final io.camunda.gateway.protocol.model.ProcessInstanceFilter request) {

    final var filter = SearchQueryFilterMapper.toRequiredProcessInstanceFilter(request);
    if (filter.isLeft()) {
      return Either.left(createProblemDetail(filter.getLeft()).get());
    }

    return Either.right(filter.get());
  }

  public Either<ProblemDetail, ProcessInstanceMigrateBatchOperationRequest>
      toProcessInstanceMigrationBatchOperationRequest(
          final ProcessInstanceMigrationBatchOperationRequest request) {
    final var migrationPlan = request.getMigrationPlan();
    return RequestMapper.getResult(
        validateMigrateProcessInstanceBatchOperationRequest(request),
        () ->
            new ProcessInstanceMigrateBatchOperationRequest(
                toRequiredProcessInstanceFilter(request.getFilter()).get(),
                keyToLong(migrationPlan.getTargetProcessDefinitionKey()),
                migrationPlan.getMappingInstructions().stream()
                    .map(
                        instruction ->
                            new ProcessInstanceMigrationMappingInstruction()
                                .setSourceElementId(instruction.getSourceElementId())
                                .setTargetElementId(instruction.getTargetElementId()))
                    .toList()));
  }

  public Either<ProblemDetail, ProcessInstanceModifyRequest> toModifyProcessInstance(
      final long processInstanceKey, final ProcessInstanceModificationInstruction request) {
    return RequestMapper.getResult(
        validateModifyProcessInstanceRequest(request),
        () ->
            new ProcessInstanceModifyRequest(
                processInstanceKey,
                mapProcessInstanceModificationActivateInstruction(
                    request.getActivateInstructions()),
                mapProcessInstanceModificationMoveInstruction(request.getMoveInstructions()),
                request.getTerminateInstructions().stream()
                    .map(
                        instruction -> {
                          final var mappedInstruction =
                              new ProcessInstanceModificationTerminateInstruction();
                          if (instruction
                              instanceof
                              final ProcessInstanceModificationTerminateByKeyInstruction byKey) {
                            mappedInstruction.setElementInstanceKey(
                                keyToLong(byKey.getElementInstanceKey()));
                          } else {
                            mappedInstruction.setElementId(
                                ((ProcessInstanceModificationTerminateByIdInstruction) instruction)
                                    .getElementId());
                          }

                          return mappedInstruction;
                        })
                    .toList(),
                request.getOperationReference()));
  }

  public Either<ProblemDetail, ProcessInstanceModifyBatchOperationRequest>
      toProcessInstanceModifyBatchOperationRequest(
          final ProcessInstanceModificationBatchOperationRequest request) {
    return RequestMapper.getResult(
        validateModifyProcessInstanceBatchOperationRequest(request),
        () ->
            new ProcessInstanceModifyBatchOperationRequest(
                toRequiredProcessInstanceFilter(request.getFilter()).get(),
                mapProcessInstanceModificationMoveBatchInstruction(request.getMoveInstructions())));
  }

  private static List<ProcessInstanceModificationActivateInstruction>
      mapProcessInstanceModificationActivateInstruction(
          final List<
                  io.camunda.gateway.protocol.model.ProcessInstanceModificationActivateInstruction>
              instructions) {
    return instructions.stream()
        .map(
            instruction -> {
              final var mappedInstruction = new ProcessInstanceModificationActivateInstruction();
              mappedInstruction
                  .setElementId(instruction.getElementId())
                  .setAncestorScopeKey(getAncestorKey(instruction.getAncestorElementInstanceKey()));
              instruction.getVariableInstructions().stream()
                  .map(ProcessInstanceMapper::mapVariableInstruction)
                  .forEach(mappedInstruction::addVariableInstruction);
              return mappedInstruction;
            })
        .toList();
  }

  private static ProcessInstanceModificationVariableInstruction mapVariableInstruction(
      final ModifyProcessInstanceVariableInstruction variable) {
    return new ProcessInstanceModificationVariableInstruction()
        .setElementId(variable.getScopeId())
        .setVariables(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(variable.getVariables())));
  }

  private static Long getAncestorKey(final @Nullable String ancestorElementInstanceKey) {
    if (ancestorElementInstanceKey == null) {
      return -1L;
    }
    return keyToLong(ancestorElementInstanceKey);
  }

  private static List<ProcessInstanceModificationMoveInstruction>
      mapProcessInstanceModificationMoveInstruction(
          final List<io.camunda.gateway.protocol.model.ProcessInstanceModificationMoveInstruction>
              instructions) {
    return instructions.stream()
        .map(
            instruction -> {
              final var mappedInstruction =
                  new ProcessInstanceModificationMoveInstruction()
                      .setTargetElementId(instruction.getTargetElementId());

              switch (instruction.getSourceElementInstruction()) {
                case final SourceElementIdInstruction byId ->
                    mappedInstruction.setSourceElementId(byId.getSourceElementId());
                case final SourceElementInstanceKeyInstruction byKey ->
                    mappedInstruction.setSourceElementInstanceKey(
                        keyToLong(byKey.getSourceElementInstanceKey()));
                default ->
                    throw new IllegalStateException(
                        "Unexpected value: " + instruction.getSourceElementInstruction());
              }

              switch (instruction.getAncestorScopeInstruction()) {
                case null -> mappedInstruction.setAncestorScopeKey(-1);
                case final DirectAncestorKeyInstruction direct ->
                    mappedInstruction.setAncestorScopeKey(
                        getAncestorKey(direct.getAncestorElementInstanceKey()));
                case final UseSourceParentKeyInstruction sourceParent ->
                    mappedInstruction.setUseSourceParentKeyAsAncestorScopeKey(true);
                default -> mappedInstruction.setInferAncestorScopeFromSourceHierarchy(true);
              }
              instruction.getVariableInstructions().stream()
                  .map(ProcessInstanceMapper::mapVariableInstruction)
                  .forEach(mappedInstruction::addVariableInstruction);
              return mappedInstruction;
            })
        .toList();
  }

  private static List<ProcessInstanceModificationMoveInstruction>
      mapProcessInstanceModificationMoveBatchInstruction(
          final List<ProcessInstanceModificationMoveBatchOperationInstruction> instructions) {
    return instructions.stream()
        .map(
            instruction ->
                new ProcessInstanceModificationMoveInstruction()
                    .setSourceElementId(instruction.getSourceElementId())
                    .setTargetElementId(instruction.getTargetElementId())
                    .setInferAncestorScopeFromSourceHierarchy(true))
        .toList();
  }
}
