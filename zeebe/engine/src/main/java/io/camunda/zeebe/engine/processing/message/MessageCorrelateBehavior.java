/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.MessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agrona.DirectBuffer;

public final class MessageCorrelateBehavior {

  private final MessageStartEventSubscriptionState startEventSubscriptionState;
  private final MessageSubscriptionState messageSubscriptionState;
  private final MessageState messageState;
  private final EventHandle eventHandle;
  private final StateWriter stateWriter;
  private final SubscriptionCommandSender commandSender;

  public MessageCorrelateBehavior(
      final MessageStartEventSubscriptionState startEventSubscriptionState,
      final MessageState messageState,
      final EventHandle eventHandle,
      final StateWriter stateWriter,
      final MessageSubscriptionState messageSubscriptionState,
      final SubscriptionCommandSender commandSender) {
    this.startEventSubscriptionState = startEventSubscriptionState;
    this.messageSubscriptionState = messageSubscriptionState;
    this.messageState = messageState;
    this.eventHandle = eventHandle;
    this.stateWriter = stateWriter;
    this.commandSender = commandSender;
  }

  public Subscriptions correlateToMessageStartEvents(final MessageData messageData) {
    final var correlatingSubscriptions = new Subscriptions();

    startEventSubscriptionState.visitSubscriptionsByMessageName(
        messageData.tenantId(),
        messageData.messageName(),
        subscription -> {
          final var subscriptionRecord = subscription.getRecord();
          final var bpmnProcessIdBuffer = subscriptionRecord.getBpmnProcessIdBuffer();

          // create only one instance of a process per correlation key
          // - allow multiple instance if correlation key is empty
          if (!correlatingSubscriptions.contains(bpmnProcessIdBuffer)
              && (messageData.correlationKey().capacity() == 0
                  || !messageState.existActiveProcessInstance(
                      messageData.tenantId(), bpmnProcessIdBuffer, messageData.correlationKey()))) {

            final var processInstanceKey =
                eventHandle.triggerMessageStartEvent(
                    subscription.getKey(),
                    subscriptionRecord,
                    messageData.messageKey(),
                    messageData.messageName(),
                    messageData.correlationKey(),
                    messageData.variables());

            subscriptionRecord.setProcessInstanceKey(processInstanceKey);
            correlatingSubscriptions.add(subscriptionRecord);
          }
        });

    return correlatingSubscriptions;
  }

  public Subscriptions correlateToMessageEvents(final MessageData messageData) {
    final var correlatingSubscriptions = new Subscriptions();
    final var hasResponded = new AtomicBoolean(false);

    messageSubscriptionState.visitSubscriptions(
        messageData.tenantId(),
        messageData.messageName(),
        messageData.correlationKey(),
        subscription -> {

          // correlate the message only once per process
          if (!subscription.isCorrelating()
              && !correlatingSubscriptions.contains(
                  subscription.getRecord().getBpmnProcessIdBuffer())) {

            final var correlatingSubscription =
                subscription
                    .getRecord()
                    .setMessageKey(messageData.messageKey())
                    .setVariables(messageData.variables());

            // Respond only for one subscription if we have request data available.
            final var hasRequestData =
                messageData.requestId().isPresent() && messageData.requestStreamId().isPresent();
            if (hasRequestData && !hasResponded.get()) {
              correlatingSubscription
                  .setRequestId(messageData.requestId().get())
                  .setRequestStreamId(messageData.requestStreamId().get());
              hasResponded.set(true);
            }

            stateWriter.appendFollowUpEvent(
                subscription.getKey(),
                MessageSubscriptionIntent.CORRELATING,
                correlatingSubscription);

            correlatingSubscriptions.add(correlatingSubscription);

            commandSender.correlateProcessMessageSubscription(
                correlatingSubscription.getProcessInstanceKey(),
                correlatingSubscription.getElementInstanceKey(),
                correlatingSubscription.getBpmnProcessIdBuffer(),
                messageData.messageName(),
                messageData.messageKey(),
                messageData.variables(),
                messageData.correlationKey(),
                messageData.tenantId());
          }

          return true;
        });
    return correlatingSubscriptions;
  }

  public record MessageData(
      long messageKey,
      DirectBuffer messageName,
      DirectBuffer correlationKey,
      DirectBuffer variables,
      String tenantId,
      Optional<Long> requestId,
      Optional<Integer> requestStreamId) {

    MessageData(
        final long messageKey,
        final DirectBuffer messageName,
        final DirectBuffer correlationKey,
        final DirectBuffer variables,
        final String tenantId) {
      this(
          messageKey,
          messageName,
          correlationKey,
          variables,
          tenantId,
          Optional.empty(),
          Optional.empty());
    }

    MessageData(
        final long messageKey,
        final DirectBuffer messageName,
        final DirectBuffer correlationKey,
        final DirectBuffer variables,
        final String tenantId,
        final long requestId,
        final int requestStreamId) {
      this(
          messageKey,
          messageName,
          correlationKey,
          variables,
          tenantId,
          Optional.of(requestId),
          Optional.of(requestStreamId));
    }
  }
}
