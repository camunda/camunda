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

  public void correlateToMessageStartEvents(
      final MessageData messageData, final Subscriptions correlatingSubscriptions) {
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
  }

  /**
   * Collects message start event subscriptions without writing state changes. Used for
   * authorization checks before actual correlation.
   */
  public void collectMessageStartEventSubscriptions(
      final MessageData messageData, final Subscriptions correlatingSubscriptions) {
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

            // Just collect, don't trigger events yet
            correlatingSubscriptions.add(subscriptionRecord);
          }
        });
  }

  public void correlateToMessageEvents(
      final MessageData messageData, final Subscriptions correlatingSubscriptions) {

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

            stateWriter.appendFollowUpEvent(
                subscription.getKey(),
                MessageSubscriptionIntent.CORRELATING,
                correlatingSubscription);

            correlatingSubscriptions.add(correlatingSubscription);
          }

          return true;
        });
  }

  /**
   * Collects message event subscriptions without writing state changes. Used for authorization
   * checks before actual correlation.
   */
  public void collectMessageEventSubscriptions(
      final MessageData messageData, final Subscriptions correlatingSubscriptions) {
    messageSubscriptionState.visitSubscriptions(
        messageData.tenantId(),
        messageData.messageName(),
        messageData.correlationKey(),
        subscription -> {

          // correlate the message only once per process
          if (!subscription.isCorrelating()
              && !correlatingSubscriptions.contains(
                  subscription.getRecord().getBpmnProcessIdBuffer())) {

            // Just collect, don't write state yet
            correlatingSubscriptions.add(subscription.getRecord());
          }

          return true;
        });
  }

  public void sendCorrelateCommands(
      final MessageData messageData, final Subscriptions correlatingSubscriptions) {
    correlatingSubscriptions.visitSubscriptions(
        subscription ->
            commandSender.correlateProcessMessageSubscription(
                subscription.getProcessInstanceKey(),
                subscription.getElementInstanceKey(),
                subscription.getBpmnProcessId(),
                messageData.messageName(),
                messageData.messageKey(),
                messageData.variables(),
                messageData.correlationKey(),
                messageData.tenantId()));
  }

  public record MessageData(
      long messageKey,
      DirectBuffer messageName,
      DirectBuffer correlationKey,
      DirectBuffer variables,
      String tenantId) {}
}
