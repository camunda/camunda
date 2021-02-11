/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.message;

import io.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.zeebe.engine.state.mutable.MutableMessageStartEventSubscriptionState;
import io.zeebe.engine.state.mutable.MutableMessageState;
import io.zeebe.engine.state.mutable.MutableMessageSubscriptionState;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.MessageIntent;
import io.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.zeebe.protocol.record.intent.MessageSubscriptionIntent;

public final class MessageEventProcessors {

  public static void addMessageProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final ZeebeState zeebeState,
      final SubscriptionCommandSender subscriptionCommandSender,
      final Writers writers) {

    final MutableMessageState messageState = zeebeState.getMessageState();
    final MutableMessageSubscriptionState subscriptionState =
        zeebeState.getMessageSubscriptionState();
    final MutableMessageStartEventSubscriptionState startEventSubscriptionState =
        zeebeState.getMessageStartEventSubscriptionState();
    final MutableEventScopeInstanceState eventScopeInstanceState =
        zeebeState.getEventScopeInstanceState();
    final KeyGenerator keyGenerator = zeebeState.getKeyGenerator();

    typedRecordProcessors
        .onCommand(
            ValueType.MESSAGE,
            MessageIntent.PUBLISH,
            new PublishMessageProcessor(
                messageState,
                subscriptionState,
                startEventSubscriptionState,
                eventScopeInstanceState,
                subscriptionCommandSender,
                keyGenerator))
        .onCommand(
            ValueType.MESSAGE, MessageIntent.EXPIRE, new MessageExpireProcessor(writers.state()))
        .onCommand(
            ValueType.MESSAGE_SUBSCRIPTION,
            MessageSubscriptionIntent.OPEN,
            new OpenMessageSubscriptionProcessor(
                messageState, subscriptionState, subscriptionCommandSender))
        .onCommand(
            ValueType.MESSAGE_SUBSCRIPTION,
            MessageSubscriptionIntent.CORRELATE,
            new CorrelateMessageSubscriptionProcessor(
                messageState, subscriptionState, subscriptionCommandSender))
        .onCommand(
            ValueType.MESSAGE_SUBSCRIPTION,
            MessageSubscriptionIntent.CLOSE,
            new CloseMessageSubscriptionProcessor(subscriptionState, subscriptionCommandSender))
        .onCommand(
            ValueType.MESSAGE_SUBSCRIPTION,
            MessageSubscriptionIntent.REJECT,
            new RejectMessageCorrelationProcessor(
                messageState, subscriptionState, subscriptionCommandSender))
        .onCommand(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            MessageStartEventSubscriptionIntent.OPEN,
            new OpenMessageStartEventSubscriptionProcessor(
                startEventSubscriptionState, zeebeState.getEventScopeInstanceState()))
        .onCommand(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            MessageStartEventSubscriptionIntent.CLOSE,
            new CloseMessageStartEventSubscriptionProcessor(
                startEventSubscriptionState, eventScopeInstanceState))
        .withListener(
            new MessageObserver(messageState, subscriptionState, subscriptionCommandSender));
  }
}
