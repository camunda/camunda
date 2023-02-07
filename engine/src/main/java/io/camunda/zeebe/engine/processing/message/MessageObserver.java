/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.engine.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.message.DbMessageState;
import io.camunda.zeebe.engine.state.mutable.MutablePendingMessageSubscriptionState;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.streamprocessor.ProcessingScheduleServiceImpl;
import io.camunda.zeebe.streamprocessor.StreamProcessorContext;
import java.time.Duration;

public final class MessageObserver implements StreamProcessorLifecycleAware {

  public static final Duration MESSAGE_TIME_TO_LIVE_CHECK_INTERVAL = Duration.ofSeconds(60);

  public static final Duration SUBSCRIPTION_TIMEOUT = Duration.ofSeconds(10);
  public static final Duration SUBSCRIPTION_CHECK_INTERVAL = Duration.ofSeconds(30);

  private final SubscriptionCommandSender subscriptionCommandSender;
  private final MessageState messageState;
  private final MutablePendingMessageSubscriptionState pendingState;

  private ProcessingScheduleServiceImpl processingSchedulingService;
  private Actor actor;

  public MessageObserver(
      final MessageState messageState,
      final MutablePendingMessageSubscriptionState pendingState,
      final SubscriptionCommandSender subscriptionCommandSender) {
    this.subscriptionCommandSender = subscriptionCommandSender;
    this.messageState = messageState;
    this.pendingState = pendingState;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    final var actorSchedulingService = context.getActorSchedulingService();
    actor =
        Actor.wrap(
            (c) -> {
              startMessageTimeToLiveChecker(c, (StreamProcessorContext) context);
            });
    actorSchedulingService.submitActor(actor);

    final var scheduleService = context.getScheduleService();
    final PendingMessageSubscriptionChecker pendingSubscriptionChecker =
        new PendingMessageSubscriptionChecker(
            subscriptionCommandSender, pendingState, SUBSCRIPTION_TIMEOUT.toMillis());
    scheduleService.runAtFixedRate(SUBSCRIPTION_CHECK_INTERVAL, pendingSubscriptionChecker);
  }

  @Override
  public void onFailed() {
    onClose();
  }

  @Override
  public void onClose() {
    if (actor != null) {
      actor.closeAsync();
    }
    if (processingSchedulingService != null) {
      processingSchedulingService.close();
    }
  }

  private void startMessageTimeToLiveChecker(
      final ActorControl actor, final StreamProcessorContext context) {
    final var logStream = context.getLogStream();
    final var zeebeDb = context.getZeebeDb();
    final var transactionContext = zeebeDb.createContext();
    final var messageState = new DbMessageState(zeebeDb, transactionContext);
    final var timeToLiveChecker = new MessageTimeToLiveChecker(messageState);

    processingSchedulingService =
        new ProcessingScheduleServiceImpl(
            context::getStreamProcessorPhase,
            context.getAbortCondition(),
            logStream::newLogStreamBatchWriter);
    final var future = processingSchedulingService.open(actor);
    actor.runOnCompletion(
        future,
        (v, t) -> {
          processingSchedulingService.runAtFixedRate(
              MESSAGE_TIME_TO_LIVE_CHECK_INTERVAL, timeToLiveChecker);
        });
  }
}
