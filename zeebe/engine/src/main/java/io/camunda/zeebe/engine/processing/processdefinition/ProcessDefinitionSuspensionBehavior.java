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
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.identity.authorization.CslTenantCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor.ProcessingError;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.TenantState;
import io.camunda.zeebe.protocol.impl.record.value.processdefinition.ProcessDefinitionSuspensionRecord;
import io.camunda.zeebe.protocol.impl.record.value.processdefinition.ProcessDefinitionSuspensionResolvedVersion;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionOutcome;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.records.ExceededBatchRecordSizeException;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Finds the process definition versions a suspension command names, checks that the caller may
 * suspend and resume them, and builds the rejections, for {@link
 * ProcessDefinitionSuspensionSuspendProcessor} and {@link
 * ProcessDefinitionSuspensionResumeProcessor}. Each processor gives its own instance the verb
 * ("suspend", "resume") that names the action in rejection reasons, like the job processors do.
 */
@NullMarked
public final class ProcessDefinitionSuspensionBehavior {

  static final PermissionType PERMISSION = PermissionType.SUSPEND_PROCESS_DEFINITION;

  /** The record's version when none is set: all versions of the BPMN process ID. */
  private static final int ALL_VERSIONS = -1;

  private static final String INVALID_VERSION_MESSAGE =
      "Expected to %s a process definition with a version of at least 1, or without a version for all versions, but version '%d' was given";
  private static final String NO_IDENTIFIER_MESSAGE =
      "Expected to %s a process definition by key or by BPMN process ID, but none given";
  private static final String BOTH_IDENTIFIERS_MESSAGE =
      "Expected to %s a process definition by key or by BPMN process ID, but both were given: key '%d', BPMN process ID '%s'";
  private static final String KEY_AND_VERSION_MESSAGE =
      "Expected to %s a process definition by key or by BPMN process ID and version, but key '%d' was given with version '%d'";
  private static final String KEY_AND_TENANT_MESSAGE =
      "Expected to %s a process definition by key or by BPMN process ID and tenant, but key '%d' was given with tenant '%s'";
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
  private final TenantState tenantState;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CslAuthorizationCheck cslCheck;
  private final CslTenantCheck tenantCheck;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final String verb;

  ProcessDefinitionSuspensionBehavior(
      final ProcessingState processingState,
      final Writers writers,
      final CslAuthorizationCheck cslCheck,
      final CslTenantCheck tenantCheck,
      final CommandDistributionBehavior commandDistributionBehavior,
      final String verb) {
    processState = processingState.getProcessState();
    tenantState = processingState.getTenantState();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.cslCheck = cslCheck;
    this.tenantCheck = tenantCheck;
    this.commandDistributionBehavior = commandDistributionBehavior;
    this.verb = verb;
  }

  /**
   * Rejects a command that names no process definition, names it both by key and by BPMN process
   * ID, version or tenant, or gives a version below 1, like the REST API does. The REST mapping
   * sends version {@code -1} and no tenant for requests by key.
   */
  private Either<Rejection, ProcessDefinitionSuspensionRecord> validateIdentifiers(
      final ProcessDefinitionSuspensionRecord value) {
    final long key = value.getProcessDefinitionKey();
    final String bpmnProcessId = value.getBpmnProcessId();
    final int version = value.getVersion();
    if (version != ALL_VERSIONS && version < 1) {
      return invalidArgument(INVALID_VERSION_MESSAGE.formatted(verb, version));
    }
    if (key > 0 && !bpmnProcessId.isEmpty()) {
      return invalidArgument(BOTH_IDENTIFIERS_MESSAGE.formatted(verb, key, bpmnProcessId));
    }
    if (key > 0 && value.getVersion() >= 0) {
      return invalidArgument(KEY_AND_VERSION_MESSAGE.formatted(verb, key, value.getVersion()));
    }
    // an unset tenant reads as the default tenant, so only another tenant can be told apart
    if (key > 0 && !TenantOwned.DEFAULT_TENANT_IDENTIFIER.equals(value.getTenantId())) {
      return invalidArgument(KEY_AND_TENANT_MESSAGE.formatted(verb, key, value.getTenantId()));
    }
    if (key <= 0 && bpmnProcessId.isEmpty()) {
      return invalidArgument(NO_IDENTIFIER_MESSAGE.formatted(verb));
    }
    return Either.right(value);
  }

