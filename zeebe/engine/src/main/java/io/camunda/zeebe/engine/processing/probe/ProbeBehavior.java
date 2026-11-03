/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.probe;

import io.camunda.zeebe.engine.processing.deployment.model.element.SecretReference;
import io.camunda.zeebe.engine.processing.job.JobSecretLookup.Secret;
import io.camunda.zeebe.engine.processing.job.JobSecretLookup.SecretCheckResult;
import io.camunda.zeebe.engine.processing.secretreference.SecretResolutionScheduler;
import io.camunda.zeebe.engine.processing.streamprocessor.ProbeNotifier;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.AsyncRequestState;
import io.camunda.zeebe.engine.state.immutable.ProbeState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AsyncRequestIntent;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.value.ProbeEndReason;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.HashSet;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/**
 * What several probe processors do: announce that a probe ID has probes to collect, park a probe on
 * the secrets a poll found uncached, and end a probe, answering the client that waits for it.
 */
@NullMarked
public final class ProbeBehavior {

  private final ProbeNotifier probeNotifier;
  private final ProbeState probeState;
  private final AsyncRequestState asyncRequestState;
  private final KeyGenerator keyGenerator;
  private final SecretResolutionScheduler secretResolutionScheduler;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final SideEffectWriter sideEffectWriter;

  public ProbeBehavior(
      final ProbeNotifier probeNotifier,
      final ProcessingState state,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final SecretResolutionScheduler secretResolutionScheduler) {
    this.probeNotifier = probeNotifier;
    probeState = state.getProbeState();
    asyncRequestState = state.getAsyncRequestState();
    this.keyGenerator = keyGenerator;
    this.secretResolutionScheduler = secretResolutionScheduler;
    stateWriter = writers.state();
    responseWriter = writers.response();
    sideEffectWriter = writers.sideEffect();
  }

  /**
   * Tells the gateways that the probe ID has a probe to collect, after the events that made it
   * activatable are written, as jobs announce their type.
   */
  public void notifyProbesAvailable(final String probeId) {
    sideEffectWriter.appendSideEffect(() -> probeNotifier.notifyProbesAvailable(probeId));
  }

  /**
   * Requests the resolution of every reference the poll found uncached; the event's applier parks
   * the probe until they are resolved, which makes it activatable again.
   */
  public void requestResolutionAndPark(final long probeKey, final SecretCheckResult secrets) {
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

  /** Ends a probe parked on a secret reference that failed to resolve; it would never resolve. */
  public void cancelAfterResolutionFailure(final long probeKey, final String message) {
    final var probe = probeState.getProbe(probeKey);
    if (probe != null) {
      end(probeKey, probe, ProbeIntent.CANCELED, ProbeEndReason.SECRET_RESOLUTION_ERROR, message);
    }
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

  /** Why a probe that no poll collected before its deadline ends; the client sees it as 404. */
  public static String noWorkerMessage(final ProbeRecord probe) {
    return "No worker for '%s' collected the probe within %d ms. Start a worker for this probe ID, then retry."
        .formatted(probe.getProbeId(), probe.getRequestTimeout());
  }
}
