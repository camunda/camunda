/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.message;

import io.zeebe.engine.processor.KeyGenerator;
import io.zeebe.engine.processor.TypedRecordProcessors;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.instance.EventScopeInstanceState;
import io.zeebe.engine.state.message.MessageStartEventSubscriptionState;
import io.zeebe.engine.state.message.MessageState;
import io.zeebe.engine.state.message.MessageSubscriptionState;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.MessageIntent;
import io.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.zeebe.protocol.record.intent.MessageSubscriptionIntent;

public class MessageEventProcessors {

  public static void addMessageProcessors(
      TypedRecordProcessors typedRecordProcessors,
      ZeebeState zeebeState,
      SubscriptionCommandSender subscriptionCommandSender) {

    final MessageState messageState = zeebeState.getMessageState();
    final MessageSubscriptionState subscriptionState = zeebeState.getMessageSubscriptionState();
    final MessageStartEventSubscriptionState startEventSubscriptionState =
        zeebeState.getMessageStartEventSubscriptionState();
    final EventScopeInstanceState eventScopeInstanceState =
        zeebeState.getWorkflowState().getEventScopeInstanceState();
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
            ValueType.MESSAGE, MessageIntent.DELETE, new DeleteMessageProcessor(messageState))
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
                startEventSubscriptionState, zeebeState.getWorkflowState()))
        .onCommand(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            MessageStartEventSubscriptionIntent.CLOSE,
            new CloseMessageStartEventSubscriptionProcessor(startEventSubscriptionState))
        .withListener(
            new MessageObserver(messageState, subscriptionState, subscriptionCommandSender));
  }
}
