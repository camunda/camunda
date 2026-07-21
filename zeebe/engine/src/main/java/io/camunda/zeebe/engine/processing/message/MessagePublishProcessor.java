/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.common.EventTriggerBehavior;
import io.camunda.zeebe.engine.processing.identity.PermissionsBehavior;
import io.camunda.zeebe.engine.processing.message.MessageCorrelateBehavior.MessageData;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.variable.VariableBehavior;
import io.camunda.zeebe.engine.state.immutable.BannedInstanceState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.EventScopeInstanceState;
import io.camunda.zeebe.engine.state.immutable.MessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public final class MessagePublishProcessor implements TypedRecordProcessor<MessageRecord> {

  private static final String ALREADY_PUBLISHED_MESSAGE =
      "Expected to publish a new message with id '%s', but a message with that id was already published";

  private final int partitionId;
  private final MessageState messageState;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final MessageCorrelateBehavior correlateBehavior;

  private MessageRecord messageRecord;
  private long messageKey;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final PermissionsBehavior permissionsBehavior;
  private final RoutingInfo routingInfo;
  private final VariableBehavior variableBehavior;

  public MessagePublishProcessor(
      final int partitionId,
      final MessageState messageState,
      final MessageSubscriptionState subscriptionState,
      final MessageStartEventSubscriptionState startEventSubscriptionState,
      final EventScopeInstanceState eventScopeInstanceState,
      final SubscriptionCommandSender commandSender,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final ProcessState processState,
      final EventTriggerBehavior eventTriggerBehavior,
      final BpmnStateBehavior stateBehavior,
      final PermissionsBehavior permissionsBehavior,
      final RoutingInfo routingInfo,
      final ElementInstanceState elementInstanceState,
      final BannedInstanceState bannedInstanceState,
      final boolean businessIdUniquenessEnabled,
      final VariableBehavior variableBehavior) {
    this.partitionId = partitionId;
    this.messageState = messageState;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    this.permissionsBehavior = permissionsBehavior;
    this.routingInfo = routingInfo;
    this.variableBehavior = variableBehavior;
    final var eventHandle =
        new EventHandle(
            keyGenerator,
            eventScopeInstanceState,
            writers,
            processState,
            eventTriggerBehavior,
            stateBehavior);
    correlateBehavior =
        new MessageCorrelateBehavior(
            startEventSubscriptionState,
            messageState,
            eventHandle,
            stateWriter,
            subscriptionState,
            commandSender,
            elementInstanceState,
            bannedInstanceState,
            businessIdUniquenessEnabled,
            routingInfo,
            partitionId);
  }

  @Override
  public void processRecord(final TypedRecord<MessageRecord> command) {
    final var isAuthorized =
        permissionsBehavior.isAuthorizedWithResourceIdentifiers(
            command,
            AuthorizationResourceType.MESSAGE,
            PermissionType.CREATE,
            command.getValue().getName());
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectedResponseOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    final var tenantId = command.getValue().getTenantId();
    final var tenantMessage =
        "Expected to perform operation '%s' on resource '%s' for tenant '%s', but user is not assigned to this tenant"
            .formatted(PermissionType.CREATE, AuthorizationResourceType.MESSAGE, tenantId);
    final var tenantCheck =
        permissionsBehavior.checkTenant(
            command,
            tenantId,
            command.getValue(),
            new Rejection(RejectionType.FORBIDDEN, tenantMessage));
    if (tenantCheck.isLeft()) {
      final var rejection = tenantCheck.getLeft();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectedResponseOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    messageRecord = command.getValue();
    final var variables = messageRecord.getVariablesBuffer();
    if (variables.capacity() > 0) {
      final var validation = variableBehavior.validateVariables(variables);
      if (validation.isLeft()) {
        final String reason = validation.getLeft().reason();
        rejectionWriter.appendRejection(command, RejectionType.INVALID_ARGUMENT, reason);
        responseWriter.writeRejectedResponseOnCommand(
            command, RejectionType.INVALID_ARGUMENT, reason);
        return;
      }
    }
    if (routingInfo.partitionForCorrelationKey(messageRecord.getCorrelationKeyBuffer())
        != partitionId) {
      final var reason =
          "The message has not been routed to the right partition. This is probably a temporary issue, please retry in a few seconds";
      rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, reason);
      responseWriter.writeRejectedResponseOnCommand(command, RejectionType.INVALID_STATE, reason);
      return;
    }

    if (messageRecord.hasMessageId()
        && messageState.exist(
            messageRecord.getNameBuffer(),
            messageRecord.getCorrelationKeyBuffer(),
            messageRecord.getMessageIdBuffer(),
            messageRecord.getTenantId())) {
      final String rejectionReason =
          String.format(
              ALREADY_PUBLISHED_MESSAGE, bufferAsString(messageRecord.getMessageIdBuffer()));

      rejectionWriter.appendRejection(command, RejectionType.ALREADY_EXISTS, rejectionReason);
      responseWriter.writeRejectedResponseOnCommand(
          command, RejectionType.ALREADY_EXISTS, rejectionReason);
    } else {
      handleNewMessage(command);
    }
  }

  private void handleNewMessage(final TypedRecord<MessageRecord> command) {
    messageKey = keyGenerator.nextKey();

    // calculate the deadline based on the command's timestamp
    messageRecord.setDeadline(command.getTimestamp() + messageRecord.getTimeToLive());

    stateWriter.appendFollowUpEvent(messageKey, MessageIntent.PUBLISHED, command.getValue());
    responseWriter.writeAcceptedResponseOnCommand(
        messageKey, MessageIntent.PUBLISHED, command.getValue(), command);

    final var correlatingSubscriptions = new Subscriptions();
    final var messageData = createMessageData(messageKey, messageRecord);
    correlateBehavior.correlateToMessageEvents(messageData, correlatingSubscriptions);
    correlateBehavior.correlateToMessageStartEvents(messageData, correlatingSubscriptions);
    correlateBehavior.sendCorrelateCommands(messageData, correlatingSubscriptions);

    if (messageRecord.getTimeToLive() <= 0L) {
      // avoid that the message can be correlated again by writing the EXPIRED event as a follow-up
      stateWriter.appendFollowUpEvent(messageKey, MessageIntent.EXPIRED, messageRecord);
    }
  }

  private MessageData createMessageData(
      final long messageKey, final MessageRecord messageCorrelationRecord) {
    return new MessageData(
        messageKey,
        messageCorrelationRecord.getNameBuffer(),
        messageCorrelationRecord.getCorrelationKeyBuffer(),
        messageCorrelationRecord.getVariablesBuffer(),
        messageCorrelationRecord.getTenantId(),
        messageCorrelationRecord.getBusinessIdBuffer(),
        messageCorrelationRecord.getDeadline(),
        messageCorrelationRecord.getTimeToLive());
  }
}
