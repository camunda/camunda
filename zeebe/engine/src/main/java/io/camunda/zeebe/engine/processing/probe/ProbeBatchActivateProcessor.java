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
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.deployment.model.element.SecretReference;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.identity.authorization.CslTenantCheck;
import io.camunda.zeebe.engine.processing.job.JobSecretLookup;
import io.camunda.zeebe.engine.processing.job.JobSecretLookup.Secret;
import io.camunda.zeebe.engine.processing.job.JobSecretLookup.SecretCheckResult;
import io.camunda.zeebe.engine.processing.job.JobSecretLookup.SecretPointerMismatchException;
import io.camunda.zeebe.engine.processing.secretreference.SecretResolutionScheduler;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProbeState;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProbeBatchIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Collects the activatable probes of the probe IDs a worker polls for, as job activation collects
 * the activatable jobs of one type. One poll names several probe IDs (D50), and its maximum counts
 * across all of them. The probe IDs the poller has no {@code PROBE:ANSWER} on are skipped (D52), as
 * job activation skips the jobs its worker may not activate; this is why the processor checks the
 * permission itself rather than once for the command.
 *
 * <p>Secrets are checked at collection, as {@code JobBatchCollector} does (D12, D39): a probe whose
 * secrets are all cached gets their values in the response only, the event keeping the
 * placeholders; a probe with an uncached secret is skipped and parked until its resolution; a probe
 * whose values cannot be injected is canceled.
 */
@NullMarked
@ExcludeAuthorizationCheck
public final class ProbeBatchActivateProcessor implements TypedRecordProcessor<ProbeBatchRecord> {

  static final int MAX_PROBE_IDS = 100;

  private static final Logger LOGGER = Loggers.ENGINE_PROCESSING_LOGGER;

  private static final String SECRET_REFERENCE_UNRESOLVED_MESSAGE =
      "The probe with key '%d' can not be activated, because the secret reference '%s' could not be resolved at '%s'. Fix the input of the probe, then retry.";
  private static final String SECRET_INJECTION_UNKNOWN_CAUSE_MESSAGE =
      "The probe with key '%d' can not be activated, because injecting its secret values failed.";

  private final ProbeState probeState;
  private final ProbeBehavior probeBehavior;
  private final JobSecretLookup secretLookup;
  private final SecretResolutionScheduler secretResolutionScheduler;
  private final CslAuthorizationCheck cslCheck;
  private final CslTenantCheck tenantCheck;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  // filled per command while collecting, read once the ACTIVATED event is written
  private final ProbeBatchRecord responseValue = new ProbeBatchRecord();
  private final Map<Integer, byte[]> injectedInputs = new HashMap<>();
  private final Map<SecretReference, List<Long>> probesToPark = new LinkedHashMap<>();
  private final Map<Long, CanceledProbe> probesToCancel = new LinkedHashMap<>();
  private int responseGrowth;

  public ProbeBatchActivateProcessor(
      final ProbeState probeState,
      final ProbeBehavior probeBehavior,
      final SecretStoreRegistry secretStoreRegistry,
      final SecretResolutionScheduler secretResolutionScheduler,
      final CslAuthorizationCheck cslCheck,
      final CslTenantCheck tenantCheck,
      final KeyGenerator keyGenerator,
      final Writers writers) {
    this.probeState = probeState;
    this.probeBehavior = probeBehavior;
    secretLookup = new JobSecretLookup(secretStoreRegistry);
    this.secretResolutionScheduler = secretResolutionScheduler;
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

    injectedInputs.clear();
    probesToPark.clear();
    probesToCancel.clear();
    responseGrowth = 0;
    collect(command, tenantIds(value, authorizedTenants));

    final long batchKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(batchKey, ProbeBatchIntent.ACTIVATED, value);
    responseWriter.writeAcceptedResponseOnCommand(
        batchKey, ProbeBatchIntent.ACTIVATED, responseValueFor(value), command);

    requestSecretResolution();
    probesToCancel.forEach(
        (probeKey, canceled) ->
            probeBehavior.cancelForSecretResolutionError(
                probeKey, canceled.probe(), canceled.message()));
  }

