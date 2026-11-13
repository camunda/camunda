/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.mapper;

import static io.camunda.gateway.mapping.http.RequestMapper.getResult;
import static io.camunda.gateway.mapping.http.util.KeyUtil.keyToLong;
import static io.camunda.gateway.mapping.http.util.KeyUtil.keyToString;
import static io.camunda.gateway.mapping.http.validator.MultiTenancyValidator.validateTenantId;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validateKeyFormat;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validateProcessDefinitionId;

import io.camunda.gateway.mapping.http.GatewayErrorMapper;
import io.camunda.gateway.protocol.model.ProcessDefinitionResumptionInstruction;
import io.camunda.gateway.protocol.model.ProcessDefinitionResumptionInstructionById;
import io.camunda.gateway.protocol.model.ProcessDefinitionResumptionInstructionByKey;
import io.camunda.gateway.protocol.model.ProcessDefinitionResumptionResult;
import io.camunda.gateway.protocol.model.ProcessDefinitionResumptionVersionResult;
import io.camunda.gateway.protocol.model.ProcessDefinitionSuspensionInstruction;
import io.camunda.gateway.protocol.model.ProcessDefinitionSuspensionInstructionById;
import io.camunda.gateway.protocol.model.ProcessDefinitionSuspensionInstructionByKey;
import io.camunda.gateway.protocol.model.ProcessDefinitionSuspensionResult;
import io.camunda.gateway.protocol.model.ProcessDefinitionSuspensionVersionResult;
import io.camunda.service.ProcessDefinitionServices.ProcessDefinitionStateChangeRequest;
import io.camunda.zeebe.protocol.impl.record.value.processdefinition.ProcessDefinitionSuspensionRecord;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionResolvedVersionValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.Either;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public class ProcessDefinitionMapper {

  private static final String SUSPEND_COMMAND = "Suspend Process Definition";
  private static final String RESUME_COMMAND = "Resume Process Definition";

  public Either<ProblemDetail, ProcessDefinitionStateChangeRequest> toSuspendProcessDefinitions(
      final ProcessDefinitionSuspensionInstruction request, final boolean multiTenancyEnabled) {
    return switch (request) {
      case final ProcessDefinitionSuspensionInstructionByKey byKey ->
          toByKey(byKey.getProcessDefinitionKey(), byKey.getIncludeProcessInstances());
      case final ProcessDefinitionSuspensionInstructionById byId ->
          toById(
              byId.getProcessDefinitionId(),
              byId.getProcessDefinitionVersion(),
              byId.getTenantId(),
              byId.getIncludeProcessInstances(),
              multiTenancyEnabled,
              SUSPEND_COMMAND);
      default -> unsupported("suspension", request);
    };
  }

  public Either<ProblemDetail, ProcessDefinitionStateChangeRequest> toResumeProcessDefinitions(
      final ProcessDefinitionResumptionInstruction request, final boolean multiTenancyEnabled) {
    return switch (request) {
      case final ProcessDefinitionResumptionInstructionByKey byKey ->
          toByKey(byKey.getProcessDefinitionKey(), byKey.getIncludeProcessInstances());
      case final ProcessDefinitionResumptionInstructionById byId ->
          toById(
              byId.getProcessDefinitionId(),
              byId.getProcessDefinitionVersion(),
              byId.getTenantId(),
              byId.getIncludeProcessInstances(),
              multiTenancyEnabled,
              RESUME_COMMAND);
      default -> unsupported("resumption", request);
    };
  }

  public ProcessDefinitionSuspensionResult toSuspensionResult(
      final ProcessDefinitionSuspensionRecord record) {
    return ProcessDefinitionSuspensionResult.Builder.create()
        .processDefinitions(
            record.getResolvedVersions().stream().map(this::toSuspensionVersionResult).toList())
        .batchOperationKey(toBatchOperationKey(record))
        .build();
  }

  public ProcessDefinitionResumptionResult toResumptionResult(
      final ProcessDefinitionSuspensionRecord record) {
    return ProcessDefinitionResumptionResult.Builder.create()
        .processDefinitions(
            record.getResolvedVersions().stream().map(this::toResumptionVersionResult).toList())
        .batchOperationKey(toBatchOperationKey(record))
        .build();
  }

  private ProcessDefinitionSuspensionVersionResult toSuspensionVersionResult(
      final ProcessDefinitionSuspensionResolvedVersionValue version) {
    return ProcessDefinitionSuspensionVersionResult.Builder.create()
        .processDefinitionKey(keyToString(version.getProcessDefinitionKey()))
        .version(version.getVersion())
        .outcome(
            ProcessDefinitionSuspensionVersionResult.OutcomeEnum.fromValue(
                version.getOutcome().name()))
        .build();
  }

  private ProcessDefinitionResumptionVersionResult toResumptionVersionResult(
      final ProcessDefinitionSuspensionResolvedVersionValue version) {
    return ProcessDefinitionResumptionVersionResult.Builder.create()
        .processDefinitionKey(keyToString(version.getProcessDefinitionKey()))
        .version(version.getVersion())
        .outcome(
            ProcessDefinitionResumptionVersionResult.OutcomeEnum.fromValue(
                version.getOutcome().name()))
        .build();
  }

  private static @Nullable String toBatchOperationKey(
      final ProcessDefinitionSuspensionRecord record) {
    final long batchOperationKey = record.getBatchOperationKey();
    return batchOperationKey < 0 ? null : keyToString(batchOperationKey);
  }

  private static Either<ProblemDetail, ProcessDefinitionStateChangeRequest> toByKey(
      final String processDefinitionKey, final @Nullable Boolean includeProcessInstances) {
    return getResult(
        validate(
            violations ->
                validateKeyFormat(processDefinitionKey, "processDefinitionKey", violations)),
        () ->
            new ProcessDefinitionStateChangeRequest(
                keyToLong(processDefinitionKey),
                "",
                -1,
                TenantOwned.DEFAULT_TENANT_IDENTIFIER,
                Boolean.TRUE.equals(includeProcessInstances)));
  }

  private static Either<ProblemDetail, ProcessDefinitionStateChangeRequest> toById(
      final String processDefinitionId,
      final @Nullable Integer version,
      final @Nullable String tenantId,
      final @Nullable Boolean includeProcessInstances,
      final boolean multiTenancyEnabled,
      final String commandName) {
    return validateById(processDefinitionId, version)
        .map(Either::<ProblemDetail, String>left)
        .orElseGet(() -> validateTenantId(tenantId, multiTenancyEnabled, commandName))
        .map(
            tenant ->
                new ProcessDefinitionStateChangeRequest(
                    -1L,
                    processDefinitionId,
                    version == null ? -1 : version,
                    tenant,
                    Boolean.TRUE.equals(includeProcessInstances)));
  }

  private static Optional<ProblemDetail> validateById(
      final String processDefinitionId, final @Nullable Integer version) {
    return validate(
        violations -> {
          validateProcessDefinitionId(processDefinitionId, violations);
          if (version != null && version < 1) {
            violations.add(
                "The value for processDefinitionVersion is '%d' but must be a positive integer."
                    .formatted(version));
          }
        });
  }

  private static <T> Either<ProblemDetail, T> unsupported(final String kind, final Object request) {
    return Either.left(
        GatewayErrorMapper.createProblemDetail(
            HttpStatus.BAD_REQUEST,
            "Unsupported process definition %s instruction type: %s"
                .formatted(kind, request.getClass().getSimpleName()),
            "Only process definition %s by key or by ID is supported.".formatted(kind)));
  }
}
