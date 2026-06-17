/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.common.EventTriggerBehavior;
import io.camunda.zeebe.engine.processing.message.MessageCorrelateBehavior;
import io.camunda.zeebe.engine.processing.message.MessageCorrelateBehavior.MessageData;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.BannedInstanceState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.MessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.MessageStartProcessInstanceAskState;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.message.StoredMessage;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.InstantSource;
import java.util.Optional;
import org.agrona.DirectBuffer;

public final class BpmnBufferedMessageStartEventBehavior {

  private final MessageState messageState;
  private final ProcessState processState;
  private final MessageStartEventSubscriptionState messageStartEventSubscriptionState;
  private final ElementInstanceState elementInstanceState;
  private final BannedInstanceState bannedInstanceState;
  private final MessageStartProcessInstanceAskState messageStartProcessInstanceAskState;
  private final boolean businessIdUniquenessEnabled;

  private final MessageCorrelateBehavior messageCorrelateBehavior;
  private final InstantSource clock;

  public BpmnBufferedMessageStartEventBehavior(
      final ProcessingState processingState,
      final KeyGenerator keyGenerator,
      final EventTriggerBehavior eventTriggerBehavior,
      final BpmnStateBehavior stateBehavior,
      final Writers writers,
      final SubscriptionCommandSender commandSender,
      final RoutingInfo routingInfo,
      final InstantSource clock,
      final boolean businessIdUniquenessEnabled) {
    messageState = processingState.getMessageState();
    processState = processingState.getProcessState();
    messageStartEventSubscriptionState = processingState.getMessageStartEventSubscriptionState();
    elementInstanceState = processingState.getElementInstanceState();
    bannedInstanceState = processingState.getBannedInstanceState();
    messageStartProcessInstanceAskState = processingState.getMessageStartProcessInstanceAskState();
    this.businessIdUniquenessEnabled = businessIdUniquenessEnabled;
    this.clock = clock;

    final var eventHandle =
        new EventHandle(
            keyGenerator,
            processingState.getEventScopeInstanceState(),
            writers,
            processState,
            eventTriggerBehavior,
            stateBehavior);

    // Reuse the live-publish correlation logic for the buffered pick-up so a buffered message whose
    // businessId belongs to another partition is re-routed through the cross-partition handshake
    // instead of being started locally (which would bypass P_B's uniqueness check).
    messageCorrelateBehavior =
        new MessageCorrelateBehavior(
            messageStartEventSubscriptionState,
            messageState,
            eventHandle,
            writers.state(),
            processingState.getMessageSubscriptionState(),
            commandSender,
            elementInstanceState,
            bannedInstanceState,
            businessIdUniquenessEnabled,
            routingInfo,
            processingState.getPartitionId());
  }

  public Optional<DirectBuffer> findCorrelationKey(final BpmnElementContext context) {
    final var processInstanceKey = context.getProcessInstanceKey();
    return Optional.ofNullable(messageState.getProcessInstanceCorrelationKey(processInstanceKey));
  }

  /**
   * Returns {@code true} when the completing/terminating instance holds a Business ID that the
   * on-completion re-drive should act on — i.e. the feature is enabled and the instance carries a
   * non-empty Business ID.
   *
   * <p>Unlike the correlation-key arm, the Business ID arm does <em>not</em> require the completing
   * instance's own process to have a message start event. Business ID uniqueness is scoped per
   * {@code (businessId, bpmnProcessId)} across versions, so the holder may be an older version — or
   * a none-start instance created via {@code CreateProcessInstance} — whose version lacks a message
   * start event, while the <em>latest</em> version that owns the buffered message-start
   * subscription has one. Gating the re-drive on the holder's own {@code hasMessageStartEvent()}
   * would strand such a buffered start until TTL (ADR 0002 D5).
   */
  public boolean shouldRedriveBlockedBusinessIdOnCompletion(final BpmnElementContext context) {
    final var businessId = context.getBusinessId();
    return businessIdUniquenessEnabled && businessId != null && !businessId.isEmpty();
  }

