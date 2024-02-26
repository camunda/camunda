/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public class MessageSubscriptionMigrateProcessor
    implements TypedRecordProcessor<MessageSubscriptionRecord> {

  private final StateWriter stateWriter;
  private final MessageSubscriptionState subscriptionState;

  public MessageSubscriptionMigrateProcessor(
      final MessageState messageState,
      final MessageSubscriptionState subscriptionState,
      final SubscriptionCommandSender subscriptionCommandSender,
      final Writers writers) {
    stateWriter = writers.state();
    this.subscriptionState = subscriptionState;
  }

  @Override
  public void processRecord(final TypedRecord<MessageSubscriptionRecord> command) {

    final var subscription =
        subscriptionState.get(
            command.getValue().getElementInstanceKey(), command.getValue().getMessageNameBuffer());

    if (subscription == null) {
      // todo: deal with this case correctly
      return;
    }

    // Trust what is stored in the state, only take over specific data from the command. At this
    // time only the bpmn process id is adjusted on migration
    final MessageSubscriptionRecord migratedSubscription =
        subscription.getRecord().setBpmnProcessId(command.getValue().getBpmnProcessIdBuffer());
    stateWriter.appendFollowUpEvent(
        subscription.getKey(), MessageSubscriptionIntent.MIGRATED, migratedSubscription);
  }
}
