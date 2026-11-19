/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processdefinition;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor.ProcessingError;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.processdefinition.ProcessDefinitionSuspensionRecord;
import io.camunda.zeebe.protocol.impl.record.value.processdefinition.ProcessDefinitionSuspensionResolvedVersion;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionOutcome;
import io.camunda.zeebe.stream.api.records.ExceededBatchRecordSizeException;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Lookups, validation and rejections shared by {@link ProcessDefinitionSuspensionSuspendProcessor}
 * and {@link ProcessDefinitionSuspensionResumeProcessor}. The {@code verb} parameters ("suspend",
 * "resume") only go into rejection reasons.
 */
@NullMarked
final class ProcessDefinitionSuspensionBehavior {

  static final PermissionType PERMISSION = PermissionType.SUSPEND_PROCESS_DEFINITION;

  private static final String NO_IDENTIFIER_MESSAGE =
      "Expected to %s a process definition by key or by BPMN process ID, but none given";
  private static final String BOTH_IDENTIFIERS_MESSAGE =
      "Expected to %s a process definition by key or by BPMN process ID, but both were given: key '%d', BPMN process ID '%s'";
  private static final String KEY_AND_VERSION_MESSAGE =
      "Expected to %s a process definition by key or by BPMN process ID and version, but key '%d' was given with version '%d'";
  private static final String NOT_FOUND_BY_KEY_MESSAGE =
      "Expected to %s process definition with key '%d', but no such process definition was found";
  private static final String NOT_FOUND_BY_ID_MESSAGE =
      "Expected to %s process definition with BPMN process ID '%s', but no such process definition was found";
  private static final String NOT_FOUND_BY_ID_AND_VERSION_MESSAGE =
      "Expected to %s process definition with BPMN process ID '%s' and version '%d', but no such process definition was found";
  private static final String DRAINING_MESSAGE =
      "Expected to %s process definition with BPMN process ID '%s' and version %d (key %d), but it is being deleted";
  private static final String TOO_LARGE_MESSAGE =
      "Expected to %s process definition with %s, but the resulting records exceed the maximum batch size. Nothing was changed; %s the versions one at a time instead";

  private final ProcessState processState;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  ProcessDefinitionSuspensionBehavior(
      final ProcessingState processingState, final Writers writers) {
    processState = processingState.getProcessState();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
  }

  /**
   * Rejects a command that names no process definition, or names it both by key and by BPMN process
   * ID or version. The REST mapping sends version {@code -1} for requests by key.
   */
  Either<Rejection, ProcessDefinitionSuspensionRecord> validateIdentifiers(
      final ProcessDefinitionSuspensionRecord value, final String verb) {
    final long key = value.getProcessDefinitionKey();
    final String bpmnProcessId = value.getBpmnProcessId();
    if (key > 0 && !bpmnProcessId.isEmpty()) {
      return invalidArgument(BOTH_IDENTIFIERS_MESSAGE.formatted(verb, key, bpmnProcessId));
    }
    if (key > 0 && value.getVersion() >= 0) {
      return invalidArgument(KEY_AND_VERSION_MESSAGE.formatted(verb, key, value.getVersion()));
    }
    if (key <= 0 && bpmnProcessId.isEmpty()) {
      return invalidArgument(NO_IDENTIFIER_MESSAGE.formatted(verb));
    }
    return Either.right(value);
  }

  static boolean isByKey(final ProcessDefinitionSuspensionRecord value) {
    return value.getProcessDefinitionKey() > 0;
  }

  /**
   * A caller without access to a definition looked up by key gets this same rejection, so they
   * can't tell an unauthorized key from an unknown one.
   */
  static Rejection notFoundByKey(final ProcessDefinitionSuspensionRecord value, final String verb) {
    return new Rejection(
        RejectionType.NOT_FOUND,
        NOT_FOUND_BY_KEY_MESSAGE.formatted(verb, value.getProcessDefinitionKey()));
  }

  static Rejection notFoundById(final ProcessDefinitionSuspensionRecord value, final String verb) {
    return value.getVersion() >= 0
        ? new Rejection(
            RejectionType.NOT_FOUND,
            NOT_FOUND_BY_ID_AND_VERSION_MESSAGE.formatted(
                verb, value.getBpmnProcessId(), value.getVersion()))
        : new Rejection(
            RejectionType.NOT_FOUND,
            NOT_FOUND_BY_ID_MESSAGE.formatted(verb, value.getBpmnProcessId()));
  }

  static Rejection forbidden() {
    return AuthorizationRejectionMapper.forbidden(
        PERMISSION, AuthorizationResourceType.PROCESS_DEFINITION);
  }

