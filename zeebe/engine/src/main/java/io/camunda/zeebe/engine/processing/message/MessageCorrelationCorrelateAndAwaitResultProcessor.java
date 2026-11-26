/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.metrics.MessageCorrelationMetrics;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.identity.authorization.CslTenantCheck;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.BannedInstanceState;
import io.camunda.zeebe.engine.state.immutable.EventScopeInstanceState;
import io.camunda.zeebe.engine.state.immutable.MessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.instance.AwaitProcessInstanceResultMetadata;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageCorrelationRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

/**
 * Variant B1 (spike): correlates like {@code CORRELATE}, but the caller waits for the instance
 * result, as with create-with-result. The wait is stored in the same processing batch that
 * activates the new instance, so the instance cannot complete before the wait exists.
 */
public final class MessageCorrelationCorrelateAndAwaitResultProcessor
    extends MessageCorrelationCorrelateProcessor {

  private static final String CROSS_PARTITION =
      "Expected to correlate message and await its result, but business id '%s' belongs to partition %d, not to partition %d. Awaiting the result is only supported on the partition of the correlation key.";

  private final MutableElementInstanceState mutableElementInstanceState;
  private final RoutingInfo routingInfo;
  private final int partitionId;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  public MessageCorrelationCorrelateAndAwaitResultProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final EventScopeInstanceState eventScopeInstanceState,
      final ProcessState processState,
      final BpmnBehaviors bpmnBehaviors,
      final MessageStartEventSubscriptionState startEventSubscriptionState,
      final MessageState messageState,
      final MessageSubscriptionState messageSubscriptionState,
      final SubscriptionCommandSender commandSender,
      final CslAuthorizationCheck cslCheck,
      final CslTenantCheck tenantCheck,
      final MutableElementInstanceState elementInstanceState,
      final BannedInstanceState bannedInstanceState,
      final boolean businessIdUniquenessEnabled,
      final RoutingInfo routingInfo,
      final int partitionId,
      final MessageCorrelationMetrics metrics) {
    super(
        writers,
        keyGenerator,
        eventScopeInstanceState,
        processState,
        bpmnBehaviors,
        startEventSubscriptionState,
        messageState,
        messageSubscriptionState,
        commandSender,
        cslCheck,
        tenantCheck,
        elementInstanceState,
        bannedInstanceState,
        businessIdUniquenessEnabled,
        routingInfo,
        partitionId,
        metrics);
    mutableElementInstanceState = elementInstanceState;
    this.routingInfo = routingInfo;
    this.partitionId = partitionId;
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
  }

  @Override
  public void processRecord(final TypedRecord<MessageCorrelationRecord> command) {
    final var businessId = command.getValue().getBusinessIdBuffer();
    if (businessId.capacity() != 0) {
      final int businessIdPartition = routingInfo.partitionForCorrelationKey(businessId);
      if (businessIdPartition != partitionId) {
        final var reason =
            CROSS_PARTITION.formatted(
                command.getValue().getBusinessId(), businessIdPartition, partitionId);
        rejectionWriter.appendRejection(command, RejectionType.INVALID_ARGUMENT, reason);
        responseWriter.writeRejectedResponseOnCommand(
            command, RejectionType.INVALID_ARGUMENT, reason);
        return;
      }
    }
    super.processRecord(command);
  }

  @Override
  protected void respondToStartedInstance(
      final TypedRecord<MessageCorrelationRecord> command,
      final long messageKey,
      final MessageCorrelationRecord record) {
    // same call as create-with-result; no response now, BpmnProcessResultSenderBehavior answers
    final var metadata =
        new AwaitProcessInstanceResultMetadata()
            .setRequestId(command.getRequestId())
            .setRequestStreamId(command.getRequestStreamId())
            .setFetchVariables(record.fetchVariables());
    mutableElementInstanceState.setAwaitResultRequestMetadata(
        record.getProcessInstanceKey(), metadata);
  }
}
