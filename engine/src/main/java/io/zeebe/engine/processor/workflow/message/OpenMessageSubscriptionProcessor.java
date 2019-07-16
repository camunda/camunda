/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.message;

import io.zeebe.engine.processor.SideEffectProducer;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
import io.zeebe.engine.state.message.MessageState;
import io.zeebe.engine.state.message.MessageSubscription;
import io.zeebe.engine.state.message.MessageSubscriptionState;
import io.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.util.function.Consumer;

public class OpenMessageSubscriptionProcessor
    implements TypedRecordProcessor<MessageSubscriptionRecord> {

  public static final String SUBSCRIPTION_ALREADY_OPENED_MESSAGE =
      "Expected to open a new message subscription for element with key '%d' and message name '%s', "
          + "but there is already a message subscription for that element key and message name opened";
  private final MessageCorrelator messageCorrelator;
  private final MessageSubscriptionState subscriptionState;
  private final SubscriptionCommandSender commandSender;

  private MessageSubscriptionRecord subscriptionRecord;

  public OpenMessageSubscriptionProcessor(
      final MessageState messageState,
      final MessageSubscriptionState subscriptionState,
      final SubscriptionCommandSender commandSender) {
    this.subscriptionState = subscriptionState;
    this.commandSender = commandSender;
    this.messageCorrelator = new MessageCorrelator(messageState, subscriptionState, commandSender);
  }

  @Override
  public void processRecord(
      final TypedRecord<MessageSubscriptionRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {
    subscriptionRecord = record.getValue();

    if (subscriptionState.existSubscriptionForElementInstance(
        subscriptionRecord.getElementInstanceKey(), subscriptionRecord.getMessageNameBuffer())) {
      sideEffect.accept(this::sendAcknowledgeCommand);

      streamWriter.appendRejection(
          record,
          RejectionType.INVALID_STATE,
          String.format(
              SUBSCRIPTION_ALREADY_OPENED_MESSAGE,
              subscriptionRecord.getElementInstanceKey(),
              BufferUtil.bufferAsString(subscriptionRecord.getMessageNameBuffer())));
      return;
    }

    handleNewSubscription(record, streamWriter, sideEffect);
  }

  private void handleNewSubscription(
      final TypedRecord<MessageSubscriptionRecord> record,
      final TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect) {
    final MessageSubscription subscription =
        new MessageSubscription(
            subscriptionRecord.getWorkflowInstanceKey(),
            subscriptionRecord.getElementInstanceKey(),
            subscriptionRecord.getMessageNameBuffer(),
            subscriptionRecord.getCorrelationKeyBuffer(),
            subscriptionRecord.shouldCloseOnCorrelate());

    sideEffect.accept(this::sendAcknowledgeCommand);

    subscriptionState.put(subscription);
    messageCorrelator.correlateNextMessage(subscription, subscriptionRecord, sideEffect);

    streamWriter.appendFollowUpEvent(
        record.getKey(), MessageSubscriptionIntent.OPENED, subscriptionRecord);
  }

  private boolean sendAcknowledgeCommand() {
    return commandSender.openWorkflowInstanceSubscription(
        subscriptionRecord.getWorkflowInstanceKey(),
        subscriptionRecord.getElementInstanceKey(),
        subscriptionRecord.getMessageNameBuffer(),
        subscriptionRecord.shouldCloseOnCorrelate());
  }
}
