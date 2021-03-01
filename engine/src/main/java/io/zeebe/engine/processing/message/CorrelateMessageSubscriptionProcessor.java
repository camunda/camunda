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
import io.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.zeebe.engine.state.message.MessageSubscription;
import io.zeebe.engine.state.mutable.MutableMessageState;
import io.zeebe.engine.state.mutable.MutableMessageSubscriptionState;
import io.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.util.function.Consumer;

public final class CorrelateMessageSubscriptionProcessor
    implements TypedRecordProcessor<MessageSubscriptionRecord> {
  public static final String NO_SUBSCRIPTION_FOUND_MESSAGE =
      "Expected to correlate subscription for element with key '%d' and message name '%s', "
          + "but no such message subscription exists";

  private final MutableMessageSubscriptionState subscriptionState;
  private final MessageCorrelator messageCorrelator;

  public CorrelateMessageSubscriptionProcessor(
      final MutableMessageState messageState,
      final MutableMessageSubscriptionState subscriptionState,
      final SubscriptionCommandSender commandSender,
      final Writers writers) {
    this.subscriptionState = subscriptionState;
    messageCorrelator = new MessageCorrelator(messageState, commandSender, writers.state());
  }

  @Override
  public void processRecord(
      final TypedRecord<MessageSubscriptionRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {

    final MessageSubscriptionRecord subscriptionRecord = record.getValue();
    final MessageSubscription subscription =
        subscriptionState.get(
            subscriptionRecord.getElementInstanceKey(), subscriptionRecord.getMessageNameBuffer());

    if (subscription != null) {
      // TODO (saig0): not all values are written in the command (#3346)
      subscriptionRecord
          .setCorrelationKey(subscription.getCorrelationKey())
          .setCloseOnCorrelate(subscription.shouldCloseOnCorrelate());

      streamWriter.appendFollowUpEvent(
          record.getKey(), MessageSubscriptionIntent.CORRELATED, subscriptionRecord);

      if (subscription.shouldCloseOnCorrelate()) {
        subscriptionState.remove(subscription);
      } else {
        subscriptionState.resetSentTime(subscription);
        messageCorrelator.correlateNextMessage(subscriptionRecord, sideEffect);
      }
    } else {
      streamWriter.appendRejection(
          record,
          RejectionType.NOT_FOUND,
          String.format(
              NO_SUBSCRIPTION_FOUND_MESSAGE,
              subscriptionRecord.getElementInstanceKey(),
              BufferUtil.bufferAsString(subscriptionRecord.getMessageNameBuffer())));
    }
  }
}
