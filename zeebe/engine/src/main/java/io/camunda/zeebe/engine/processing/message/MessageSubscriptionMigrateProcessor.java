/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public class MessageSubscriptionMigrateProcessor
    implements DistributedTypedRecordProcessor<MessageSubscriptionRecord> {

  private final StateWriter stateWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public MessageSubscriptionMigrateProcessor(
      final Writers writers, final CommandDistributionBehavior commandDistributionBehavior) {
    stateWriter = writers.state();
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<MessageSubscriptionRecord> command) {
    final var value = command.getValue();

    stateWriter.appendFollowUpEvent(command.getKey(), MessageSubscriptionIntent.MIGRATED, value);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<MessageSubscriptionRecord> command) {
    final var value = command.getValue();

    stateWriter.appendFollowUpEvent(command.getKey(), MessageSubscriptionIntent.MIGRATED, value);
    commandDistributionBehavior.acknowledgeCommand(command);
  }
}
