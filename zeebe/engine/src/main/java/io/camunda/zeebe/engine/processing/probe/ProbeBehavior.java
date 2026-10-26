/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.probe;

import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.identity.authorization.CslTenantCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.ProbeStreamer;
import io.camunda.zeebe.engine.processing.streamprocessor.ProbeStreamer.ProbeStream;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.AsyncRequestState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.impl.stream.probe.ActivatedProbeImpl;
import io.camunda.zeebe.protocol.impl.stream.probe.ProbeActivationProperties;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AsyncRequestIntent;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ProbeEndReason;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Hands a probe to a worker and ends it, answering the client that waits for it. Mirrors {@code
 * BpmnJobActivationBehavior} for the push: the stream is picked with the same tenant and permission
 * filter, the activation event never carries the input, and the push is a side effect.
 */
@NullMarked
public final class ProbeBehavior {

  private final ProbeStreamer probeStreamer;
  private final AsyncRequestState asyncRequestState;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final SideEffectWriter sideEffectWriter;
  private final CslAuthorizationCheck cslCheck;
  private final CslTenantCheck tenantCheck;

  public ProbeBehavior(
      final ProbeStreamer probeStreamer,
      final ProcessingState state,
      final Writers writers,
      final CslAuthorizationCheck cslCheck,
      final CslTenantCheck tenantCheck) {
    this.probeStreamer = probeStreamer;
    asyncRequestState = state.getAsyncRequestState();
    stateWriter = writers.state();
    responseWriter = writers.response();
    sideEffectWriter = writers.sideEffect();
    this.cslCheck = cslCheck;
    this.tenantCheck = tenantCheck;
  }

  /**
   * Returns a stream that may answer the probe: its type is the probe's capability, it serves the
   * probe's tenant, its principal holds {@code PROBE:ANSWER} on the template, and its worker is not
   * the excluded one (the worker a yielded probe was pushed to).
   */
  public Optional<ProbeStream> findStream(
      final ProbeRecord probe, final @Nullable String excludedWorker) {
    final var streamType =
        BufferUtil.wrapString(probe.getOperation().name() + ":" + probe.getTemplate());
    return probeStreamer.streamFor(
        streamType,
        properties ->
            !BufferUtil.bufferAsString(properties.worker()).equals(excludedWorker)
                && isAuthorized(properties, probe));
  }

  /**
   * Activates the probe on the given stream and pushes it there. The activation event carries the
   * worker but never the input; the input goes only to the pushed copy.
   */
  public void activate(final long probeKey, final ProbeRecord probe, final ProbeStream stream) {
    final var worker = BufferUtil.bufferAsString(stream.properties().worker());
    final var activated = withoutInput(probe).setWorker(worker);
    stateWriter.appendFollowUpEvent(probeKey, ProbeIntent.ACTIVATED, activated);

    final var pushed = new ActivatedProbeImpl().setProbeKey(probeKey);
    pushed.setRecord(probe.copy().setWorker(worker));
    sideEffectWriter.appendSideEffect(() -> stream.push(pushed));
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
    return "No worker can answer %s for template %s. Start a connector runtime that supports it, then retry."
        .formatted(probe.getOperation(), probe.getTemplate());
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

    final var template = probe.getTemplate();
    return cslCheck
        .checkWithClaims(
            properties.claims(),
            RequiredAuthorization.of(
                b ->
                    b.resourceType(AuthzModelMapper.fromProtocol(AuthorizationResourceType.PROBE))
                        .permissionType(AuthzModelMapper.fromProtocol(PermissionType.ANSWER))
                        .resourceId(template)),
            template,
            AuthorizationRejectionMapper.forbidden(
                PermissionType.ANSWER, AuthorizationResourceType.PROBE))
        .isRight();
  }
}
