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
import io.camunda.zeebe.engine.processing.deployment.model.element.SecretReference;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.identity.authorization.CslTenantCheck;
import io.camunda.zeebe.engine.processing.job.JobSecretLookup;
import io.camunda.zeebe.engine.processing.job.JobSecretLookup.Secret;
import io.camunda.zeebe.engine.processing.job.JobSecretLookup.SecretCheckResult;
import io.camunda.zeebe.engine.processing.secretreference.SecretResolutionScheduler;
import io.camunda.zeebe.engine.processing.streamprocessor.ProbeStreamer;
import io.camunda.zeebe.engine.processing.streamprocessor.ProbeStreamer.ProbeStream;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.AsyncRequestState;
import io.camunda.zeebe.engine.state.immutable.ProbeState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.SecretReferenceState;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.impl.stream.probe.ActivatedProbeImpl;
import io.camunda.zeebe.protocol.impl.stream.probe.ProbeActivationProperties;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AsyncRequestIntent;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ProbeEndReason;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.agrona.collections.MutableBoolean;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hands a probe to a worker and ends it, answering the client that waits for it. Mirrors {@code
 * BpmnJobActivationBehavior} for the push: the stream is picked with the same tenant and permission
 * filter, the activation event never carries the input, and the push is a side effect.
 */