  /**
   * Finds the versions the command names and checks that the caller may suspend and resume them:
   * one version by key or by BPMN process ID and version, or all versions by BPMN process ID only.
   */
  public Either<Rejection, Iterator<DeployedProcess>> findVersions(
      final TypedRecord<ProcessDefinitionSuspensionRecord> command) {
    return validateIdentifiers(command.getValue())
        .flatMap(value -> isByKey(value) ? findByKey(command) : findById(command));
  }

  private static boolean isByKey(final ProcessDefinitionSuspensionRecord value) {
    return value.getProcessDefinitionKey() > 0;
  }

  /**
   * A caller without access to a definition looked up by key gets this same rejection, so they
   * can't tell an unauthorized key from an unknown one.
   */
  private Rejection notFoundByKey(final ProcessDefinitionSuspensionRecord value) {
    return new Rejection(
        RejectionType.NOT_FOUND,
        NOT_FOUND_BY_KEY_MESSAGE.formatted(verb, value.getProcessDefinitionKey()));
  }

  private Rejection notFoundById(final ProcessDefinitionSuspensionRecord value) {
    return value.getVersion() >= 0
        ? new Rejection(
            RejectionType.NOT_FOUND,
            NOT_FOUND_BY_ID_AND_VERSION_MESSAGE.formatted(
                verb, value.getBpmnProcessId(), value.getVersion()))
        : new Rejection(
            RejectionType.NOT_FOUND,
            NOT_FOUND_BY_ID_MESSAGE.formatted(verb, value.getBpmnProcessId()));
  }

  private static Rejection forbidden() {
    return AuthorizationRejectionMapper.forbidden(
        PERMISSION, AuthorizationResourceType.PROCESS_DEFINITION);
  }

  private static RequiredAuthorization<?> requiredAuthorization(final String bpmnProcessId) {
    return RequiredAuthorization.of(
        b ->
            b.resourceType(
                    AuthzModelMapper.fromProtocol(AuthorizationResourceType.PROCESS_DEFINITION))
                .permissionType(AuthzModelMapper.fromProtocol(PERMISSION))
                .resourceId(bpmnProcessId));
  }

  /**
   * Requests by key carry no tenant, so the key is looked up in the tenants the caller is
   * authorized for, like resource deletion does. The definition's tenant then goes on the record,
   * so the events and the response carry it.
   *
   * <p>The BPMN process ID to authorize against is only known after the lookup, so every failure is
   * a {@code NOT_FOUND}.
   */
  private Either<Rejection, Iterator<DeployedProcess>> findByKey(
      final TypedRecord<ProcessDefinitionSuspensionRecord> command) {
    final var value = command.getValue();
    final long key = value.getProcessDefinitionKey();
    final var notFound = notFoundByKey(value);
    final var authorizedTenants = tenantCheck.resolveAuthorizedTenants(command);
    final @Nullable DeployedProcess process =
        authorizedTenants.wildcard()
            ? findByKeyInAnyTenant(key)
            : findByKeyInTenants(key, authorizedTenants.tenantIds());
    if (process == null) {
      return Either.left(notFound);
    }
    value.setTenantId(process.getTenantId());
    return cslCheck
        .checkAuthorizationAndTenant(
            command,
            requiredAuthorization(bufferAsString(process.getBpmnProcessId())),
            process,
            notFound,
            rejection -> notFound,
            process.getTenantId(),
            notFound)
        .flatMap(authorized -> rejectIfDraining(authorized))
        .map(found -> List.of(found).iterator());
  }

