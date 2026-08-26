/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState.PendingSubscription;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.BufferedCommandRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;

@ExcludeAuthorizationCheck
public final class ProcessMessageSubscriptionDeleteProcessor
    implements TypedRecordProcessor<ProcessMessageSubscriptionRecord> {

  private static final String NO_SUBSCRIPTION_FOUND_MESSAGE =
      "Expected to delete process message subscription for element with key '%d' and message name '%s', "
          + "but no such subscription was found.";
  private static final String NOT_CLOSING_MESSAGE =
      "Expected to delete process message subscription for element with key '%d' and message name"
          + " '%s', but it is not in CLOSING state.";

  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final SideEffectWriter sideEffectWriter;
  private final TypedCommandWriter commandWriter;
  private final ProcessMessageSubscriptionState subscriptionState;
  private final SuspensionState suspensionState;
  private final ElementInstanceState elementInstanceState;
  private final TransientPendingSubscriptionState transientProcessMessageSubscriptionState;
  private final KeyGenerator keyGenerator;

  // Scratch copy of the manifest, taken before CREATED (whose applier re-reads the shared record),
  // so the REOPEN command we buffer keeps the right field values.
  private final ProcessMessageSubscriptionRecord reopenScratch =
      new ProcessMessageSubscriptionRecord();

  public ProcessMessageSubscriptionDeleteProcessor(
      final ProcessMessageSubscriptionState subscriptionState,
      final SuspensionState suspensionState,
      final ElementInstanceState elementInstanceState,
      final Writers writers,
      final TransientPendingSubscriptionState transientProcessMessageSubscriptionState,
      final KeyGenerator keyGenerator) {
    this.subscriptionState = subscriptionState;
    this.suspensionState = suspensionState;
    this.elementInstanceState = elementInstanceState;
    this.transientProcessMessageSubscriptionState = transientProcessMessageSubscriptionState;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    sideEffectWriter = writers.sideEffect();
    commandWriter = writers.command();
  }

  @Override
  public void processRecord(final TypedRecord<ProcessMessageSubscriptionRecord> command) {

    final ProcessMessageSubscriptionRecord subscriptionRecord = command.getValue();
    final long elementInstanceKey = subscriptionRecord.getElementInstanceKey();
    final String messageName = subscriptionRecord.getMessageName();
    final String tenantId = subscriptionRecord.getTenantId();
    final var subscription =
        subscriptionState.getSubscription(
            elementInstanceKey, subscriptionRecord.getMessageNameBuffer(), tenantId);

    if (subscription == null) {
      rejectCommand(command);
      return;
    }

    if (!subscription.isClosing()) {
      // DELETE ack for a row never put into CLOSING — a protocol violation; emit a terminal
      // rejection so replay is deterministic rather than a silent gap.
      final var reason = String.format(NOT_CLOSING_MESSAGE, elementInstanceKey, messageName);
      rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, reason);
      return;
    }

    final long processInstanceKey = subscription.getRecord().getProcessInstanceKey();
    if (isSuspendDrivenClose(processInstanceKey, elementInstanceKey)) {
      // Restore the PI-side row to OPENED as the durable resume manifest and buffer a REOPEN so the
      // drain re-subscribes it one row per batch on resume. Snapshot before CREATED, whose applier
      // re-reads the shared record.
      reopenScratch.wrap(subscription.getRecord());
      stateWriter.appendFollowUpEvent(
          subscription.getKey(),
          ProcessMessageSubscriptionIntent.CREATED,
          subscription.getRecord());
      bufferReopenCommand(subscription.getKey(), reopenScratch);
      retriggerDrainIfResuming(processInstanceKey, reopenScratch);
    } else {
      stateWriter.appendFollowUpEvent(
          subscription.getKey(),
          ProcessMessageSubscriptionIntent.DELETED,
          subscription.getRecord());
      // A non-suspend close (e.g. a buffered command terminating the element mid-resume) can clear
      // the last CLOSING row while the instance is RESUMING and the drain is parked waiting on it.
      // Re-wake the drain here too, otherwise the instance stays RESUMING forever. Self-gates on
      // RESUMING, so it is a no-op for the common unsuspended close.
      retriggerDrainIfResuming(processInstanceKey, subscription.getRecord());
    }

    // update transient state in a side-effect to ensure that these changes only take effect after
    // the command has been successfully processed
    sideEffectWriter.appendSideEffect(
        () -> {
          transientProcessMessageSubscriptionState.remove(
              new PendingSubscription(elementInstanceKey, messageName, tenantId));
        });
  }

  /**
   * DELETING (→ CLOSING) is emitted by normal unsubscribe, migration, cancellation, and the suspend
   * path. Only the suspend path runs while the instance is suspended <em>and</em> its element is
   * still active — the others run unsuspended or while the element is already ending — so those two
   * conditions identify a suspend-driven close, whose ack must restore the manifest, not delete it.
   */
  private boolean isSuspendDrivenClose(
      final long processInstanceKey, final long elementInstanceKey) {
    if (!suspensionState.isSuspended(processInstanceKey)) {
      return false;
    }
    final var elementInstance = elementInstanceState.getInstance(elementInstanceKey);
    return elementInstance != null && elementInstance.isActive();
  }

  /**
   * Buffers a {@code REOPEN} for the restored manifest row (mirroring {@code
   * CommandBufferingBehavior}); the drain replays it one row per batch on resume. Consumed exactly
   * once (drain writes {@code DRAINED}), so a later CREATE ack flipping the row's state cannot
   * re-reopen it — hence no resume cursor is needed.
   */
  private void bufferReopenCommand(
      final long subscriptionKey, final ProcessMessageSubscriptionRecord manifest) {
    final var bufferedCommandRecord =
        new BufferedCommandRecord()
            .setProcessInstanceKey(manifest.getProcessInstanceKey())
            .setProcessDefinitionKey(manifest.getProcessDefinitionKey())
            .setTenantId(manifest.getTenantId())
            .setCommandKey(subscriptionKey)
            .setValueType(ValueType.PROCESS_MESSAGE_SUBSCRIPTION)
            .setIntent(ProcessMessageSubscriptionIntent.REOPEN)
            .setCommandValue(manifest);
    stateWriter.appendFollowUpEvent(
        keyGenerator.nextKey(), BufferedCommandIntent.BUFFERED, bufferedCommandRecord);
  }

  /**
   * If the close acks while the instance is already RESUMING, the drain may have emptied the buffer
   * and be waiting; the just-buffered REOPEN needs a fresh {@code DRAIN} to be picked up. The drain
   * re-checks state, so triggering once per draining row is safe.
   */
  private void retriggerDrainIfResuming(
      final long processInstanceKey, final ProcessMessageSubscriptionRecord manifest) {
    if (suspensionState.getSuspensionState(processInstanceKey) != SuspensionState.State.RESUMING) {
      return;
    }
    commandWriter.appendFollowUpCommand(
        processInstanceKey,
        BufferedCommandIntent.DRAIN,
        new BufferedCommandRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setProcessDefinitionKey(manifest.getProcessDefinitionKey())
            .setTenantId(manifest.getTenantId()));
  }

  private void rejectCommand(final TypedRecord<ProcessMessageSubscriptionRecord> command) {
    final var reason =
        String.format(
            NO_SUBSCRIPTION_FOUND_MESSAGE,
            command.getValue().getElementInstanceKey(),
            BufferUtil.bufferAsString(command.getValue().getMessageNameBuffer()));

    rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, reason);
  }
}
