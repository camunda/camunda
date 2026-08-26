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
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState.PendingSubscription;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.InstantSource;
import java.util.ArrayDeque;
import org.jspecify.annotations.NullMarked;

/**
 * Closes the message subscriptions of a process instance while it is suspended, mirroring {@link
 * ProcessInstanceSuspensionJobBehavior} for jobs.
 */
@NullMarked
public final class ProcessInstanceSuspensionMessageSubscriptionBehavior {

  private final ElementInstanceState elementInstanceState;
  private final ProcessMessageSubscriptionState processMessageSubscriptionState;
  private final StateWriter stateWriter;
  private final SideEffectWriter sideEffectWriter;
  private final SubscriptionCommandSender subscriptionCommandSender;
  private final TransientPendingSubscriptionState transientProcessMessageSubscriptionState;
  private final InstantSource clock;

  public ProcessInstanceSuspensionMessageSubscriptionBehavior(
      final ElementInstanceState elementInstanceState,
      final ProcessMessageSubscriptionState processMessageSubscriptionState,
      final StateWriter stateWriter,
      final SideEffectWriter sideEffectWriter,
      final SubscriptionCommandSender subscriptionCommandSender,
      final TransientPendingSubscriptionState transientProcessMessageSubscriptionState,
      final InstantSource clock) {
    this.elementInstanceState = elementInstanceState;
    this.processMessageSubscriptionState = processMessageSubscriptionState;
    this.stateWriter = stateWriter;
    this.sideEffectWriter = sideEffectWriter;
    this.subscriptionCommandSender = subscriptionCommandSender;
    this.transientProcessMessageSubscriptionState = transientProcessMessageSubscriptionState;
    this.clock = clock;
  }

  /**
   * Walks the element-instance tree BFS and initiates a durable, retryable close of every {@code
   * OPENED} process message subscription.
   *
   * <p>For each subscription this emits a {@code DELETING} event (putting the PI-side row into
   * {@code CLOSING} state), enqueues the subscription in the pending-retry transient index, and
   * sends the {@code MESSAGE_SUBSCRIPTION.DELETE} command to the message partition as a
   * side-effect. On failover, {@link
   * io.camunda.zeebe.engine.state.message.DbProcessMessageSubscriptionState#onRecovered} re-adds
   * all {@code CLOSING} rows to the transient index so the {@link
   * PendingProcessMessageSubscriptionCheckScheduler} picks them up and resends the close command.
   *
   * <p>When the delete ack arrives, {@link ProcessMessageSubscriptionDeleteProcessor} detects that
   * the instance is still suspended and emits {@code CREATED} (transitioning the row back to {@code
   * OPENED}) instead of {@code DELETED}, preserving it as a resume manifest.
   *
   * <p>Subscriptions in {@code OPENING} or {@code CLOSING} state are skipped: {@code OPENING} ones
   * are mid-handshake (the message-side row may not exist yet; the CREATE ack handler will close it
   * when the instance is still suspended), and {@code CLOSING} ones are already being torn down.
   *
   * <p>All closes are emitted in this single SUSPEND batch, so an instance with more concurrent
   * message subscriptions than fit in one record batch (~10K) would exceed the limit and be
   * rejected. Chunking the closes across cycles is deferred — same limitation as job suspension;
   * see <a href="https://github.com/camunda/camunda/issues/61057">#61057</a>.
   */
  public void closeSubscriptions(final long processInstanceKey) {
    final var root = elementInstanceState.getInstance(processInstanceKey);
    if (root == null) {
      return;
    }
    final var queue = new ArrayDeque<ElementInstance>();
    queue.add(root);
    while (!queue.isEmpty()) {
      final var elementInstance = queue.poll();
      processMessageSubscriptionState.visitElementSubscriptions(
          elementInstance.getKey(),
          subscription -> {
            if (!subscription.isOpening() && !subscription.isClosing()) {
              final var record = subscription.getRecord();
              // Capture subscriptionKey before emitting the DELETING event: the DELETING applier
              // calls updateToClosingState, which re-reads the shared subscription object from DB
              // and can reset subscriptionKey to -1 on the shared mutable record instance.
              final int partitionId = record.getSubscriptionPartitionId();
              final long piKey = record.getProcessInstanceKey();
              final long elemKey = record.getElementInstanceKey();
              final long pdKey = record.getProcessDefinitionKey();
              final long subKey = record.getSubscriptionKey();
              final String messageName = record.getMessageName();
              final String tenantId = record.getTenantId();

              stateWriter.appendFollowUpEvent(
                  subscription.getKey(), ProcessMessageSubscriptionIntent.DELETING, record);

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
                          subKey));
            }
            return true;
          });
      elementInstanceState.getChildren(elementInstance.getKey()).stream()
          .filter(child -> child.getValue().getProcessInstanceKey() == processInstanceKey)
          .forEach(queue::add);
    }
  }
}
