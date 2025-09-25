/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.message;

import io.camunda.zeebe.engine.common.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.common.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.common.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.common.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.common.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.common.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.common.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.common.state.immutable.MessageState;
import io.camunda.zeebe.engine.common.state.immutable.MessageSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.InstantSource;

@ExcludeAuthorizationCheck
public final class MessageSubscriptionCreateProcessor
    implements TypedRecordProcessor<MessageSubscriptionRecord> {

  private static final String SUBSCRIPTION_ALREADY_OPENED_MESSAGE =
      "Expected to open a new message subscription for element with key '%d' and message name '%s', "
          + "but there is already a message subscription for that element key and message name opened";

  private final MessageCorrelator messageCorrelator;
  private final MessageSubscriptionState subscriptionState;
  private final SubscriptionCommandSender commandSender;
  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;

  private MessageSubscriptionRecord subscriptionRecord;
  private final TypedRejectionWriter rejectionWriter;
  private final SideEffectWriter sideEffectWriter;
  private final int currentPartitionId;

  public MessageSubscriptionCreateProcessor(
      final int partitionId,
      final MessageState messageState,
      final MessageSubscriptionState subscriptionState,
      final SubscriptionCommandSender commandSender,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final InstantSource clock) {
    this.subscriptionState = subscriptionState;
    this.commandSender = commandSender;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    sideEffectWriter = writers.sideEffect();
    this.keyGenerator = keyGenerator;
    messageCorrelator =
        new MessageCorrelator(
            partitionId, messageState, commandSender, stateWriter, sideEffectWriter, clock);
    currentPartitionId = partitionId;
  }

  @Override
  public void processRecord(final TypedRecord<MessageSubscriptionRecord> record) {
    subscriptionRecord = record.getValue();

    if (subscriptionState.existSubscriptionForElementInstance(
        subscriptionRecord.getElementInstanceKey(), subscriptionRecord.getMessageNameBuffer())) {
      sendAcknowledgeCommand();

      rejectionWriter.appendRejection(
          record,
          RejectionType.INVALID_STATE,
          String.format(
              SUBSCRIPTION_ALREADY_OPENED_MESSAGE,
              subscriptionRecord.getElementInstanceKey(),
              BufferUtil.bufferAsString(subscriptionRecord.getMessageNameBuffer())));
      return;
    }

    handleNewSubscription(sideEffectWriter);
  }

  private void handleNewSubscription(final SideEffectWriter sideEffectWriter) {

    final var subscriptionKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(
        subscriptionKey, MessageSubscriptionIntent.CREATED, subscriptionRecord);

    final var isMessageCorrelated =
        messageCorrelator.correlateNextMessage(subscriptionKey, subscriptionRecord);

    if (!isMessageCorrelated) {
      sendAcknowledgeCommand();
    }
  }

  private boolean sendAcknowledgeCommand() {
    return commandSender.openProcessMessageSubscription(
        subscriptionRecord.getProcessInstanceKey(),
        subscriptionRecord.getElementInstanceKey(),
        subscriptionRecord.getMessageNameBuffer(),
        subscriptionRecord.isInterrupting(),
        subscriptionRecord.getTenantId());
  }
}