  /**
   * Picks the next eligible buffered message-start message for the given {@code (process,
   * correlationKey)} and triggers it, if any.
   *
   * <p>Takes {@code bpmnProcessId} and {@code tenantId} directly rather than a {@link
   * BpmnElementContext} so it can be driven from contexts that have no completing element instance
   * to hand. Today the local process-correlation-key lock release runs on process-instance
   * completion (which supplies a context); the cross-partition lock release runs from a poll
   * response on {@code P_K} (which does not). Both need the same buffer pick-up behaviour.
   */
  public void correlateNextBufferedMessage(
      final DirectBuffer bpmnProcessId, final DirectBuffer correlationKey, final String tenantId) {

    final var process = processState.getLatestProcessVersionByProcessId(bpmnProcessId, tenantId);

    findNextMessageToCorrelate(process, correlationKey).ifPresent(this::triggerCorrelation);
  }

  /**
   * Picks up buffered message-starts unblocked by a holder instance completing/terminating, for
   * both unblock reasons: the freed process-correlation-key lock and the freed Business ID. The
   * holder's own correlation key and businessId are captured by the caller <em>before</em> the
   * completion transition removes them.
   *
   * <p>A single completion may unblock a message in the correlation-key queue and, separately, a
   * message held only on the Business ID (which may carry a different correlation key, or none).
   * Both are re-driven. The Business ID hold of a freshly triggered start is applied via a
   * follow-up command and is therefore <em>not</em> visible within this processing step, so this
   * method must never trigger two starts carrying the same (freed) Business ID: when the
   * correlation-key candidate also carries the freed Business ID, only the lower-message-key of the
   * two is triggered; the other resumes on the next completion. Candidates carrying different
   * Business IDs are independent and may both start.
   */
  public void correlateNextBufferedMessagesOnCompletion(
      final BpmnElementContext context,
      final DirectBuffer correlationKey,
      final String businessId) {
    final var bpmnProcessId = context.getBpmnProcessId();
    final var tenantId = context.getTenantId();
    final var process = processState.getLatestProcessVersionByProcessId(bpmnProcessId, tenantId);
    if (process == null) {
      return;
    }

    final var correlationKeyCandidate =
        correlationKey != null
            ? findNextMessageToCorrelate(process, correlationKey)
            : Optional.<Correlation>empty();
    final var businessIdCandidate =
        businessIdUniquenessEnabled && businessId != null && !businessId.isEmpty()
            ? findNextMessageToCorrelateByBusinessId(
                process, tenantId, BufferUtil.wrapString(businessId))
            : Optional.<Correlation>empty();

    if (correlationKeyCandidate.isEmpty()) {
      businessIdCandidate.ifPresent(this::triggerCorrelation);
      return;
    }
    if (businessIdCandidate.isEmpty()) {
      triggerCorrelation(correlationKeyCandidate.get());
      return;
    }

    final var ck = correlationKeyCandidate.get();
    final var bid = businessIdCandidate.get();
    if (ck.messageKey == bid.messageKey) {
      // both reasons resolve to the same buffered message: trigger it once
      triggerCorrelation(ck);
      return;
    }

    final var ckMessage = messageState.getMessage(ck.messageKey);
    final var ckBusinessId =
        ckMessage != null
            ? BufferUtil.bufferAsString(ckMessage.getMessage().getBusinessIdBuffer())
            : "";
    if (businessId.equals(ckBusinessId)) {
      // both candidates carry the freed Business ID — starting both would create two PIs holding
      // it,
      // since the first start's hold is not yet applied. Trigger only the earlier (FIFO) one.
      triggerCorrelation(ck.messageKey <= bid.messageKey ? ck : bid);
    } else {
      // different Business IDs (or the correlation-key candidate carries none) — safe to start both
      triggerCorrelation(ck);
      triggerCorrelation(bid);
    }
  }