@NullMarked
public final class ProbeBehavior {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProbeBehavior.class);
  private static final DirectBuffer PROBE_STREAM_TYPE =
      BufferUtil.wrapString(ProbeActivationProperties.STREAM_TYPE);

  private final ProbeStreamer probeStreamer;
  private final ProbeState probeState;
  private final SecretReferenceState secretReferenceState;
  private final AsyncRequestState asyncRequestState;
  private final KeyGenerator keyGenerator;
  private final JobSecretLookup secretLookup;
  private final SecretResolutionScheduler secretResolutionScheduler;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final SideEffectWriter sideEffectWriter;
  private final CslAuthorizationCheck cslCheck;
  private final CslTenantCheck tenantCheck;

  public ProbeBehavior(
      final ProbeStreamer probeStreamer,
      final ProcessingState state,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final CslAuthorizationCheck cslCheck,
      final CslTenantCheck tenantCheck,
      final SecretStoreRegistry secretStoreRegistry,
      final SecretResolutionScheduler secretResolutionScheduler) {
    this.probeStreamer = probeStreamer;
    probeState = state.getProbeState();
    secretReferenceState = state.getSecretReferenceState();
    asyncRequestState = state.getAsyncRequestState();
    this.keyGenerator = keyGenerator;
    secretLookup = new JobSecretLookup(secretStoreRegistry);
    this.secretResolutionScheduler = secretResolutionScheduler;
    stateWriter = writers.state();
    responseWriter = writers.response();
    sideEffectWriter = writers.sideEffect();
    this.cslCheck = cslCheck;
    this.tenantCheck = tenantCheck;
  }

  /**
   * Returns a stream that may answer the probe: it declares the probe's exact ID, it serves the
   * probe's tenant, its principal holds {@code PROBE:ANSWER} on the probe ID, and its worker is not
   * the excluded one (the worker a yielded probe was pushed to).
   */
  public Optional<ProbeStream> findStream(
      final ProbeRecord probe, final @Nullable String excludedWorker) {
    final var probeId = probe.getProbeId();
    return probeStreamer.streamFor(
        PROBE_STREAM_TYPE,
        properties ->
            properties.probeIds().contains(probeId)
                && !BufferUtil.bufferAsString(properties.worker()).equals(excludedWorker)
                && isAuthorized(properties, probe));
  }

  /**
   * Activates the probe on the given stream and pushes it there, once every secret it references
   * has a cached value. The activation event carries the worker but never the input; the secret
   * values go only into the pushed copy, as for a pushed job.
   *
   * <p>A probe with a non-cached reference is not activated: its resolution is requested and the
   * probe is parked until it resolves, which dispatches it again ({@link
   * #dispatchAfterResolution}).
   */
  public void activate(final long probeKey, final ProbeRecord probe, final ProbeStream stream) {
    final SecretCheckResult secrets = secretLookup.check(probe.secretReferences());
    if (!secrets.nonCachedSecrets().isEmpty()) {
      requestResolutionAndPark(probeKey, secrets);
      return;
    }

    final var worker = BufferUtil.bufferAsString(stream.properties().worker());
    final var pushedRecord = probe.copy().setWorker(worker);
    if (!secrets.cachedSecrets().isEmpty()) {
      try {
        final byte[] injected =
            secretLookup.injectedDocumentOf(probe.getInputBuffer(), secrets.cachedSecrets());
        if (injected != null) {
          pushedRecord.setInput(BufferUtil.wrapArray(injected));
        }
      } catch (final Exception e) {
        // the exception may quote the input, so its details are only logged
        LOGGER.warn(
            "Failed to inject secret values into the input of the probe with key {}", probeKey, e);
        end(
            probeKey,
            probe,
            ProbeIntent.CANCELED,
            ProbeEndReason.SECRET_RESOLUTION_ERROR,
            "Failed to inject the secret values into the input of the probe.");
        return;
      }
    }

    stateWriter.appendFollowUpEvent(
        probeKey, ProbeIntent.ACTIVATED, withoutInput(probe).setWorker(worker));
    final var pushed = new ActivatedProbeImpl().setProbeKey(probeKey);
    pushed.setRecord(pushedRecord);
    sideEffectWriter.appendSideEffect(() -> stream.push(pushed));
  }

  /**
   * Dispatches a probe parked on a secret reference that just resolved, unless it still waits for
   * another one. The subscriptions may have changed while it was parked, so the stream is picked
   * again; a yielded probe still avoids the worker it was yielded by.
   */
  public void dispatchAfterResolution(final long probeKey) {
    if (probeState.getState(probeKey) != ProbeState.State.PARKED) {
      return;
    }
    final var stillPending = new MutableBoolean(false);
    probeState.visitSecretReferencesByProbe(
        probeKey,
        (storeId, secretReference) -> {
          stillPending.set(secretReferenceState.isPending(storeId, secretReference));
          return !stillPending.get();
        });
    if (stillPending.get()) {
      return;
    }

    final var probe = probeState.getProbe(probeKey);
    final var excludedWorker = probe.getYieldCount() > 0 ? probe.getWorker() : null;
    findStream(probe, excludedWorker)
        .ifPresentOrElse(
            stream -> activate(probeKey, probe, stream), () -> cancelNoWorker(probeKey, probe));
  }

  /** Ends a probe parked on a secret reference that failed to resolve; it would never resolve. */
  public void cancelAfterResolutionFailure(final long probeKey, final String message) {
    final var probe = probeState.getProbe(probeKey);
    if (probe != null) {
      end(probeKey, probe, ProbeIntent.CANCELED, ProbeEndReason.SECRET_RESOLUTION_ERROR, message);
    }
  }

  /** Requests the resolution of every non-cached reference; the event's applier parks the probe. */
  private void requestResolutionAndPark(final long probeKey, final SecretCheckResult secrets) {
    final Set<SecretReference> requested = new HashSet<>();
    for (final Secret secret : secrets.nonCachedSecrets()) {
      if (!requested.add(secret.reference())) {
        continue;
      }
      final var event =
          new SecretReferenceRecord()
              .setStoreId(secret.reference().storeId())
              .setSecretReference(secret.reference().name())
              .addProbeKey(probeKey);
      stateWriter.appendFollowUpEvent(
          keyGenerator.nextKey(), SecretReferenceIntent.RESOLUTION_REQUESTED, event);
    }
    secretResolutionScheduler.stayAwake();
  }

  /** Ends the probe without an answer and tells the waiting client why. */
  public void end(
      final long probeKey,
      final ProbeRecord probe,
      final ProbeIntent intent,
      final ProbeEndReason reason,
      final String message) {
    final var ended = withoutInput(probe).setEndReason(reason).setMessage(message);
    stateWriter.appendFollowUpEvent(probeKey, intent, ended);
    respondToClient(probeKey, intent, ended);
  }

  /** Ends the probe because no worker can take it. */
  public void cancelNoWorker(final long probeKey, final ProbeRecord probe) {
    end(probeKey, probe, ProbeIntent.CANCELED, ProbeEndReason.NO_WORKER, noWorkerMessage(probe));
  }

  /**
   * Answers the client that created the probe, if it still waits. A client that is gone, e.g.
   * because the gateway restarted, simply gets no answer, as for any other async request.
   */
  public void respondToClient(
      final long probeKey, final ProbeIntent intent, final ProbeRecord value) {
    asyncRequestState
        .findRequest(probeKey, ValueType.PROBE, ProbeIntent.CREATE)
        .ifPresent(
            request -> {
              responseWriter.writeAcceptedResponse(
                  probeKey,
                  intent,
                  value,
                  ValueType.PROBE,
                  request.requestId(),
                  request.requestStreamId());
              stateWriter.appendFollowUpEvent(
                  request.key(), AsyncRequestIntent.PROCESSED, request.record());
            });
  }

  /** A copy of the probe that carries neither the input nor its secret references. */
  public static ProbeRecord withoutInput(final ProbeRecord probe) {
    return probe.copy().resetInput().resetSecretReferences();
  }

  public static String noWorkerMessage(final ProbeRecord probe) {
    return "No worker can answer probe '%s'. Start a worker that answers it, then retry."
        .formatted(probe.getProbeId());
  }

  private boolean isAuthorized(
      final ProbeActivationProperties properties, final ProbeRecord probe) {
    final var tenantId = probe.getTenantId();
    final var isTenantAuthorized =
        switch (properties.tenantFilter()) {
          case ASSIGNED -> {
            final var authorizedTenants = tenantCheck.resolveAuthorizedTenants(properties.claims());
            yield !authorizedTenants.wildcard()
                && authorizedTenants.isAuthorizedForTenantId(tenantId);
          }
          case PROVIDED -> properties.tenantIds().contains(tenantId);
        };
    if (!isTenantAuthorized) {
      return false;
    }

    final var probeId = probe.getProbeId();
    return cslCheck
        .checkWithClaims(
            properties.claims(),
            RequiredAuthorization.of(
                b ->
                    b.resourceType(AuthzModelMapper.fromProtocol(AuthorizationResourceType.PROBE))
                        .permissionType(AuthzModelMapper.fromProtocol(PermissionType.ANSWER))
                        .resourceId(probeId)),
            probeId,
            AuthorizationRejectionMapper.forbidden(
                PermissionType.ANSWER, AuthorizationResourceType.PROBE))
        .isRight();
  }
}
