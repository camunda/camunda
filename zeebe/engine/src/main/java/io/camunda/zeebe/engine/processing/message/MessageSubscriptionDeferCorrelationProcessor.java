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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.InstantSource;

/**
 * Handles {@link MessageSubscriptionIntent#DEFER_CORRELATION}, sent by the process instance
 * partition ({@code ProcessMessageSubscriptionCorrelateProcessor}) when the target instance is
 * suspended and correlation cannot proceed.
 *
 * <p>The deferred subscription is reset non-destructively (it stays open, only the in-flight
 * correlation attempt is undone via {@link MessageSubscriptionIntent#CORRELATION_DEFERRED}) and,
 * unless this attempt was itself the result of a redirect, one attempt is made to redirect the
 * message to a ready sibling subscription of the same process sharing the same message name and
 * correlation key. Capping the redirect at depth one prevents two suspended siblings from bouncing
 * the same message between each other forever.
 */
@ExcludeAuthorizationCheck
public final class MessageSubscriptionDeferCorrelationProcessor
    implements TypedRecordProcessor<MessageSubscriptionRecord> {

  private static final String NO_SUBSCRIPTION_FOUND_MESSAGE =
      "Expected to defer correlation for subscription with element key '%d' and message name '%s', "
          + "but no such message subscription exists";

  private final MessageSubscriptionState subscriptionState;
  private final MessageCorrelator messageCorrelator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;

  public MessageSubscriptionDeferCorrelationProcessor(
      final int partitionId,
      final MessageState messageState,
      final MessageSubscriptionState subscriptionState,
      final SubscriptionCommandSender commandSender,
      final Writers writers,
      final InstantSource clock) {
    this.subscriptionState = subscriptionState;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    messageCorrelator =
        new MessageCorrelator(
            partitionId, messageState, commandSender, stateWriter, writers.sideEffect(), clock);
  }

  @Override
  public void processRecord(final TypedRecord<MessageSubscriptionRecord> command) {
    final var value = command.getValue();
    final var subscription =
        subscriptionState.get(value.getElementInstanceKey(), value.getMessageNameBuffer());

    if (subscription == null) {
      // the subscription was concurrently closed (e.g. element terminated) - nothing to defer
      rejectionWriter.appendRejection(
          command,
          RejectionType.NOT_FOUND,
          NO_SUBSCRIPTION_FOUND_MESSAGE.formatted(
              value.getElementInstanceKey(),
              BufferUtil.bufferAsString(value.getMessageNameBuffer())));
      return;
    }

    // read before appending: the CORRELATION_DEFERRED applier resets this same persisted
    // subscription in place, so its shared buffer must not be read again afterwards
    final boolean alreadyRedirected = subscription.getRecord().isRedirected();

    stateWriter.appendFollowUpEvent(
        subscription.getKey(), MessageSubscriptionIntent.CORRELATION_DEFERRED, value);

    if (!alreadyRedirected) {
      redirectToSibling(value, subscription.getKey());
    }
  }

  /**
   * Looks for one other, non-correlating subscription of the same process sharing the deferred
   * subscription's message name and correlation key, and asks it to try correlating its next
   * available message. Stops at the first candidate found, whether or not it had a correlatable
   * message - this is a best-effort nudge, not a scan of every sibling.
   */
  private void redirectToSibling(
      final MessageSubscriptionRecord deferred, final long deferredSubscriptionKey) {
    subscriptionState.visitSubscriptions(
        deferred.getTenantId(),
        deferred.getMessageNameBuffer(),
        deferred.getCorrelationKeyBuffer(),
        candidate -> {
          final boolean isSibling =
              candidate.getKey() != deferredSubscriptionKey
                  && !candidate.isCorrelating()
                  && candidate
                      .getRecord()
                      .getBpmnProcessIdBuffer()
                      .equals(deferred.getBpmnProcessIdBuffer());
          if (isSibling) {
            messageCorrelator.correlateNextMessage(candidate.getKey(), candidate.getRecord(), true);
          }
          return !isSibling;
        });
  }
}
