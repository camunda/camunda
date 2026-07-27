/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.common.EventTriggerBehavior;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.BannedInstanceState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.EventScopeInstanceState;
import io.camunda.zeebe.engine.state.immutable.MessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.MessageStartProcessInstanceDedupState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.message.MessageStartProcessInstanceDedupEntry;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.time.InstantSource;

/**
 * Handles the cross-partition {@link MessageStartProcessInstanceRequestIntent#REQUEST} command on
 * {@code P_B = hash(businessId)}.
 *
 * <p>On every request, the processor first consults the per-(processDefinitionKey, messageKey)
 * dedup state. A hit short-circuits the live-state evaluation and re-replies {@link
 * MessageStartProcessInstanceRequestIntent#STARTED} with the cached process-instance key, so a
 * retry from {@code P_K}'s pending-ask state never produces a second PI. A hit is treated as valid
 * when its {@code deletionDeadline} has not yet passed <em>and</em> the cached PI is not banned;
 * otherwise (no entry, expired entry, or banned holder) the request falls through to live-state
 * evaluation. The dedup entry's {@code deletionDeadline} is taken directly from the request's
 * {@code messageDeadline} (= {@code publishTime + ttl} on {@code P_K}), so the dedup row on {@code
 * P_B} and the buffered message on {@code P_K} share the same base lifetime without any
 * engine-internal time coupling. The entry exists to bound {@code P_K}'s retry window — the sole
 * correctness contract that prevents duplicate creates is the deterministic expiry guard below
 * (backed on {@code P_K} by the {@link
 * io.camunda.zeebe.engine.state.appliers.MessageExpiredV2Applier}-driven pending-ask cleanup).
 *
 * <p>Before the dedup lookup, a deterministic TTL-gated expiry guard runs: a request whose {@code
 * messageDeadline} has passed on this partition's clock <em>and</em> whose {@code messageTtl} is
 * positive is refused with a {@link MessageStartProcessInstanceRequestIntent#REJECT_EXPIRED} reply
 * — no dedup lookup, no live evaluation, no activation. This closes the near-deadline duplicate
 * window deterministically and regardless of inter-partition delay: it is a single-clock comparison
 * (same {@code messageDeadline} value the sweep uses, against the same clock), so skew can only
 * shift the accept/reject boundary (liveness), never reopen a duplicate (safety). The {@code
 * messageTtl > 0} carve-out preserves the documented TTL=0 first-arrival activation (a TTL=0
 * message always arrives past its deadline and must still be able to start). See {@link
 * #isDeterministicallyExpired}.
 *
 * <p>On a cache miss the three live-state outcomes are the same as before:
 *
 * <ul>
 *   <li>the start-event subscription is not locally present (deployment-distribution race) → reply
 *       {@link MessageStartProcessInstanceRequestIntent#REJECT_NO_SUBSCRIPTION}; the publisher's
 *       message stays buffered on {@code P_K};
 *   <li>an active root PI on this partition already holds the {@code businessId} for this process
 *       definition <em>and uniqueness is enabled</em> → reply {@link
 *       MessageStartProcessInstanceRequestIntent#REJECT_UNIQUENESS}; the publisher's message stays
 *       buffered on {@code P_K};
 *   <li>otherwise → activate the new PI locally, write a local {@link
 *       MessageStartProcessInstanceRequestIntent#STARTED} follow-up event (whose applier records
 *       the dedup entry), and dispatch the {@link MessageStartProcessInstanceRequestIntent#START}
 *       reply back to {@code P_K}.
 * </ul>
 *
 * <p>The {@code businessIdUniquenessEnabled} flag gates only the uniqueness <em>rejection</em>, not
 * the routing: a remote businessId is always delegated here so the "every businessId PI lives on
 * {@code P_B}" placement invariant holds regardless of the flag (see {@link
 * io.camunda.zeebe.engine.processing.message.MessageCorrelateBehavior}). With the flag off this
 * processor therefore still creates the PI on {@code P_B}, it just never rejects on uniqueness.
 *
 * <p>Rejection outcomes are deliberately not deduplicated: each retry re-evaluates live state on
 * {@code P_B}, so a holder PI that has since completed (or a deployment that has since arrived) can
 * flip a previously-rejected ask into a fresh success. Both rejection outcomes are engine-level
 * idempotent, so re-evaluation is always safe.
 */
