/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

@ExcludeAuthorizationCheck
public class MessageSubscriptionMigrateProcessor
    implements DistributedTypedRecordProcessor<MessageSubscriptionRecord> {

  private final MessageSubscriptionState subscriptionState;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  private final MessageSubscriptionRecord messageSubscriptionRecord =
      new MessageSubscriptionRecord();

  public MessageSubscriptionMigrateProcessor(
      final MessageSubscriptionState subscriptionState,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    this.subscriptionState = subscriptionState;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<MessageSubscriptionRecord> command) {
    migrateMessageSubscription(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<MessageSubscriptionRecord> command) {
    migrateMessageSubscription(command);
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private void migrateMessageSubscription(final TypedRecord<MessageSubscriptionRecord> command) {
    final var value = command.getValue();

    final var subscription =
        subscriptionState.get(value.getElementInstanceKey(), value.getMessageNameBuffer());

    if (subscription == null) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.NOT_FOUND,
          """
          Expected to migrate a message subscription with key '%s', \
          but subscription not found for element instance key '%d' and the provided message name"""
              .formatted(command.getKey(), value.getElementInstanceKey()));
      return;
    }

    // copy the value provided by the state, as the event applier will cause to overwrite it
    messageSubscriptionRecord.reset();
    messageSubscriptionRecord.copyFrom(subscription.getRecord());

    stateWriter.appendFollowUpEvent(
        subscription.getKey(),
        MessageSubscriptionIntent.MIGRATED,
        messageSubscriptionRecord
            .setBpmnProcessId(value.getBpmnProcessIdBuffer())
            .setInterrupting(value.isInterrupting()));
  }
}
