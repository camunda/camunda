/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.common.EventTriggerBehavior;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Builders;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;

public final class MessageEventProcessors {

  public static void addMessageProcessors(
      final EventTriggerBehavior eventTriggerBehavior,
      final TypedRecordProcessors typedRecordProcessors,
      final MutableZeebeState zeebeState,
      final SubscriptionCommandSender subscriptionCommandSender,
      final Builders builders) {

    final MutableMessageState messageState = zeebeState.getMessageState();
    final MutableMessageSubscriptionState subscriptionState =
        zeebeState.getMessageSubscriptionState();
    final MutableMessageStartEventSubscriptionState startEventSubscriptionState =
        zeebeState.getMessageStartEventSubscriptionState();
    final MutableEventScopeInstanceState eventScopeInstanceState =
        zeebeState.getEventScopeInstanceState();
    final KeyGenerator keyGenerator = zeebeState.getKeyGenerator();
    final var processState = zeebeState.getProcessState();

    typedRecordProcessors
        .onCommand(
            ValueType.MESSAGE,
            MessageIntent.PUBLISH,
            new MessagePublishProcessor(
                messageState,
                subscriptionState,
                startEventSubscriptionState,
                eventScopeInstanceState,
                subscriptionCommandSender,
                keyGenerator,
                builders,
                processState,
                eventTriggerBehavior))
        .onCommand(
            ValueType.MESSAGE, MessageIntent.EXPIRE, new MessageExpireProcessor(builders.state()))
        .onCommand(
            ValueType.MESSAGE_SUBSCRIPTION,
            MessageSubscriptionIntent.CREATE,
            new MessageSubscriptionCreateProcessor(
                messageState, subscriptionState, subscriptionCommandSender, builders, keyGenerator))
        .onCommand(
            ValueType.MESSAGE_SUBSCRIPTION,
            MessageSubscriptionIntent.CORRELATE,
            new MessageSubscriptionCorrelateProcessor(
                messageState, subscriptionState, subscriptionCommandSender, builders))
        .onCommand(
            ValueType.MESSAGE_SUBSCRIPTION,
            MessageSubscriptionIntent.DELETE,
            new MessageSubscriptionDeleteProcessor(
                subscriptionState, subscriptionCommandSender, builders))
        .onCommand(
            ValueType.MESSAGE_SUBSCRIPTION,
            MessageSubscriptionIntent.REJECT,
            new MessageSubscriptionRejectProcessor(
                messageState, subscriptionState, subscriptionCommandSender, builders))
        .withListener(
            new MessageObserver(
                messageState,
                zeebeState.getPendingMessageSubscriptionState(),
                subscriptionCommandSender));
  }
}