@ExcludeAuthorizationCheck
public final class MessageStartProcessInstanceRequestRequestProcessor
    implements TypedRecordProcessor<MessageStartProcessInstanceRequestRecord> {

  private final MessageStartEventSubscriptionState startEventSubscriptionState;
  private final ElementInstanceState elementInstanceState;
  private final BannedInstanceState bannedInstanceState;
  private final MessageStartProcessInstanceDedupState dedupState;
  private final SubscriptionCommandSender commandSender;
  private final StateWriter stateWriter;
  private final EventHandle eventHandle;
  private final KeyGenerator keyGenerator;
  private final InstantSource clock;
  private final boolean businessIdUniquenessEnabled;
  private final MessageStartProcessInstanceRequestRecord startedEventBuffer =
      new MessageStartProcessInstanceRequestRecord();

  public MessageStartProcessInstanceRequestRequestProcessor(
      final MessageStartEventSubscriptionState startEventSubscriptionState,
      final ElementInstanceState elementInstanceState,
      final BannedInstanceState bannedInstanceState,
      final MessageStartProcessInstanceDedupState dedupState,
      final EventScopeInstanceState eventScopeInstanceState,
      final ProcessState processState,
      final EventTriggerBehavior eventTriggerBehavior,
      final BpmnStateBehavior stateBehavior,
      final SubscriptionCommandSender commandSender,
      final KeyGenerator keyGenerator,
      final InstantSource clock,
      final boolean businessIdUniquenessEnabled,
      final Writers writers) {
    this.startEventSubscriptionState = startEventSubscriptionState;
    this.elementInstanceState = elementInstanceState;
    this.bannedInstanceState = bannedInstanceState;
    this.dedupState = dedupState;
    this.commandSender = commandSender;
    stateWriter = writers.state();
    this.keyGenerator = keyGenerator;
    this.clock = clock;
    this.businessIdUniquenessEnabled = businessIdUniquenessEnabled;
    eventHandle =
        new EventHandle(
            keyGenerator,
            eventScopeInstanceState,
            writers,
            processState,
            eventTriggerBehavior,
            stateBehavior);
  }

  @Override
  public void processRecord(final TypedRecord<MessageStartProcessInstanceRequestRecord> record) {
    final var request = record.getValue();

    stateWriter.appendFollowUpEvent(
        record.getKey(), MessageStartProcessInstanceRequestIntent.REQUESTED, request);

    if (isDeterministicallyExpired(request)) {
      // The message's TTL has provably elapsed on this partition's clock, so no PI may be created
      // and no live state must be consulted: doing so could re-evaluate a since-freed businessId
      // and start a duplicate. Reply like every other outcome; the EXPIRED_REJECTED applier on P_K
      // backs the pending ask off (removal stays owned by P_K's message-expiry path).
      commandSender.sendStartProcessInstanceExpiredRejected(request);
      return;
    }

    final var cachedPiKey = lookupValidDedupHit(request);
    if (cachedPiKey != null) {
      commandSender.sendStartProcessInstanceStarted(request, cachedPiKey);
      return;
    }

    if (!localSubscriptionExists(request)) {
      commandSender.sendStartProcessInstanceNoSubscriptionRejected(request);
      return;
    }

    if (businessIdUniquenessEnabled && isBusinessIdAlreadyHeld(request)) {
      commandSender.sendStartProcessInstanceUniquenessRejected(request);
      return;
    }

    final long processInstanceKey = keyGenerator.nextKey();
    // Activate the new PI directly. We do NOT use EventHandle#triggerMessageStartEvent here
    // because that path also writes a MessageStartEventSubscription:CORRELATED event, whose
    // applier dereferences the buffered message in local MessageState — but on P_B the message
    // has never been published locally. The CORRELATED event against the originating
    // subscription is written on P_K when it applies the STARTED reply.
    final var activated =
        eventHandle.activateProcessInstanceForStartEvent(
            request.getProcessDefinitionKey(),
            processInstanceKey,
            request.getStartEventIdBuffer(),
            request.getVariablesBuffer(),
            request.getTenantId(),
            request.getBusinessIdBuffer());

    if (!activated) {
      // Activation failed because the definition is draining/deleted on this partition.
      commandSender.sendStartProcessInstanceNoSubscriptionRejected(request);
      return;
    }

    // Write a local STARTED follow-up event so the dedup applier records
    // (processDefinitionKey, messageKey) → processInstanceKey. The cross-partition reply
    // command triggers a second STARTED event on P_K whose applier clears the pending-ask
    // bookkeeping; this local event is what populates the dedup state on P_B.
    startedEventBuffer.wrap(request);
    startedEventBuffer.setProcessInstanceKey(processInstanceKey);
    stateWriter.appendFollowUpEvent(
        record.getKey(), MessageStartProcessInstanceRequestIntent.STARTED, startedEventBuffer);

    commandSender.sendStartProcessInstanceStarted(request, processInstanceKey);
  }

  /**
   * Returns {@code true} when the request has deterministically expired on {@code P_B}'s clock and
   * must never activate: its {@code messageDeadline} has passed <em>and</em> it was published with
   * a positive TTL. This is the TTL-gated expiry guard.
   *
   * <p>The comparison is <em>single-clock</em>: both this guard and the dedup sweep compare the
   * same {@code messageDeadline} value against {@code P_B}'s clock, so they can never disagree — if
   * the sweep would remove the dedup row, this guard would reject the request, for every
   * interleaving. {@code P_K}/{@code P_B} skew can therefore only shift the accept/reject boundary
   * (a bounded liveness effect: a near-deadline request may expire unstarted), never reopen a
   * duplicate-creation (safety) window.
   *
   * <p>The {@code messageTtl > 0} carve-out preserves the documented TTL=0 first-arrival
   * activation: a TTL=0 (fire-and-forget) message always arrives with {@code messageDeadline <=
   * now}, so gating on the deadline alone would wrongly reject it. Since only a positive-TTL
   * message can be rejected here, and every such message that reaches the deadline is genuinely
   * expired, the guard is exact. The boundary uses {@code <=} to match {@code lookupValidDedupHit}
   * and the sweep, which compare the same value.
   */
  private boolean isDeterministicallyExpired(
      final MessageStartProcessInstanceRequestRecord request) {
    return request.getMessageDeadline() <= clock.millis() && request.getMessageTtl() > 0;
  }

  /**
   * Returns the cached process-instance key when a valid dedup entry exists for the request, or
   * {@code null} when the request must fall through to live-state evaluation. A hit is valid when
   * its {@code deletionDeadline} (the message deadline) has not yet passed, and the cached PI is
   * not currently banned; banned holders are filtered out so a retry can produce a fresh PI,
   * mirroring the lookup-time banned filter on the live-state uniqueness check.
   *
   * <p>The threshold is {@code deletionDeadline <= now} on {@code P_B}'s clock — the same
   * single-clock comparison the expiry guard and the dedup sweep use, so the three never disagree:
   * a request that outlives its dedup row is refused by the expiry guard ({@code REJECT_EXPIRED}),
   * not re-evaluated. The dedup key {@code (processDefinitionKey, messageKey)} is unique per
   * publish, so a not-yet-swept row can never shadow a different publish.
   */
  private Long lookupValidDedupHit(final MessageStartProcessInstanceRequestRecord request) {
    final MessageStartProcessInstanceDedupEntry entry =
        dedupState.get(request.getProcessDefinitionKey(), request.getMessageKey());
    if (entry == null) {
      return null;
    }
    if (entry.getDeletionDeadline() <= clock.millis()) {
      return null;
    }
    final long cachedPiKey = entry.getProcessInstanceKey();
    return bannedInstanceState.isProcessInstanceBanned(cachedPiKey) ? null : cachedPiKey;
  }

  /**
   * Returns {@code true} when this partition has the message-start-event subscription for the
   * requested {@code (tenantId, messageName, processDefinitionKey)} triple. A missing subscription
   * is typically a deployment-distribution race: {@code P_K} already sees it locally, but it has
   * not reached {@code P_B} yet. The O(1) keyed lookup is sufficient because the subscription
   * fields {@code P_B} needs for activation (bpmnProcessId, startEventId, tenantId,
   * processDefinitionKey) are carried verbatim on the request, captured from {@code P_K}'s copy of
   * the same deployment.
   */
  private boolean localSubscriptionExists(final MessageStartProcessInstanceRequestRecord request) {
    return startEventSubscriptionState.exists(
        new MessageStartEventSubscriptionRecord()
            .setTenantId(request.getTenantId())
            .setMessageName(request.getMessageNameBuffer())
            .setProcessDefinitionKey(request.getProcessDefinitionKey()));
  }

  /**
   * Reuses the existing local-arm predicate so the cross-partition check on {@code P_B} and the
   * local-arm check on a same-partition publish stay in lockstep — including the {@link
   * BannedInstanceState#isProcessInstanceBanned banned-PI} filter, without which a banned holder
   * would permanently block legitimate starts.
   */
  private boolean isBusinessIdAlreadyHeld(final MessageStartProcessInstanceRequestRecord request) {
    final var businessId = request.getBusinessIdBuffer();
    if (businessId == null || businessId.capacity() == 0) {
      return false;
    }
    return elementInstanceState.hasActiveProcessInstanceWithBusinessId(
        request.getBusinessId(),
        request.getBpmnProcessId(),
        request.getTenantId(),
        bannedInstanceState::isProcessInstanceBanned);
  }
}