  private @Nullable DeployedProcess findByKeyInAnyTenant(final long key) {
    final @Nullable DeployedProcess process =
        processState.getProcessByKeyAndTenant(key, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    if (process != null) {
      return process;
    }
    final var found = new AtomicReference<@Nullable DeployedProcess>();
    tenantState.forEachTenant(
        tenantId -> {
          found.set(processState.getProcessByKeyAndTenant(key, tenantId));
          return found.get() == null;
        });
    return found.get();
  }

  private @Nullable DeployedProcess findByKeyInTenants(
      final long key, final List<String> tenantIds) {
    for (final var tenantId : tenantIds) {
      final @Nullable DeployedProcess process =
          processState.getProcessByKeyAndTenant(key, tenantId);
      if (process != null) {
        return process;
      }
    }
    return null;
  }

  /** The permission is checked before the lookup, so a caller without it learns nothing. */
  private Either<Rejection, Iterator<DeployedProcess>> findById(
      final TypedRecord<ProcessDefinitionSuspensionRecord> command) {
    final var value = command.getValue();
    return cslCheck
        .checkAuthorizationAndTenant(
            command,
            requiredAuthorization(value.getBpmnProcessId()),
            value,
            forbidden(),
            value.getTenantId(),
            notFoundById(value))
        .flatMap(authorized -> findVersionsById(authorized));
  }

  /**
   * One version by BPMN process ID and version, or all versions by BPMN process ID only.
   *
   * <p>All versions are looked up one at a time as the caller iterates, so the full models of
   * earlier versions can be released while later ones are processed.
   */
  private Either<Rejection, Iterator<DeployedProcess>> findVersionsById(
      final ProcessDefinitionSuspensionRecord value) {
    final var bpmnProcessId = wrapString(value.getBpmnProcessId());
    final String tenantId = value.getTenantId();
    if (value.getVersion() >= 0) {
      final @Nullable DeployedProcess process =
          processState.getProcessByProcessIdAndVersion(bpmnProcessId, value.getVersion(), tenantId);
      if (process == null) {
        return Either.left(notFoundById(value));
      }
      // the rejection carries the key, so the audit log can name the version
      return rejectIfDraining(process)
          .mapLeft(
              rejection -> {
                value.setProcessDefinitionKey(process.getKey());
                return rejection;
              })
          .map(found -> List.of(found).iterator());
    }

    final Iterator<DeployedProcess> versions =
        processState.getKnownProcessVersions(value.getBpmnProcessId(), tenantId).stream()
            .map(
                version ->
                    processState.getProcessByProcessIdAndVersion(
                        bpmnProcessId, version.intValue(), tenantId))
            .filter(Objects::nonNull)
            .iterator();
    return versions.hasNext() ? Either.right(versions) : Either.left(notFoundById(value));
  }

  /**
   * A draining version targeted directly is rejected, like process instance creation and migration
   * do. When resolving all versions, it gets the {@code DRAINING} outcome instead.
   */
  private Either<Rejection, DeployedProcess> rejectIfDraining(final DeployedProcess process) {
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

  /**
   * Distributes the command with its resolved versions and outcomes in the deployment queue (see
   * ADR 0015).
   */
  void distribute(final long key, final TypedRecord<ProcessDefinitionSuspensionRecord> command) {
    commandDistributionBehavior
        .withKey(key)
        .inQueue(DistributionQueue.DEPLOYMENT)
        .distribute(command);
  }

  /** The versions of the command with the given outcome, looked up on this partition. */
  Iterator<DeployedProcess> findVersionsWithOutcome(
      final ProcessDefinitionSuspensionRecord value,
      final ProcessDefinitionSuspensionOutcome outcome) {
    final String tenantId = value.getTenantId();
    return value.getResolvedVersions().stream()
        .filter(version -> version.getOutcome() == outcome)
        .map(
            version ->
                processState.getProcessByKeyAndTenant(version.getProcessDefinitionKey(), tenantId))
        .filter(Objects::nonNull)
        .iterator();
  }

  void acknowledge(final TypedRecord<ProcessDefinitionSuspensionRecord> command) {
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  /**
   * Acknowledges a distributed copy that was rejected for an expected reason; an unexpected failure
   * is sent again (see ADR 0015).
   */
  void acknowledgeIfDistributedAndExpected(
      final TypedRecord<ProcessDefinitionSuspensionRecord> command,
      final ProcessingError processingError) {
    if (command.isCommandDistributed() && processingError == ProcessingError.EXPECTED_ERROR) {
      acknowledge(command);
    }
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
      final TypedRecord<ProcessDefinitionSuspensionRecord> command, final Throwable error) {
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
