/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.message;

import io.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectProducer;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.message.MessageSubscription;
import io.zeebe.engine.state.message.StoredMessage;
import io.zeebe.engine.state.mutable.MutableMessageState;
import io.zeebe.engine.state.mutable.MutableMessageSubscriptionState;
import io.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

public final class RejectMessageCorrelationProcessor
    implements TypedRecordProcessor<MessageSubscriptionRecord> {

  private final MutableMessageState messageState;
  private final MutableMessageSubscriptionState subscriptionState;
  private final SubscriptionCommandSender commandSender;
  private MessageSubscription subscription;

  public RejectMessageCorrelationProcessor(
      final MutableMessageState messageState,
      final MutableMessageSubscriptionState subscriptionState,
      final SubscriptionCommandSender commandSender) {
    this.messageState = messageState;
    this.subscriptionState = subscriptionState;
    this.commandSender = commandSender;
  }

  @Override
  public void processRecord(
      final TypedRecord<MessageSubscriptionRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {

    final MessageSubscriptionRecord subscriptionRecord = record.getValue();
    final long messageKey = subscriptionRecord.getMessageKey();
    final long workflowInstanceKey = subscriptionRecord.getWorkflowInstanceKey();
    final DirectBuffer bpmnProcessId = subscriptionRecord.getBpmnProcessIdBuffer();

    if (!messageState.existMessageCorrelation(messageKey, bpmnProcessId)) {
      streamWriter.appendRejection(
          record,
          RejectionType.INVALID_STATE,
          String.format(
              "Expected message '%d' to be correlated for workflow with BPMN process id '%s' but no correlation was found",
              messageKey, subscriptionRecord.getBpmnProcessId()));
      return;
    }
    messageState.removeMessageCorrelation(messageKey, bpmnProcessId);

    findSubscriptionToCorrelate(sideEffect, subscriptionRecord, messageKey);

    streamWriter.appendFollowUpEvent(
        record.getKey(), MessageSubscriptionIntent.REJECTED, record.getValue());
  }

  private void findSubscriptionToCorrelate(
      final Consumer<SideEffectProducer> sideEffect,
      final MessageSubscriptionRecord subscriptionRecord,
      final long messageKey) {

    // the message TTL may expire after the previous correlation attempt
    final StoredMessage storedMessage = messageState.getMessage(messageKey);
    if (storedMessage == null) {
      return;
    }

    subscriptionState.visitSubscriptions(
        subscriptionRecord.getMessageNameBuffer(),
        subscriptionRecord.getCorrelationKeyBuffer(),
        subscription -> {
          if (subscription.getBpmnProcessId().equals(subscriptionRecord.getBpmnProcessIdBuffer())
              && !subscription.isCorrelating()) {
            subscription.setMessageKey(messageKey);
            subscription.setMessageVariables(storedMessage.getMessage().getVariablesBuffer());

            correlateMessage(subscription, sideEffect);
            return false;
          }
          return true;
        });
  }

  private void correlateMessage(
      final MessageSubscription subscription, final Consumer<SideEffectProducer> sideEffect) {

    subscriptionState.updateToCorrelatingState(
        subscription,
        subscription.getMessageVariables(),
        ActorClock.currentTimeMillis(),
        subscription.getMessageKey());

    messageState.putMessageCorrelation(
        subscription.getMessageKey(), subscription.getBpmnProcessId());

    this.subscription = subscription;
    sideEffect.accept(this::sendCorrelateCommand);
  }

  private boolean sendCorrelateCommand() {
    return commandSender.correlateWorkflowInstanceSubscription(
        subscription.getWorkflowInstanceKey(),
        subscription.getElementInstanceKey(),
        subscription.getBpmnProcessId(),
        subscription.getMessageName(),
        subscription.getMessageKey(),
        subscription.getMessageVariables(),
        subscription.getCorrelationKey());
  }
}
