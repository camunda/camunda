/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.probe;

import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.authz.TenantAccess;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.identity.authorization.CslTenantCheck;
import io.camunda.zeebe.engine.processing.job.JobSecretLookup;
import io.camunda.zeebe.engine.processing.job.JobSecretLookup.SecretCheckResult;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProbeState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProbeBatchIntent;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ProbeEndReason;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects the activatable probes of a probe ID for a worker's poll, as {@code
 * JobBatchActivateProcessor} does for jobs.
 *
 * <p>The secrets of a probe are checked when a poll collects it, from the stores' caches only, as
 * {@code JobBatchCollector} does: when all are cached, their values go into the activation response
 * only, never into the event or the state; when one is missing, the poll skips the probe, which is
 * parked until its resolution makes it activatable again.
 */
public final class ProbeBatchActivateProcessor implements TypedRecordProcessor<ProbeBatchRecord> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProbeBatchActivateProcessor.class);

  private final ProbeState probeState;
  private final ProbeBehavior probeBehavior;
  private final JobSecretLookup secretLookup;
  private final CslAuthorizationCheck cslCheck;
  private final CslTenantCheck tenantCheck;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  public ProbeBatchActivateProcessor(
      final ProcessingState state,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ProbeBehavior probeBehavior,
      final SecretStoreRegistry secretStoreRegistry,
      final CslAuthorizationCheck cslCheck,
      final CslTenantCheck tenantCheck) {
    probeState = state.getProbeState();
    this.probeBehavior = probeBehavior;
    secretLookup = new JobSecretLookup(secretStoreRegistry);
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
    final var authorizedTenants = tenantCheck.resolveAuthorizedTenants(command.getAuthorizations());

    final var invalid = validate(value, authorizedTenants);
    if (invalid != null) {
      rejectionWriter.appendRejection(command, invalid.type(), invalid.reason());
      responseWriter.writeRejectedResponseOnCommand(command, invalid.type(), invalid.reason());
      return;
    }

    final var batch = header(value);
    final var response = header(value);
    final Map<Long, SecretCheckResult> toPark = new LinkedHashMap<>();
    final List<Long> failedInjections = new ArrayList<>();
    // a poll collects nothing that its worker may not answer, as job activation does
    if (mayAnswer(command, value.getProbeId())) {
      collect(value, authorizedTenants, batch, response, toPark, failedInjections);
    }

    final long batchKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(batchKey, ProbeBatchIntent.ACTIVATED, batch);
    responseWriter.writeAcceptedResponseOnCommand(
        batchKey, ProbeBatchIntent.ACTIVATED, response, command);

    toPark.forEach(probeBehavior::requestResolutionAndPark);
    for (final long probeKey : failedInjections) {
      probeBehavior.end(
          probeKey,
          probeState.getProbe(probeKey),
          ProbeIntent.CANCELED,
          ProbeEndReason.SECRET_RESOLUTION_ERROR,
          "Failed to inject the secret values into the input of the probe.");
    }
  }

  /**
   * Adds the probes the poll may collect to the batch (with placeholders) and to the response (with
   * the secret values), up to the requested amount and the room left in the record batch.
   */
  private void collect(
      final ProbeBatchRecord value,
      final TenantAccess authorizedTenants,
      final ProbeBatchRecord batch,
      final ProbeBatchRecord response,
      final Map<Long, SecretCheckResult> toPark,
      final List<Long> failedInjections) {
    final var tenantIds = tenantIds(value, authorizedTenants);
    final var worker = value.getWorker();
    probeState.visitActivatableProbes(
        value.getProbeId(),
        (probeKey, probe) -> {
          if (batch.probeKeys().size() >= value.getMaxProbesToActivate()) {
            batch.setTruncated(true);
            response.setTruncated(true);
            return false;
          }
          if (!isOfTenant(probe, tenantIds, authorizedTenants)) {
            return true;
          }

          final var secrets = secretLookup.check(probe.secretReferences());
          if (!secrets.nonCachedSecrets().isEmpty()) {
            toPark.put(probeKey, secrets);
            return true;
          }
          final var delivered = probe.copy();
          if (!injectSecretValues(probeKey, delivered, secrets)) {
            failedInjections.add(probeKey);
            return true;
          }

          probe.setWorker(worker);
          delivered.setWorker(worker);
          // the event carries the probe once, the response once more with the secret values
          final int growth =
              probe.getLength()
                  + delivered.getLength()
                  + EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER;
          if (!stateWriter.canWriteEventOfLength(batch.getLength() + growth)) {
            batch.setTruncated(true);
            response.setTruncated(true);
            return false;
          }
          batch.addProbe(probeKey, probe);
          response.addProbe(probeKey, delivered);
          return true;
        });
  }

  /** Replaces the placeholders of the delivered copy's input; the probe itself keeps them. */
  private boolean injectSecretValues(
      final long probeKey, final ProbeRecord delivered, final SecretCheckResult secrets) {
    if (secrets.cachedSecrets().isEmpty()) {
      return true;
    }
    try {
      final byte[] injected =
          secretLookup.injectedDocumentOf(delivered.getInputBuffer(), secrets.cachedSecrets());
      if (injected != null) {
        delivered.setInput(BufferUtil.wrapArray(injected));
      }
      return true;
    } catch (final Exception e) {
      // the exception may quote the input, so its details are only logged
      LOGGER.warn(
          "Failed to inject secret values into the input of the probe with key {}", probeKey, e);
      return false;
    }
  }

  private boolean mayAnswer(final TypedRecord<ProbeBatchRecord> command, final String probeId) {
    return cslCheck
        .check(
            command,
            RequiredAuthorization.of(
                b ->
                    b.resourceType(AuthzModelMapper.fromProtocol(AuthorizationResourceType.PROBE))
                        .permissionType(AuthzModelMapper.fromProtocol(PermissionType.ANSWER))
                        .resourceId(probeId)),
            probeId,
            AuthorizationRejectionMapper.noPrincipal())
        .isRight();
  }

  private Rejection validate(final ProbeBatchRecord value, final TenantAccess authorizedTenants) {
    if (value.getProbeId().isBlank()) {
      return new Rejection(
          RejectionType.INVALID_ARGUMENT,
          "Expected to activate probes for a probe ID, but it was blank.");
    }
    if (value.getMaxProbesToActivate() <= 0) {
      return new Rejection(
          RejectionType.INVALID_ARGUMENT,
          "Expected to activate at least one probe, but max probes to activate was %d."
              .formatted(value.getMaxProbesToActivate()));
    }
    if (value.getTenantFilter() == TenantFilter.PROVIDED) {
      final var tenantIds = providedTenantIds(value);
      final var tenantResult =
          tenantCheck.checkTenantsRequiringPrincipal(
              tenantIds,
              authorizedTenants,
              null,
              () ->
                  new Rejection(
                      RejectionType.UNAUTHORIZED,
                      "Expected to activate probes for tenants '%s', but the user is not authorized. Authorized tenants are '%s'."
                          .formatted(tenantIds, authorizedTenants.tenantIds())));
      return tenantResult.isLeft() ? tenantResult.getLeft() : null;
    }
    return null;
  }

  private static List<String> tenantIds(
      final ProbeBatchRecord value, final TenantAccess authorizedTenants) {
    return value.getTenantFilter() == TenantFilter.ASSIGNED
        ? authorizedTenants.tenantIds()
        : providedTenantIds(value);
  }

  /** As job activation: no provided tenant means the default tenant. */
  private static List<String> providedTenantIds(final ProbeBatchRecord value) {
    final var tenantIds = value.getTenantIds();
    return tenantIds.isEmpty() ? List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER) : tenantIds;
  }

  private static boolean isOfTenant(
      final ProbeRecord probe, final List<String> tenantIds, final TenantAccess authorizedTenants) {
    return authorizedTenants.wildcard() || tenantIds.contains(probe.getTenantId());
  }

  private static ProbeBatchRecord header(final ProbeBatchRecord value) {
    return new ProbeBatchRecord()
        .setProbeId(value.getProbeId())
        .setWorker(value.getWorker())
        .setMaxProbesToActivate(value.getMaxProbesToActivate())
        .setTenantIds(value.getTenantIds())
        .setTenantFilter(value.getTenantFilter());
  }
}