  private void triggerCorrelation(final Correlation messageCorrelation) {
    final var storedMessage = messageState.getMessage(messageCorrelation.messageKey);
    final var message = storedMessage.getMessage();

    // Route the picked-up message exactly as a fresh publish would be: a businessId that hashes to
    // another partition is delegated to P_B via the cross-partition ask rather than started
    // locally,
    // so the buffered pick-up cannot bypass the uniqueness handshake or violate the invariant that
    // every businessId-carrying PI lives on P_B.
    messageCorrelateBehavior.triggerOrDelegateStartEvent(
        new MessageData(
            storedMessage.getMessageKey(),
            message.getNameBuffer(),
            message.getCorrelationKeyBuffer(),
            message.getVariablesBuffer(),
            message.getTenantId(),
            message.getBusinessIdBuffer(),
            message.getDeadline()),
        messageCorrelation.subscriptionKey,
        messageCorrelation.subscriptionRecord);
  }

  /**
   * Business-ID analogue of {@link #findNextMessageToCorrelate}: picks the lowest-message-key
   * eligible buffered message-start that carries {@code businessId} for one of {@code process}'s
   * message-start subscriptions, applying the same eligibility guards. Mirrors the correlation-key
   * scan but is driven by the Business-ID index (see ADR 0002 D5).
   */
  private Optional<Correlation> findNextMessageToCorrelateByBusinessId(
      final DeployedProcess process, final String tenantId, final DirectBuffer businessId) {

    final var messageCorrelation = new Correlation();
    final var bpmnProcessId = BufferUtil.bufferAsString(process.getBpmnProcessId());

    messageStartEventSubscriptionState.visitSubscriptionsByProcessDefinition(
        process.getKey(),
        subscription -> {
          final var messageName = subscription.getRecord().getMessageNameBuffer();

          messageState.visitMessagesWithBusinessId(
              tenantId,
              businessId,
              storedMessage -> {
                // the Business-ID index is not name-scoped, so match the subscription's name here;
                // otherwise apply the same guards as the correlation-key scan
                if (BufferUtil.equals(storedMessage.getMessage().getNameBuffer(), messageName)
                    && storedMessage.getMessage().getDeadline() > clock.millis()
                    && !messageState.existMessageCorrelation(
                        storedMessage.getMessageKey(), process.getBpmnProcessId())
                    && !isBusinessIdAlreadyHeld(storedMessage, bpmnProcessId)
                    && !hasLivePendingAsk(storedMessage.getMessageKey(), process.getKey())) {

                  if (storedMessage.getMessageKey() < messageCorrelation.messageKey) {
                    messageCorrelation.messageKey = storedMessage.getMessageKey();
                    messageCorrelation.subscriptionKey = subscription.getKey();
                    messageCorrelation.subscriptionRecord.wrap(subscription.getRecord());
                  }

                  return false;
                }

                return true;
              });
        });

    if (messageCorrelation.subscriptionKey > 0) {
      return Optional.of(messageCorrelation);
    } else {
      return Optional.empty();
    }
  }

  private Optional<Correlation> findNextMessageToCorrelate(
      final DeployedProcess process, final DirectBuffer correlationKey) {

    final var messageCorrelation = new Correlation();
    final var bpmnProcessId = BufferUtil.bufferAsString(process.getBpmnProcessId());

    messageStartEventSubscriptionState.visitSubscriptionsByProcessDefinition(
        process.getKey(),
        subscription -> {
          final var subscriptionRecord = subscription.getRecord();
          final var messageName = subscriptionRecord.getMessageNameBuffer();

          messageState.visitMessages(
              subscriptionRecord.getTenantId(),
              messageName,
              correlationKey,
              storedMessage -> {
                // correlate the first message with same correlation key that was not correlated
                // yet. Additionally, when the feature is enabled, skip a buffered message whose
                // businessId is currently taken by another active PI on this partition; the
                // visitor returns true so the scan continues to subsequent buffered entries for
                // the same correlation key — the queue itself is not stalled, only the skipped
                // entry is left in the buffer until its TTL or until another K-keyed completion
                // triggers a rescan.
                //
                // Also skip a message that has a live pending cross-partition ask: such a message
                // is owned by the rejection-retry registry, which is its single retry owner. This
                // correlation-key-keyed scan cannot retry it correctly anyway — the unblocking
                // event (the holder PI completing) is businessId-scoped and may carry a different
                // correlation key, or none at all, so it need not trigger a rescan for this
                // correlation key. Picking the message up here would emit a redundant second ask
                // (harmless under P_B's dedup, but wasteful); the registry's scheduler drives the
                // retry under back-off until the message starts or its TTL expires.
                if (storedMessage.getMessage().getDeadline() > clock.millis()
                    && !messageState.existMessageCorrelation(
                        storedMessage.getMessageKey(), process.getBpmnProcessId())
                    && !isBusinessIdAlreadyHeld(storedMessage, bpmnProcessId)
                    && !hasLivePendingAsk(storedMessage.getMessageKey(), process.getKey())) {

                  // correlate the first published message across all message start events
                  // - using the message key to decide which message was published before
                  if (storedMessage.getMessageKey() < messageCorrelation.messageKey) {
                    messageCorrelation.messageKey = storedMessage.getMessageKey();
                    messageCorrelation.subscriptionKey = subscription.getKey();
                    messageCorrelation.subscriptionRecord.wrap(subscription.getRecord());
                  }

                  return false;
                }

                return true;
              });
        });

    if (messageCorrelation.subscriptionKey > 0) {
      return Optional.of(messageCorrelation);
    } else {
      return Optional.empty();
    }
  }

