/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.engine.state.message.ProcessMessageSubscription;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState.PendingSubscription;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.InstantSource;

@ExcludeAuthorizationCheck
public final class ProcessMessageSubscriptionCreateProcessor
    implements TypedRecordProcessor<ProcessMessageSubscriptionRecord> {

  private static final String NO_SUBSCRIPTION_FOUND_MESSAGE =
      "Expected to create process message subscription with element key '%d' and message name '%s', "
          + "but no such subscription was found";
  private static final String NOT_OPENING_MSG =
      "Expected to create process message subscription with element key '%d' and message name '%s', "
          + "but it is already %s";
  private static final String REOPEN_NO_SUBSCRIPTION_MSG =
      "Expected to reopen process message subscription with element key '%d' and message name '%s', "
          + "but no such subscription was found — the resume manifest is gone (likely the instance "
          + "was cancelled while suspended)";
  private static final String REOPEN_NOT_MANIFEST_MSG =
      "Expected to reopen process message subscription with element key '%d' and message name '%s', "
          + "but it is %s rather than an OPENED resume manifest";

  private final ProcessMessageSubscriptionState subscriptionState;
  private final SuspensionState suspensionState;
  private final SubscriptionCommandSender subscriptionCommandSender;
  private final TransientPendingSubscriptionState transientProcessMessageSubscriptionState;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final SideEffectWriter sideEffectWriter;
  private final InstantSource clock;

  // Scratch record used to build the follow-up event value. The state API returns a value backed by
  // the column family's shared mutable object, so we must not mutate it in place — we copy into
  // this processor-owned instance, stamp the key, and append the copy.
  private final ProcessMessageSubscriptionRecord eventRecord =
      new ProcessMessageSubscriptionRecord();

  public ProcessMessageSubscriptionCreateProcessor(
      final ProcessMessageSubscriptionState subscriptionState,
      final SuspensionState suspensionState,
      final SubscriptionCommandSender subscriptionCommandSender,
      final Writers writers,
      final TransientPendingSubscriptionState transientProcessMessageSubscriptionState,
      final InstantSource clock) {
    this.subscriptionState = subscriptionState;
    this.suspensionState = suspensionState;
    this.subscriptionCommandSender = subscriptionCommandSender;
    this.transientProcessMessageSubscriptionState = transientProcessMessageSubscriptionState;
    stateWriter = writers.state();
    sideEffectWriter = writers.sideEffect();
    rejectionWriter = writers.rejection();
    this.clock = clock;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessMessageSubscriptionRecord> command) {
    if (command.getIntent() == ProcessMessageSubscriptionIntent.REOPEN) {
      reopenFromManifest(command);
      return;
    }
    handleCreateAcknowledgement(command);
  }

  private void handleCreateAcknowledgement(
      final TypedRecord<ProcessMessageSubscriptionRecord> command) {
    final ProcessMessageSubscriptionRecord subscriptionRecord = command.getValue();
    final long elementInstanceKey = subscriptionRecord.getElementInstanceKey();
    final String tenantId = subscriptionRecord.getTenantId();
    final String messageName = subscriptionRecord.getMessageName();
    final ProcessMessageSubscription subscription =
        subscriptionState.getSubscription(
            elementInstanceKey, subscriptionRecord.getMessageNameBuffer(), tenantId);

    if (subscription != null && subscription.isOpening()) {
      // Propagate the message-side subscription key received in the ack so the PI-side record
      // stores it; the suspend/close paths read it back when sending the close command. Copy the
      // stored record into a processor-owned scratch instance before mutating: the value returned
      // by the state API is backed by the column family's shared mutable object.
      eventRecord.wrap(subscription.getRecord());
      eventRecord.setSubscriptionKey(subscriptionRecord.getSubscriptionKey());

      if (suspensionState.getSuspensionState(subscription.getRecord().getProcessInstanceKey())
          == SuspensionState.State.SUSPENDED) {
        // The instance was suspended while this subscription was still mid-handshake, so the
        // suspend pass skipped it (there was no confirmed message-side row to close yet). The
        // handshake has now completed, so a live message-side subscription exists on a suspended
        // instance — exactly what suspension must avoid. Close it immediately (DELETING → CLOSING,
        // send the close, enroll for retry) so its delete ack drains it to the OPENED resume
        // manifest, giving the late handshake a proper completion path instead of leaving an
        // OPENING row that retries CREATE for the whole suspension.
        //
        // Gated on the exact SUSPENDED state, not isSuspended(): during RESUMING, reopened
        // subscriptions are expected to complete their handshake normally, not be closed again.
        closeLateHandshake(subscription.getKey(), elementInstanceKey, messageName, tenantId);
      } else {
        stateWriter.appendFollowUpEvent(
            subscription.getKey(), ProcessMessageSubscriptionIntent.CREATED, eventRecord);

        // update transient state in a side-effect to ensure that these changes only take effect
        // after the command has been successfully processed
        sideEffectWriter.appendSideEffect(
            () ->
                transientProcessMessageSubscriptionState.remove(
                    new PendingSubscription(elementInstanceKey, messageName, tenantId)));
      }
    } else {
      rejectCommand(command, subscription);
    }
  }

  private void closeLateHandshake(
      final long subscriptionKey,
      final long elementInstanceKey,
      final String messageName,
      final String tenantId) {
    // Capture scalars before emitting DELETING: the DELETING applier calls updateToClosingState,
    // which re-reads the shared subscription object from DB and can reset the shared mutable
    // record.
    final int partitionId = eventRecord.getSubscriptionPartitionId();
    final long piKey = eventRecord.getProcessInstanceKey();
    final long pdKey = eventRecord.getProcessDefinitionKey();
    final long subKey = eventRecord.getSubscriptionKey();

    stateWriter.appendFollowUpEvent(
        subscriptionKey,
        ProcessMessageSubscriptionIntent.DELETING,
        eventRecord.setClosedForSuspend(true));

    final var pending = new PendingSubscription(elementInstanceKey, messageName, tenantId);
    sideEffectWriter.appendSideEffect(
        () -> transientProcessMessageSubscriptionState.update(pending, clock.millis()));
    sideEffectWriter.appendSideEffect(
        () ->
            subscriptionCommandSender.sendDirectCloseMessageSubscription(
                partitionId,
                piKey,
                elementInstanceKey,
                pdKey,
                BufferUtil.wrapString(messageName),
                tenantId,
                subKey));
  }

  /**
   * Re-subscribes one message subscription from its durable resume manifest, for a {@code REOPEN}
   * command drained from the suspend/resume buffer. Draining one {@code REOPEN} per batch keeps a
   * large instance's resume from re-subscribing every row in one oversized record batch.
   */
  private void reopenFromManifest(final TypedRecord<ProcessMessageSubscriptionRecord> command) {
    final var commandValue = command.getValue();
    final long elementInstanceKey = commandValue.getElementInstanceKey();
    final String messageName = commandValue.getMessageName();
    final String tenantId = commandValue.getTenantId();

    final ProcessMessageSubscription subscription =
        subscriptionState.getSubscription(
            elementInstanceKey, commandValue.getMessageNameBuffer(), tenantId);

    if (subscription == null) {
      final var reason = String.format(REOPEN_NO_SUBSCRIPTION_MSG, elementInstanceKey, messageName);
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, reason);
      return;
    }
    if (subscription.isOpening() || subscription.isClosing()) {
      // REOPEN is buffered only once the close ack restores OPENED, so a non-OPENED row is
      // unexpected: reject rather than double-open an OPENING row or clobber a CLOSING one.
      final var state = subscription.isClosing() ? "closing" : "opening";
      final var reason =
          String.format(REOPEN_NOT_MANIFEST_MSG, elementInstanceKey, messageName, state);
      rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, reason);
      return;
    }

    final var record = subscription.getRecord();
    // Capture fields before CREATING: its applier re-reads and can reset the shared record.
    final int partitionId = record.getSubscriptionPartitionId();
    final long piKey = record.getProcessInstanceKey();
    final long pdKey = record.getProcessDefinitionKey();
    final String bpmnProcessId = record.getBpmnProcessId();
    final String correlationKey = record.getCorrelationKey();
    final boolean interrupting = record.isInterrupting();
    final String businessId = record.getBusinessId();
    final String elementId = record.getElementId();
    final long rootPiKey = record.getRootProcessInstanceKey();
    final var elementType = record.getElementType();

    // Reset the generation to -1: re-subscribing makes the message partition assign a fresh key.
    // Until that key is confirmed (by the open ack, or the CORRELATE sent instead on the
    // buffered-message path) the old key would make that CORRELATE be rejected as stale. Copy into
    // the scratch record so the shared state object is not mutated in place.
    eventRecord.wrap(record);
    eventRecord.setSubscriptionKey(-1L);
    stateWriter.appendFollowUpEvent(
        subscription.getKey(), ProcessMessageSubscriptionIntent.CREATING, eventRecord);

    sideEffectWriter.appendSideEffect(
        () ->
            subscriptionCommandSender.sendDirectOpenMessageSubscription(
                partitionId,
                piKey,
                elementInstanceKey,
                pdKey,
                BufferUtil.wrapString(bpmnProcessId),
                BufferUtil.wrapString(messageName),
                BufferUtil.wrapString(correlationKey),
                interrupting,
                tenantId,
                BufferUtil.wrapString(businessId),
                BufferUtil.wrapString(elementId),
                rootPiKey,
                elementType));
  }

  private void rejectCommand(
      final TypedRecord<ProcessMessageSubscriptionRecord> command,
      final ProcessMessageSubscription subscription) {
    final var record = command.getValue();
    final var elementInstanceKey = record.getElementInstanceKey();
    final String messageName = BufferUtil.bufferAsString(record.getMessageNameBuffer());

    if (subscription == null) {
      final var reason =
          String.format(NO_SUBSCRIPTION_FOUND_MESSAGE, elementInstanceKey, messageName);
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, reason);

    } else {
      final String state = subscription.isClosing() ? "closing" : "opened";
      final var reason = String.format(NOT_OPENING_MSG, elementInstanceKey, messageName, state);
      rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, reason);
    }
  }
}
