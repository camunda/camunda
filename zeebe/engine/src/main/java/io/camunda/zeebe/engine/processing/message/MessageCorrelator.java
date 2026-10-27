/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.message.StoredMessage;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.InstantSource;
import org.agrona.collections.MutableBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MessageCorrelator {

  private static final Logger LOG = LoggerFactory.getLogger(MessageCorrelator.class);

  private final MessageState messageState;
  private final SubscriptionCommandSender commandSender;
  private final StateWriter stateWriter;
  private final SideEffectWriter sideEffectWriter;
  private final int currentPartitionId;
  private final InstantSource clock;

  public MessageCorrelator(
      final int currentPartitionId,
      final MessageState messageState,
      final SubscriptionCommandSender commandSender,
      final StateWriter stateWriter,
      final SideEffectWriter sideEffectWriter,
      final InstantSource clock) {
    this.currentPartitionId = currentPartitionId;
    this.messageState = messageState;
    this.commandSender = commandSender;
    this.stateWriter = stateWriter;
    this.sideEffectWriter = sideEffectWriter;
    this.clock = clock;
  }

  public boolean correlateNextMessage(
      final long subscriptionKey, final MessageSubscriptionRecord subscriptionRecord) {

    final var isMessageCorrelated = new MutableBoolean(false);

    messageState.visitMessages(
        subscriptionRecord.getTenantId(),
        subscriptionRecord.getMessageNameBuffer(),
        subscriptionRecord.getCorrelationKeyBuffer(),
        storedMessage -> {
          // correlate the first message which is not correlated to the process instance yet
          final var isCorrelated =
              correlateMessage(subscriptionKey, subscriptionRecord, storedMessage);
          isMessageCorrelated.set(isCorrelated);
          return !isCorrelated;
        });

    return isMessageCorrelated.get();
  }

  private boolean correlateMessage(
      final long subscriptionKey,
      final MessageSubscriptionRecord subscriptionRecord,
      final StoredMessage storedMessage) {
    final long messageKey = storedMessage.getMessageKey();
    final var message = storedMessage.getMessage();

    final boolean isCorrelatable =
        message.getDeadline() > clock.millis()
            && !messageState.existMessageCorrelation(
                messageKey, subscriptionRecord.getBpmnProcessIdBuffer());
    if (!isCorrelatable) {
      return false;
    }

    if (!businessIdMatches(message, subscriptionRecord)) {
      LOG.atTrace()
          .addKeyValue("messageKey", messageKey)
          .addKeyValue("subscriptionKey", subscriptionKey)
          .log("Skipping subscription: business ID mismatch");
      return false;
    }

    if (!runScopeMatches(message, subscriptionRecord)) {
      LOG.atTrace()
          .addKeyValue("messageKey", messageKey)
          .addKeyValue("subscriptionKey", subscriptionKey)
          .log("Skipping subscription: run scope mismatch");
      return false;
    }

    subscriptionRecord.setMessageKey(messageKey).setVariables(message.getVariablesBuffer());

    stateWriter.appendFollowUpEvent(
        subscriptionKey, MessageSubscriptionIntent.CORRELATING, subscriptionRecord);

    sendCorrelateCommand(subscriptionRecord);

    return true;
  }

  /**
   * Asymmetric business-id matching rule from the design: a buffered message without a business id
   * correlates regardless of the subscription's stored business id; a buffered message with a
   * business id only correlates to subscriptions whose stored business id matches exactly. The
   * subscription's business id was captured at OPEN time from the process instance, so this stays a
   * local, on-partition check.
   */
  private static boolean businessIdMatches(
      final MessageRecord message, final MessageSubscriptionRecord subscription) {
    final var messageBusinessId = message.getBusinessIdBuffer();
    if (messageBusinessId.capacity() == 0) {
      return true;
    }
    return BufferUtil.equals(messageBusinessId, subscription.getBusinessIdBuffer());
  }

  /**
   * Asymmetric run-scope matching rule, mirroring {@link #businessIdMatches}: a buffered message
   * without a run scope correlates regardless of the subscription's stored run scope; a buffered
   * message with a run scope only correlates to subscriptions whose stored run scope matches
   * exactly. The subscription's run scope was captured at OPEN time from the process instance, so
   * this stays a local, on-partition check.
   */
  private static boolean runScopeMatches(
      final MessageRecord message, final MessageSubscriptionRecord subscription) {
    final var messageRunScope = message.getRunScopeBuffer();
    if (messageRunScope.capacity() == 0) {
      return true;
    }
    return BufferUtil.equals(messageRunScope, subscription.getRunScopeBuffer());
  }

  private boolean sendCorrelateCommand(final MessageSubscriptionRecord subscriptionRecord) {
    return commandSender.correlateProcessMessageSubscription(
        subscriptionRecord.getProcessInstanceKey(),
        subscriptionRecord.getElementInstanceKey(),
        subscriptionRecord.getProcessDefinitionKey(),
        subscriptionRecord.getBpmnProcessIdBuffer(),
        subscriptionRecord.getMessageNameBuffer(),
        subscriptionRecord.getMessageKey(),
        subscriptionRecord.getVariablesBuffer(),
        subscriptionRecord.getCorrelationKeyBuffer(),
        subscriptionRecord.getTenantId(),
        subscriptionRecord.getSubscriptionKey(),
        subscriptionRecord.getStorageOrdinal());
  }
}
