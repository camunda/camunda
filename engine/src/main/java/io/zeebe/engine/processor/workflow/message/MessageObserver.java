/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.message;

import io.zeebe.engine.processor.ReadonlyProcessingContext;
import io.zeebe.engine.processor.StreamProcessorLifecycleAware;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
import io.zeebe.engine.state.message.MessageState;
import io.zeebe.engine.state.message.MessageSubscriptionState;
import io.zeebe.util.sched.ActorControl;
import java.time.Duration;

public class MessageObserver implements StreamProcessorLifecycleAware {

  public static final Duration MESSAGE_TIME_TO_LIVE_CHECK_INTERVAL = Duration.ofSeconds(60);

  public static final Duration SUBSCRIPTION_TIMEOUT = Duration.ofSeconds(10);
  public static final Duration SUBSCRIPTION_CHECK_INTERVAL = Duration.ofSeconds(30);

  private final SubscriptionCommandSender subscriptionCommandSender;
  private final MessageState messageState;
  private final MessageSubscriptionState subscriptionState;

  public MessageObserver(
      MessageState messageState,
      MessageSubscriptionState subscriptionState,
      SubscriptionCommandSender subscriptionCommandSender) {
    this.subscriptionCommandSender = subscriptionCommandSender;
    this.messageState = messageState;
    this.subscriptionState = subscriptionState;
  }

  @Override
  public void onOpen(ReadonlyProcessingContext processingContext) {

    final ActorControl actor = processingContext.getActor();

    // it is safe to reuse the write because we running in the same actor/thread
    final MessageTimeToLiveChecker timeToLiveChecker =
        new MessageTimeToLiveChecker(processingContext.getLogStreamWriter(), messageState);
    processingContext
        .getActor()
        .runAtFixedRate(MESSAGE_TIME_TO_LIVE_CHECK_INTERVAL, timeToLiveChecker);

    final PendingMessageSubscriptionChecker pendingSubscriptionChecker =
        new PendingMessageSubscriptionChecker(
            subscriptionCommandSender, subscriptionState, SUBSCRIPTION_TIMEOUT.toMillis());
    actor.runAtFixedRate(SUBSCRIPTION_CHECK_INTERVAL, pendingSubscriptionChecker);
  }
}