  static RequiredAuthorization<?> requiredAuthorization(final String bpmnProcessId) {
    return RequiredAuthorization.of(
        b ->
            b.resourceType(
                    AuthzModelMapper.fromProtocol(AuthorizationResourceType.PROCESS_DEFINITION))
                .permissionType(AuthzModelMapper.fromProtocol(PERMISSION))
                .resourceId(bpmnProcessId));
  }

  Either<Rejection, DeployedProcess> findByKey(
      final ProcessDefinitionSuspensionRecord value, final String verb) {
    final @Nullable DeployedProcess process =
        processState.getProcessByKeyAndTenant(value.getProcessDefinitionKey(), value.getTenantId());
    return process == null ? Either.left(notFoundByKey(value, verb)) : Either.right(process);
  }

  /**
   * One version by BPMN process ID and version, or all versions by BPMN process ID only. Doesn't
   * check authorization; the caller does that before the lookup.
   */
  Either<Rejection, List<DeployedProcess>> findById(
      final ProcessDefinitionSuspensionRecord value, final String verb) {
    final var bpmnProcessId = wrapString(value.getBpmnProcessId());
    final String tenantId = value.getTenantId();
    if (value.getVersion() >= 0) {
      final @Nullable DeployedProcess process =
          processState.getProcessByProcessIdAndVersion(bpmnProcessId, value.getVersion(), tenantId);
      return process == null
          ? Either.left(notFoundById(value, verb))
          : rejectIfDraining(process, verb).map(List::of);
    }

    final List<DeployedProcess> processes = new ArrayList<>();
    for (final long version :
        processState.getKnownProcessVersions(value.getBpmnProcessId(), tenantId)) {
      final @Nullable DeployedProcess process =
          processState.getProcessByProcessIdAndVersion(bpmnProcessId, (int) version, tenantId);
      if (process != null) {
        processes.add(process);
      }
    }
    return processes.isEmpty() ? Either.left(notFoundById(value, verb)) : Either.right(processes);
  }

  /**
   * A draining version targeted directly is rejected, like process instance creation and migration
   * do. When resolving all versions, it gets the {@code DRAINING} outcome instead.
   */
  Either<Rejection, DeployedProcess> rejectIfDraining(
      final DeployedProcess process, final String verb) {
    if (process.isDraining()) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_STATE,
              DRAINING_MESSAGE.formatted(
                  verb,
                  bufferAsString(process.getBpmnProcessId()),
                  process.getVersion(),
                  process.getKey())));
    }
    return Either.right(process);
  }

  boolean isSuspended(final DeployedProcess process) {
    return processState.isSuspended(process.getKey());
  }

  static ProcessDefinitionSuspensionResolvedVersion resolvedVersion(
      final DeployedProcess process, final ProcessDefinitionSuspensionOutcome outcome) {
    return new ProcessDefinitionSuspensionResolvedVersion()
        .setProcessDefinitionKey(process.getKey())
        .setVersion(process.getVersion())
        .setOutcome(outcome);
  }

  void reject(
      final TypedRecord<ProcessDefinitionSuspensionRecord> command, final Rejection rejection) {
    rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
    responseWriter.writeRejectedResponseOnCommand(command, rejection.type(), rejection.reason());
  }

  /**
   * Resolving all versions writes one {@code Process} event per changed version, so a definition
   * with very many versions can exceed the maximum batch size. The engine has already discarded the
   * records and state changes of this command, so nothing was suspended or resumed.
   */
  ProcessingError rejectIfTooLarge(
      final TypedRecord<ProcessDefinitionSuspensionRecord> command,
      final Throwable error,
      final String verb) {
    if (!(error instanceof ExceededBatchRecordSizeException)) {
      return ProcessingError.UNEXPECTED_ERROR;
    }
    final var value = command.getValue();
    // the rejection carries the command value; with every resolved version it could exceed the
    // batch size itself
    value.clearResolvedVersions();
    final var target =
        isByKey(value)
            ? "key '%d'".formatted(value.getProcessDefinitionKey())
            : "BPMN process ID '%s'".formatted(value.getBpmnProcessId());
    reject(
        command,
        new Rejection(
            RejectionType.INVALID_ARGUMENT, TOO_LARGE_MESSAGE.formatted(verb, target, verb)));
    return ProcessingError.EXPECTED_ERROR;
  }

  private static <T> Either<Rejection, T> invalidArgument(final String reason) {
    return Either.left(new Rejection(RejectionType.INVALID_ARGUMENT, reason));
  }
}
