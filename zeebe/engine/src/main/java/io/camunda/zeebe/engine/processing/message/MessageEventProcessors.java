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
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;
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
    final var elementInstanceState = processingState.getElementInstanceState();
    final var bannedInstanceState = processingState.getBannedInstanceState();
    final var businessIdUniquenessEnabled = config.isBusinessIdUniquenessEnabled();

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
                routingInfo,
                elementInstanceState,
                bannedInstanceState,
                businessIdUniquenessEnabled))
        .onCommand(
            ValueType.MESSAGE_BATCH,
            MessageBatchIntent.EXPIRE,
            new MessageBatchExpireProcessor(
                writers.state(),
                writers.command(),
                messageState,
                config.getMessagesTtlCheckerBatchLimit(),
                featureFlags.enableMessageBodyOnExpired(),
                clock))
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
                authCheckBehavior,
                elementInstanceState,
                bannedInstanceState,
                businessIdUniquenessEnabled,
                routingInfo,
                partitionId))
        .onCommand(
            ValueType.MESSAGE_START_PROCESS_INSTANCE_REQUEST,
            MessageStartProcessInstanceRequestIntent.REQUEST,
            new MessageStartProcessInstanceRequestProcessor(
                startEventSubscriptionState,
                elementInstanceState,
                bannedInstanceState,
                processingState.getMessageStartProcessInstanceDedupState(),
                eventScopeInstanceState,
                processState,
                bpmnBehaviors.eventTriggerBehavior(),
                bpmnBehaviors.stateBehavior(),
                subscriptionCommandSender,
                keyGenerator,
                clock,
                writers))
        .onCommand(
            ValueType.MESSAGE_START_PROCESS_INSTANCE_REQUEST,
            MessageStartProcessInstanceRequestIntent.SWEEP_TOMBSTONES,
            new MessageStartDedupTombstoneSweepProcessor(
                writers.state(),
                writers.command(),
                processingState.getMessageStartProcessInstanceDedupState(),
                config.getMessageStartDedupTombstoneSweepBatchLimit(),
                clock))
        // Reply command processors on P_K - these handle the cross-partition replies from P_B
        .onCommand(
            ValueType.MESSAGE_START_PROCESS_INSTANCE_REQUEST,
            MessageStartProcessInstanceRequestIntent.START,
            new MessageStartProcessInstanceStartReplyProcessor(writers.state(), messageState))
        .onCommand(
            ValueType.MESSAGE_START_PROCESS_INSTANCE_REQUEST,
            MessageStartProcessInstanceRequestIntent.REJECT_UNIQUENESS,
            new MessageStartProcessInstanceUniquenessRejectReplyProcessor(writers.state()))
        .onCommand(
            ValueType.MESSAGE_START_PROCESS_INSTANCE_REQUEST,
            MessageStartProcessInstanceRequestIntent.REJECT_NO_SUBSCRIPTION,
            new MessageStartProcessInstanceNoSubscriptionRejectReplyProcessor(writers.state()))
        .withListener(
            new MessageTimeToLiveCheckScheduler(
                config.getMessagesTtlCheckerInterval(),
                featureFlags.enableMessageTTLCheckerAsync(),
                scheduledTaskStateFactory.get().getMessageState()))
        .withListener(
            new PendingMessageSubscriptionCheckScheduler(
                subscriptionCommandSender,
                scheduledTaskStateFactory.get().getPendingMessageSubscriptionState()))
        .withListener(
            new MessageStartDedupTombstoneSweepScheduler(
                config.getMessageStartDedupTombstoneSweepInterval(),
                scheduledTaskStateFactory.get().getMessageStartProcessInstanceDedupState()))
        .withListener(
            new PendingMessageStartAskCheckScheduler(
                subscriptionCommandSender,
                processingState.getMessageStartProcessInstanceAskState(),
                routingInfo,
                config::getMessageStartAskRetryInterval));
  }
}
