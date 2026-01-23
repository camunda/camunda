/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageCorrelationState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageBatchIntent;
import io.camunda.zeebe.protocol.record.intent.MessageCorrelationIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.FeatureFlags;
import java.time.InstantSource;
import java.util.function.Supplier;

public final class MessageEventProcessors {

  public static void addMessageProcessors(
      final int partitionId,
      final BpmnBehaviors bpmnBehaviors,
      final TypedRecordProcessors typedRecordProcessors,
      final MutableProcessingState processingState,
      final Supplier<ScheduledTaskState> scheduledTaskStateFactory,
      final SubscriptionCommandSender subscriptionCommandSender,
      final Writers writers,
      final EngineConfiguration config,
      final FeatureFlags featureFlags,
      final CommandDistributionBehavior commandDistributionBehavior,
      final InstantSource clock,
      final AuthorizationCheckBehavior authCheckBehavior,
      final RoutingInfo routingInfo) {

    final MutableMessageState messageState = processingState.getMessageState();
    final MutableMessageCorrelationState messageCorrelationState =
        processingState.getMessageCorrelationState();
    final MutableMessageSubscriptionState subscriptionState =
        processingState.getMessageSubscriptionState();
    final MutableMessageStartEventSubscriptionState startEventSubscriptionState =
        processingState.getMessageStartEventSubscriptionState();
    final MutableEventScopeInstanceState eventScopeInstanceState =
        processingState.getEventScopeInstanceState();
    final KeyGenerator keyGenerator = processingState.getKeyGenerator();
    final var processState = processingState.getProcessState();

    typedRecordProcessors
        .onCommand(
            ValueType.MESSAGE,
            MessageIntent.PUBLISH,
            new MessagePublishProcessor(
                partitionId,
                messageState,
                subscriptionState,
                startEventSubscriptionState,
                eventScopeInstanceState,
                subscriptionCommandSender,
                keyGenerator,
                writers,
                processState,
                bpmnBehaviors.eventTriggerBehavior(),
                bpmnBehaviors.stateBehavior(),
                authCheckBehavior,
                routingInfo))
        .onCommand(
            ValueType.MESSAGE_BATCH,
            MessageBatchIntent.EXPIRE,
            new MessageBatchExpireProcessor(
                writers.state(),
                writers.rejection(),
                messageState,
                featureFlags.enableMessageBodyOnExpired()))
        .onCommand(
            ValueType.MESSAGE, MessageIntent.EXPIRE, new MessageExpireProcessor(writers.state()))
        .onCommand(
            ValueType.MESSAGE_SUBSCRIPTION,
            MessageSubscriptionIntent.CREATE,
            new MessageSubscriptionCreateProcessor(
                processingState.getPartitionId(),
                messageState,
                subscriptionState,
                subscriptionCommandSender,
                writers,
                keyGenerator,
                clock))
        .onCommand(
            ValueType.MESSAGE_SUBSCRIPTION,
            MessageSubscriptionIntent.CORRELATE,
            new MessageSubscriptionCorrelateProcessor(
                processingState.getPartitionId(),
                messageState,
                messageCorrelationState,
                subscriptionState,
                subscriptionCommandSender,
                writers,
                clock))
        .onCommand(
            ValueType.MESSAGE_SUBSCRIPTION,
            MessageSubscriptionIntent.DELETE,
            new MessageSubscriptionDeleteProcessor(
                subscriptionState, subscriptionCommandSender, writers))
        .onCommand(
            ValueType.MESSAGE_SUBSCRIPTION,
            MessageSubscriptionIntent.MIGRATE,
            new MessageSubscriptionMigrateProcessor(
                subscriptionState, writers, commandDistributionBehavior))
        .onCommand(
            ValueType.MESSAGE_SUBSCRIPTION,
            MessageSubscriptionIntent.REJECT,
            new MessageSubscriptionRejectProcessor(
                messageState,
                subscriptionState,
                messageCorrelationState,
                subscriptionCommandSender,
                writers))
        .onCommand(
            ValueType.MESSAGE_CORRELATION,
            MessageCorrelationIntent.CORRELATE,
            new MessageCorrelationCorrelateProcessor(
                writers,
                keyGenerator,
                eventScopeInstanceState,
                processState,
                bpmnBehaviors,
                startEventSubscriptionState,
                messageState,
                subscriptionState,
                subscriptionCommandSender,
                authCheckBehavior))
        .withListener(
            new MessageTimeToLiveCheckScheduler(
                config.getMessagesTtlCheckerInterval(),
                config.getMessagesTtlCheckerBatchLimit(),
                featureFlags.enableMessageTTLCheckerAsync(),
                scheduledTaskStateFactory.get().getMessageState()))
        .withListener(
            new PendingMessageSubscriptionCheckScheduler(
                subscriptionCommandSender,
                scheduledTaskStateFactory.get().getPendingMessageSubscriptionState()));
  }
}