  /**
   * The batch with the secret values injected into the inputs, as job activation builds its
   * response: a copy, so the event and the state keep the placeholders (D72).
   */
  private ProbeBatchRecord responseValueFor(final ProbeBatchRecord value) {
    if (injectedInputs.isEmpty()) {
      return value;
    }
    responseValue.copyFrom(value);
    injectedInputs.forEach(
        (index, input) -> responseValue.probes().get(index).setInput(BufferUtil.wrapArray(input)));
    return responseValue;
  }

  /**
   * Requests the resolution of the secret references that kept probes from being collected, one
   * {@code RESOLUTION_REQUESTED} per reference with the probes to park on it, as job activation
   * does. A reference that no longer fits the batch is left out: its probes stay activatable, and
   * the next poll parks them.
   */
  private void requestSecretResolution() {
    boolean anyRequested = false;
    for (final var parked : probesToPark.entrySet()) {
      final var event =
          new SecretReferenceRecord()
              .setStoreId(parked.getKey().storeId())
              .setSecretReference(parked.getKey().name());
      parked.getValue().forEach(event::addProbeKey);
      if (!stateWriter.canWriteEventOfLength(
          event.getLength() + EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER)) {
        break;
      }
      stateWriter.appendFollowUpEvent(
          keyGenerator.nextKey(), SecretReferenceIntent.RESOLUTION_REQUESTED, event);
      anyRequested = true;
    }
    if (anyRequested) {
      secretResolutionScheduler.stayAwake();
    }
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
            final var secrets = secretLookup.check(probe.secretReferences());
            if (!secrets.nonCachedSecrets().isEmpty()) {
              registerForResolution(probeKey, secrets);
              return true;
            }
            final byte[] injectedInput;
            try {
              injectedInput = injectedInputOf(probe, secrets);
            } catch (final Exception e) {
              registerForCancel(probeKey, probe, e);
              return true;
            }

            probe.setWorker(value.getWorkerBuffer());
            // the response is the event plus the values: it must fit the max message size too
            final int growth =
                injectedInput == null
                    ? 0
                    : injectedInput.length - probe.getInputBuffer().capacity();
            final int expectedResponseLength =
                command.getLength()
                    + probe.getLength()
                    + responseGrowth
                    + growth
                    + EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER;
            if (!stateWriter.canWriteEventOfLength(expectedResponseLength)) {
              value.setTruncated(true);
              return false;
            }
            if (injectedInput != null) {
              injectedInputs.put(value.probeKeys().size(), injectedInput);
              responseGrowth += growth;
            }
            value.addProbe(probeKey, probe);
            return value.probeKeys().size() < maxProbes;
          });
    }
  }

  /** The input with the cached secret values, or {@code null} if it has none to inject. */
  private byte @Nullable [] injectedInputOf(
      final ProbeRecord probe, final SecretCheckResult secrets) throws IOException {
    return secrets.cachedSecrets().isEmpty()
        ? null
        : secretLookup.injectedDocumentOf(probe.getInputBuffer(), secrets.cachedSecrets());
  }

  /** Groups the probe under each of its uncached references, once per reference. */
  private void registerForResolution(final long probeKey, final SecretCheckResult secrets) {
    final Set<SecretReference> seen = new HashSet<>();
    for (final Secret secret : secrets.nonCachedSecrets()) {
      if (seen.add(secret.reference())) {
        probesToPark
            .computeIfAbsent(secret.reference(), reference -> new ArrayList<>())
            .add(probeKey);
      }
    }
  }

  /**
   * Registers the probe to cancel, with the message a job's injection incident would carry, without
   * the incident part (D13). The failure's own message may quote the input, so it is only logged.
   */
  private void registerForCancel(final long probeKey, final ProbeRecord probe, final Exception e) {
    LOGGER.warn(
        "Failed to inject secret values into the input of the probe with key {}; the probe is canceled",
        probeKey,
        e);
    final String message =
        e instanceof final SecretPointerMismatchException mismatch
            ? SECRET_REFERENCE_UNRESOLVED_MESSAGE.formatted(
                probeKey, mismatch.placeholder(), mismatch.path())
            : SECRET_INJECTION_UNKNOWN_CAUSE_MESSAGE.formatted(probeKey);
    probesToCancel.put(probeKey, new CanceledProbe(probe, message));
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

  private record CanceledProbe(ProbeRecord probe, String message) {}
}
