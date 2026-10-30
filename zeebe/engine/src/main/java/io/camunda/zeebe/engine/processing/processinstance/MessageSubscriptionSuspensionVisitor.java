/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.ProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.message.ProcessMessageSubscription;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState.PendingSubscription;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.InstantSource;
import org.agrona.collections.MutableBoolean;
import org.jspecify.annotations.NullMarked;

/**
 * Starts a durable, retryable close of every {@code OPENED} process message subscription of the
 * element instance.
 *
 * <p>For each subscription this writes a {@code DELETING} event with {@code closedForSuspend} set,
 * putting the PI-side row into {@code CLOSING} state, enqueues the subscription in the
 * pending-retry transient index, and sends the {@code MESSAGE_SUBSCRIPTION.DELETE} command to the
 * message partition as a side effect. On failover, {@link
 * io.camunda.zeebe.engine.state.message.DbProcessMessageSubscriptionState#onRecovered} re-adds all
 * {@code CLOSING} rows to the transient index so the {@link
 * PendingProcessMessageSubscriptionCheckScheduler} picks them up and resends the close command.
 *
 * <p>When the delete ack arrives, {@link ProcessMessageSubscriptionDeleteProcessor} reads that flag
 * off the row and writes {@code CREATED} (transitioning it back to {@code OPENED}) instead of
 * {@code DELETED}, preserving it as a resume manifest. The ack may arrive after the instance is
 * already {@code SUSPENDED}.
 *
 * <p>Subscriptions in {@code OPENING} or {@code CLOSING} state are skipped: {@code OPENING} ones
 * are mid-handshake (the message-side row may not exist yet; the CREATE ack handler will close it
 * when the instance is still suspending or suspended), and {@code CLOSING} ones are already being
 * torn down. An overlapping or restarted walk therefore skips a subscription whose close is still
 * in flight, but closes again one whose close was already acked (see ADR 0008, D6).
 *
 * <p>Subscriptions of terminating element instances are skipped: the termination removes them, and
 * a close for suspend would restore them as a resume manifest for an element that no longer exists.
 */
@NullMarked
public final class MessageSubscriptionSuspensionVisitor implements ElementInstanceVisitor {

  private final StateWriter stateWriter;
  private final SideEffectWriter sideEffectWriter;
  private final ProcessMessageSubscriptionState processMessageSubscriptionState;
  private final SubscriptionCommandSender subscriptionCommandSender;
  private final TransientPendingSubscriptionState transientProcessMessageSubscriptionState;
  private final InstantSource clock;

  public MessageSubscriptionSuspensionVisitor(
      final StateWriter stateWriter,
      final SideEffectWriter sideEffectWriter,
      final ProcessMessageSubscriptionState processMessageSubscriptionState,
      final SubscriptionCommandSender subscriptionCommandSender,
      final TransientPendingSubscriptionState transientProcessMessageSubscriptionState,
      final InstantSource clock) {
    this.stateWriter = stateWriter;
    this.sideEffectWriter = sideEffectWriter;
    this.processMessageSubscriptionState = processMessageSubscriptionState;
    this.subscriptionCommandSender = subscriptionCommandSender;
    this.transientProcessMessageSubscriptionState = transientProcessMessageSubscriptionState;
    this.clock = clock;
  }

  @Override
  public boolean shouldVisit(final ElementInstance elementInstance) {
    if (elementInstance.isTerminating()) {
      return false;
    }
    final var hasOpenedSubscription = new MutableBoolean(false);
    processMessageSubscriptionState.visitElementSubscriptions(
        elementInstance.getKey(),
        subscription -> {
          // the state visits every subscription regardless of the returned value
          if (isOpened(subscription)) {
            hasOpenedSubscription.set(true);
          }
          return true;
        });
    return hasOpenedSubscription.get();
  }

  @Override
  public void visit(final ElementInstance elementInstance) {
    processMessageSubscriptionState.visitElementSubscriptions(
        elementInstance.getKey(),
        subscription -> {
          if (isOpened(subscription)) {
            close(subscription);
          }
          return true;
        });
  }

  private static boolean isOpened(final ProcessMessageSubscription subscription) {
    return !subscription.isOpening() && !subscription.isClosing();
  }

  private void close(final ProcessMessageSubscription subscription) {
    final var record = subscription.getRecord();
    // Capture subscriptionKey before emitting the DELETING event: the DELETING applier calls
    // updateToClosingState, which re-reads the shared subscription object from DB and can reset
    // subscriptionKey to -1 on the shared mutable record instance.
    final int partitionId = record.getSubscriptionPartitionId();
    final long piKey = record.getProcessInstanceKey();
    final long elemKey = record.getElementInstanceKey();
    final long pdKey = record.getProcessDefinitionKey();
    final long subKey = record.getSubscriptionKey();
    final String messageName = record.getMessageName();
    final String tenantId = record.getTenantId();
    final int ordinal = record.getStorageOrdinal();

    stateWriter.appendFollowUpEvent(
        subscription.getKey(),
        ProcessMessageSubscriptionIntent.DELETING,
        record.setClosedForSuspend(true));

    final var pending = new PendingSubscription(elemKey, messageName, tenantId);
    sideEffectWriter.appendSideEffect(
        () -> transientProcessMessageSubscriptionState.update(pending, clock.millis()));
    sideEffectWriter.appendSideEffect(
        () ->
            subscriptionCommandSender.sendDirectCloseMessageSubscription(
                partitionId,
                piKey,
                elemKey,
                pdKey,
                BufferUtil.wrapString(messageName),
                tenantId,
                subKey,
                ordinal));
  }
}
