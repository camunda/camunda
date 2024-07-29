/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.relocation;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.message.MessagePublishProcessor;
import io.camunda.zeebe.engine.processing.message.Subscriptions;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.scale.ScaleRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public class ScaleRelocateMessageApplyProcessor implements TypedRecordProcessor<ScaleRecord> {
  private final MessageState messageState;
  private final MessageSubscriptionState subscriptionState;
  private final TypedRejectionWriter rejectionWriter;
  private final StateWriter stateWriter;
  private final SubscriptionCommandSender commandSender;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public ScaleRelocateMessageApplyProcessor(
      final Writers writers,
      final MessageState messageState,
      final MessageSubscriptionState subscriptionState,
      final SubscriptionCommandSender commandSender,
      final CommandDistributionBehavior commandDistributionBehavior) {
    rejectionWriter = writers.rejection();
    this.messageState = messageState;
    stateWriter = writers.state();
    this.subscriptionState = subscriptionState;
    this.commandSender = commandSender;
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<ScaleRecord> record) {
    final var messageRecord = record.getValue().getMessageRecord();
    final var messageKey = record.getKey();
    if (messageState.getMessage(messageKey) != null) {
      rejectionWriter.appendRejection(
          record, RejectionType.ALREADY_EXISTS, "Message was already relocated");
    } else {
      stateWriter.appendFollowUpEvent(messageKey, MessageIntent.PUBLISHED, messageRecord);
      final var correlatingSubscriptions = new Subscriptions();
      MessagePublishProcessor.correlateToSubscriptions(
          messageKey, messageRecord, subscriptionState, correlatingSubscriptions, stateWriter);
      // TODO: Handle message start event
      correlatingSubscriptions.visitSubscriptions(
          subscription ->
              commandSender.correlateProcessMessageSubscription(
                  subscription.getProcessInstanceKey(),
                  subscription.getElementInstanceKey(),
                  subscription.getBpmnProcessId(),
                  messageRecord.getNameBuffer(),
                  messageKey,
                  messageRecord.getVariablesBuffer(),
                  messageRecord.getCorrelationKeyBuffer(),
                  messageRecord.getTenantId()));
    }
    commandDistributionBehavior.acknowledgeCommand(record);
  }
}
