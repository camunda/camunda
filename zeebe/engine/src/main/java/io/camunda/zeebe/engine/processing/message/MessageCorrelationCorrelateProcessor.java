/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.message.MessageCorrelateBehavior.MessageData;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.BannedInstanceState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.EventScopeInstanceState;
import io.camunda.zeebe.engine.state.immutable.MessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageCorrelationRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MessageCorrelationIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class MessageCorrelationCorrelateProcessor
    implements TypedRecordProcessor<MessageCorrelationRecord> {

  private static final String SUBSCRIPTION_NOT_FOUND =
      "Expected to find subscription for message with name '%s' and correlation key '%s', but none was found.";

  private static final String SUBSCRIPTION_BLOCKED_BY_ACTIVE_INSTANCE =
      "Expected to find subscription for message with name '%s' and correlation key '%s', but no "
          + "active subscription was found. A process instance with this correlation key is already active "
          + "for a message start event with this message name in process IDs %s. Only one active "
          + "process instance per correlation key is allowed for message start events.";

  private final MessageCorrelateBehavior correlateBehavior;
  private final KeyGenerator keyGenerator;
  private final CslAuthorizationCheck cslCheck;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;

  public MessageCorrelationCorrelateProcessor(
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
      final ElementInstanceState elementInstanceState,
      final BannedInstanceState bannedInstanceState,
      final boolean businessIdUniquenessEnabled,
      final RoutingInfo routingInfo,
      final int partitionId) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    this.keyGenerator = keyGenerator;
    this.cslCheck = cslCheck;
    final var eventHandle =
        new EventHandle(
            keyGenerator,
            eventScopeInstanceState,
            writers,
            processState,
            bpmnBehaviors.eventTriggerBehavior(),
            bpmnBehaviors.stateBehavior());
    correlateBehavior =
        new MessageCorrelateBehavior(
            startEventSubscriptionState,
            messageState,
            eventHandle,
            stateWriter,
            messageSubscriptionState,
            commandSender,
            elementInstanceState,
            bannedInstanceState,
            businessIdUniquenessEnabled,
            routingInfo,
            partitionId);
  }

  @Override
  public void processRecord(final TypedRecord<MessageCorrelationRecord> command) {
    final var messageCorrelationRecord = command.getValue();

    // Check tenant authorization if not an internal command
    if (!command.isInternalCommand()) {
      final var tenantCheck =
          cslCheck.checkTenant(
              command,
              messageCorrelationRecord.getTenantId(),
              messageCorrelationRecord,
              new Rejection(
                  RejectionType.FORBIDDEN,
                  "Expected to correlate message for tenant '%s', but user is not assigned to this tenant."
                      .formatted(messageCorrelationRecord.getTenantId())));
      if (tenantCheck.isLeft()) {
        final var rejection = tenantCheck.getLeft();
        rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
        responseWriter.writeRejectedResponseOnCommand(
            command, rejection.type(), rejection.reason());
        return;
      }
    }

    final long messageKey = keyGenerator.nextKey();
    messageCorrelationRecord
        .setMessageKey(messageKey)
        .setRequestId(command.getRequestId())
        .setRequestStreamId(command.getRequestStreamId());

    // Collect correlations first without writing to state
    final var correlatingSubscriptions = new Subscriptions();
    final var messageData = createMessageData(messageKey, messageCorrelationRecord);

    // Create a temporary subscriptions collector to check authorization first
    final var tempCorrelatingSubscriptions = new Subscriptions();
    correlateBehavior.collectMessageEventSubscriptions(messageData, tempCorrelatingSubscriptions);
    correlateBehavior.collectMessageStartEventSubscriptions(
        messageData, tempCorrelatingSubscriptions);

    // Check authorization before writing anything to state
    final var authorizationRejectionOptional =
        isAuthorizedForAllSubscriptions(
            command, tempCorrelatingSubscriptions, messageCorrelationRecord.getTenantId());
    if (authorizationRejectionOptional.isPresent()) {
      final var rejection = authorizationRejectionOptional.get();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectedResponseOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    // Now that authorization passed, write the message and correlations to state
    final var messageRecord =
        new MessageRecord()
            .setName(command.getValue().getName())
            .setCorrelationKey(command.getValue().getCorrelationKey())
            .setVariables(command.getValue().getVariablesBuffer())
            .setTenantId(command.getValue().getTenantId())
            .setTimeToLive(-1L);
    stateWriter.appendFollowUpEvent(messageKey, MessageIntent.PUBLISHED, messageRecord);

    stateWriter.appendFollowUpEvent(
        messageKey, MessageCorrelationIntent.CORRELATING, messageCorrelationRecord);

    // Now actually correlate with state writes
    final var blockedProcessIds = new HashSet<String>();
    correlateBehavior.correlateToMessageEvents(messageData, correlatingSubscriptions);
    final var delegatedCrossPartition =
        correlateBehavior.correlateToMessageStartEvents(
            messageData, correlatingSubscriptions, blockedProcessIds);

    if (correlatingSubscriptions.isEmpty()) {
      if (delegatedCrossPartition) {
        // A message-start correlation was delegated to P_B = hash(businessId) because the
        // businessId hashes to a different partition than this one (P_K = hash(correlationKey)).
        // The instance is being created asynchronously on P_B; the synchronous response is
        // therefore deferred. The CORRELATING event applied above stored the request's
        // requestId/requestStreamId keyed by messageKey, and the cross-partition reply processors
        // on P_K (STARTED / UNIQUENESS_REJECTED / NO_SUBSCRIPTION_REJECTED) resolve that pending
        // request into the final CORRELATED / NOT_CORRELATED response. Returning NOT_FOUND here
        // would wrongly report failure while the instance is being created.
        return;
      }
      final String errorMessage;
      if (!blockedProcessIds.isEmpty()) {
        errorMessage =
            SUBSCRIPTION_BLOCKED_BY_ACTIVE_INSTANCE.formatted(
                command.getValue().getName(),
                command.getValue().getCorrelationKey(),
                blockedProcessIds);
      } else {
        errorMessage =
            SUBSCRIPTION_NOT_FOUND.formatted(
                command.getValue().getName(), command.getValue().getCorrelationKey());
      }
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
      responseWriter.writeRejectedResponseOnCommand(command, RejectionType.NOT_FOUND, errorMessage);
      return;
    }

    correlatingSubscriptions
        .getFirstMessageStartEventSubscription()
        .ifPresent(
            subscription -> {
              messageCorrelationRecord.setProcessInstanceKey(subscription.processInstanceKey());
              messageCorrelationRecord.setProcessDefinitionKey(subscription.processDefinitionKey());

              stateWriter.appendFollowUpEvent(
                  messageKey, MessageCorrelationIntent.CORRELATED, messageCorrelationRecord);
              responseWriter.writeAcceptedResponseOnCommand(
                  messageKey,
                  MessageCorrelationIntent.CORRELATED,
                  messageCorrelationRecord,
                  command);
            });

    correlateBehavior.sendCorrelateCommands(messageData, correlatingSubscriptions);

    // Message Correlate command cannot have a TTL. As a result the message expires immediately.
    stateWriter.appendFollowUpEvent(messageKey, MessageIntent.EXPIRED, messageRecord);
  }

  private MessageData createMessageData(
      final long messageKey, final MessageCorrelationRecord messageCorrelationRecord) {
    return new MessageData(
        messageKey,
        messageCorrelationRecord.getNameBuffer(),
        messageCorrelationRecord.getCorrelationKeyBuffer(),
        messageCorrelationRecord.getVariablesBuffer(),
        messageCorrelationRecord.getTenantId(),
        messageCorrelationRecord.getBusinessIdBuffer(),
        // Correlate commands carry no TTL and are not buffered; the cross-partition dedup row's
        // deadline therefore defaults to -1 (already expired). This is safe because the pending-ask
        // on P_K is cleared immediately by the message-expire hook, so no retry ever fires.
        -1L,
        // TTL is 0 (fire-and-forget): with messageDeadline = -1 the expiry guard on P_B rejects
        // only when messageTtl > 0, so a TTL=0 correlate request falls through to live evaluation
        // exactly as before the guard existed.
        0L);
  }

  private Optional<Rejection> isAuthorizedForAllSubscriptions(
      final TypedRecord<MessageCorrelationRecord> command,
      final Subscriptions correlatingSubscriptions,
      final String tenantId) {
    final AtomicReference<Rejection> rejection = new AtomicReference<>();

    final var isAuthorized =
        correlatingSubscriptions.visitSubscriptions(
            subscription -> {
              final PermissionType permissionType =
                  subscription.isStartEventSubscription()
                      ? PermissionType.CREATE_PROCESS_INSTANCE
                      : PermissionType.UPDATE_PROCESS_INSTANCE;

              final var rejectionOrAuthorized =
                  cslCheck.check(
                      command,
                      RequiredAuthorization.of(
                          b ->
                              b.resourceType(
                                      AuthzModelMapper.fromProtocol(
                                          AuthorizationResourceType.PROCESS_DEFINITION))
                                  .permissionType(AuthzModelMapper.fromProtocol(permissionType))
                                  .resourceId(bufferAsString(subscription.bpmnProcessId()))),
                      command.getValue(),
                      AuthorizationRejectionMapper.forbidden(
                          permissionType, AuthorizationResourceType.PROCESS_DEFINITION));
              rejectionOrAuthorized.ifLeft(rejection::set);
              return rejectionOrAuthorized.isRight();
            },
            true);

    if (!isAuthorized) {
      return Optional.of(rejection.get());
    }

    return Optional.empty();
  }
}