  /**
   * Returns {@code true} when the feature is enabled, the buffered message carries a businessId,
   * and an active root PI on this partition already holds that businessId for this process
   * definition. See the class JavaDoc of {@link
   * io.camunda.zeebe.engine.processing.message.MessageCorrelateBehavior} for the system-level
   * retry/cross-partition narrative this predicate participates in.
   */
  private boolean isBusinessIdAlreadyHeld(
      final StoredMessage storedMessage, final String bpmnProcessId) {
    if (!businessIdUniquenessEnabled) {
      return false;
    }
    final var businessId = storedMessage.getMessage().getBusinessIdBuffer();
    if (businessId == null || businessId.capacity() == 0) {
      return false;
    }
    return elementInstanceState.hasActiveProcessInstanceWithBusinessId(
        BufferUtil.bufferAsString(businessId),
        bpmnProcessId,
        storedMessage.getMessage().getTenantId(),
        bannedInstanceState::isProcessInstanceBanned);
  }

  /**
   * Returns {@code true} when a cross-partition message-start ask is still pending for this {@code
   * (messageKey, processDefinitionKey)}. Such a message is owned by the rejection-retry registry,
   * which re-sends the ask under back-off, so the correlation-key buffer scan must not pick it up.
   *
   * <p>This guard is not redundant with {@link #isBusinessIdAlreadyHeld}: that predicate only sees
   * <em>local</em> active PIs, but the rejections this registry retries are precisely the ones it
   * cannot see — a cross-partition {@code UNIQUENESS_REJECTED} (the holder lives on {@code P_B})
   * and {@code NO_SUBSCRIPTION_REJECTED} (the businessId is not locally held at all) both leave
   * {@code isBusinessIdAlreadyHeld} returning {@code false}. Without this guard the scan would
   * re-pick such a message on the next same-correlation-key completion and emit a redundant second
   * ask (harmless under {@code P_B}'s dedup, but wasteful). The registry's scheduler is the single
   * owner of the retry.
   *
   * <p>The presence of a pending ask is respected regardless of {@code
   * businessIdUniquenessEnabled}: a remote Business ID is delegated to {@code P_B} independently of
   * the flag (see ADR 0002 D6), and {@code NO_SUBSCRIPTION_REJECTED} asks are retried whether or
   * not uniqueness is enabled, so asks can be pending even when the flag is off.
   */
  private boolean hasLivePendingAsk(final long messageKey, final long processDefinitionKey) {
    return messageStartProcessInstanceAskState.get(messageKey, processDefinitionKey) != null;
  }

  private static final class Correlation {
    private long messageKey = Long.MAX_VALUE;
    private long subscriptionKey = -1L;
    private final MessageStartEventSubscriptionRecord subscriptionRecord =
        new MessageStartEventSubscriptionRecord();
  }
}
