/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.probe;

import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.authz.TenantAccess;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.identity.authorization.CslTenantCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProbeState;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeBatchRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProbeBatchIntent;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import org.jspecify.annotations.NullMarked;

/**
 * Collects the activatable probes of the probe IDs a worker polls for, as job activation collects
 * the activatable jobs of one type. One poll names several probe IDs (D50), and its maximum counts
 * across all of them. The probe IDs the poller has no {@code PROBE:ANSWER} on are skipped (D52), as
 * job activation skips the jobs its worker may not activate; this is why the processor checks the
 * permission itself rather than once for the command.
 */
@NullMarked
@ExcludeAuthorizationCheck
public final class ProbeBatchActivateProcessor implements TypedRecordProcessor<ProbeBatchRecord> {

  static final int MAX_PROBE_IDS = 100;

  private final ProbeState probeState;
  private final CslAuthorizationCheck cslCheck;
  private final CslTenantCheck tenantCheck;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  public ProbeBatchActivateProcessor(
      final ProbeState probeState,
      final CslAuthorizationCheck cslCheck,
      final CslTenantCheck tenantCheck,
      final KeyGenerator keyGenerator,
      final Writers writers) {
    this.probeState = probeState;
    this.cslCheck = cslCheck;
    this.tenantCheck = tenantCheck;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
  }

  @Override
  public void processRecord(final TypedRecord<ProbeBatchRecord> command) {
    final var value = command.getValue();
    final var authorizedTenants = tenantCheck.resolveAuthorizedTenants(command.getAuthInfo());

    final var validation =
        validateTenants(value, authorizedTenants).flatMap(ignored -> validateFields(value));
    if (validation.isLeft()) {
      final var rejection = validation.getLeft();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectedResponseOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    collect(command, tenantIds(value, authorizedTenants));

    // the event and the response carry the probes with placeholders: the secret values come with
    // #68866, into the response only
    final long batchKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(batchKey, ProbeBatchIntent.ACTIVATED, value);
    responseWriter.writeAcceptedResponseOnCommand(
        batchKey, ProbeBatchIntent.ACTIVATED, value, command);
  }

  /**
   * Adds the probes to the command value, as job activation does, until the poll has its maximum or
   * the batch has no room left. The state changes only when the ACTIVATED event is applied.
   */
  private void collect(final TypedRecord<ProbeBatchRecord> command, final List<String> tenantIds) {
    final var value = command.getValue();
    final int maxProbes = value.getMaxProbesToActivate();
    final var mayAnswer = answerPermission(command);

    for (final String probeId : value.getProbeIds()) {
      if (value.probeKeys().size() >= maxProbes || value.isTruncated()) {
        return;
      }
      if (!mayAnswer.test(probeId)) {
        continue;
      }
      probeState.forEachActivatableProbe(
          BufferUtil.wrapString(probeId),
          tenantIds,
          (probeKey, probe) -> {
            probe.setWorker(value.getWorkerBuffer());
            final int expectedEventLength =
                command.getLength()
                    + probe.getLength()
                    + EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER;
            if (!stateWriter.canWriteEventOfLength(expectedEventLength)) {
              value.setTruncated(true);
              return false;
            }
            value.addProbe(probeKey, probe);
            return value.probeKeys().size() < maxProbes;
          });
    }
  }

  /** Whether the poller holds PROBE:ANSWER on a probe ID, with its claims resolved once. */
  private Predicate<String> answerPermission(final TypedRecord<ProbeBatchRecord> command) {
    final var resolved =
        cslCheck.resolveForCheck(command, AuthorizationRejectionMapper.noPrincipal());
    if (resolved.isLeft()) {
      return probeId -> false;
    }
    final var authentication = resolved.get();
    if (authentication.isEmpty()) {
      return probeId -> true;
    }
    return probeId ->
        cslCheck
            .checkAuth(
                authentication.get(),
                RequiredAuthorization.of(
                    b ->
                        b.resourceType(
                                AuthzModelMapper.fromProtocol(AuthorizationResourceType.PROBE))
                            .permissionType(AuthzModelMapper.fromProtocol(PermissionType.ANSWER))
                            .resourceId(probeId)))
            .isRight();
  }

  private Either<Rejection, ProbeBatchRecord> validateTenants(
      final ProbeBatchRecord value, final TenantAccess authorizedTenants) {
    if (value.getTenantFilter() != TenantFilter.PROVIDED) {
      return Either.right(value);
    }
    final var tenantIds = providedTenantIds(value);
    return tenantCheck.checkTenantsRequiringPrincipal(
        tenantIds,
        authorizedTenants,
        value,
        () ->
            new Rejection(
                RejectionType.UNAUTHORIZED,
                "Expected to activate probes for tenants '%s', but the user is not authorized. Authorized tenants are '%s'."
                    .formatted(tenantIds, authorizedTenants.tenantIds())));
  }

  private static Either<Rejection, ProbeBatchRecord> validateFields(final ProbeBatchRecord value) {
    final var probeIds = value.getProbeIds();
    if (probeIds.isEmpty() || probeIds.size() > MAX_PROBE_IDS) {
      return invalidArgument(
          "Expected to activate probes for 1 to %d probe IDs, but got %d."
              .formatted(MAX_PROBE_IDS, probeIds.size()));
    }
    for (final String probeId : probeIds) {
      if (!ProbeCreateProcessor.isValidProbeId(probeId)) {
        return invalidArgument(
            "Expected probe IDs of 1 to %d characters, without whitespace and other than '*', but got '%s'."
                .formatted(ProbeCreateProcessor.MAX_PROBE_ID_LENGTH, probeId));
      }
    }
    if (new HashSet<>(probeIds).size() != probeIds.size()) {
      return invalidArgument(
          "Expected to activate probes for distinct probe IDs, but got %s.".formatted(probeIds));
    }
    if (value.getMaxProbesToActivate() <= 0) {
      return invalidArgument(
          "Expected to activate probes with max probes to activate greater than zero, but it was '%d'."
              .formatted(value.getMaxProbesToActivate()));
    }
    return Either.right(value);
  }

  private static List<String> tenantIds(
      final ProbeBatchRecord value, final TenantAccess authorizedTenants) {
    return value.getTenantFilter() == TenantFilter.ASSIGNED
        ? authorizedTenants.tenantIds()
        : providedTenantIds(value);
  }

  /** As for job activation, no tenant provided means the default tenant. */
  private static List<String> providedTenantIds(final ProbeBatchRecord value) {
    final var tenantIds = value.getTenantIds();
    return tenantIds.isEmpty() ? List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER) : tenantIds;
  }

  private static Either<Rejection, ProbeBatchRecord> invalidArgument(final String reason) {
    return Either.left(new Rejection(RejectionType.INVALID_ARGUMENT, reason));
  }
}
