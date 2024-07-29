/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.relocation;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.immutable.RelocationState;
import io.camunda.zeebe.protocol.impl.record.value.scale.ScaleRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ScaleIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.List;
import org.agrona.collections.MutableBoolean;

public class ScaleRelocateMessageStartProcessor implements TypedRecordProcessor<ScaleRecord> {
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final MessageState messageState;
  private final RelocationState relocationState;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public ScaleRelocateMessageStartProcessor(
      final Writers writers,
      final MessageState messageState,
      final RelocationState relocationState,
      final CommandDistributionBehavior commandDistributionBehavior) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    this.messageState = messageState;
    this.relocationState = relocationState;
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<ScaleRecord> record) {
    final var correlationKey = record.getValue().getCorrelationKey();
    final var newPartition =
        relocationState.getRoutingInfo().newPartitionForCorrelationKey(correlationKey);

    final var foundAny = new MutableBoolean();
    messageState.visitMessages(
        message -> {
          // TODO: visit directly by correlation key to make it efficient
          if (!message.getMessage().getCorrelationKeyBuffer().equals(correlationKey)) {
            return true;
          }

          foundAny.set(true);
          final var distributionKey = message.getMessageKey();

          final var scaleRecord = new ScaleRecord();
          scaleRecord.setMessageRecord(message.getMessage());

          commandDistributionBehavior.distributeCommand(
              distributionKey,
              ValueType.SCALE,
              ScaleIntent.RELOCATE_MESSAGE_APPLY,
              scaleRecord,
              List.of(newPartition),
              ValueType.SCALE,
              ScaleIntent.RELOCATE_MESSAGE_COMPLETE,
              scaleRecord);

          return true;
        });
    if (!foundAny.get()) {
      final var newScaleRecord = new ScaleRecord();
      newScaleRecord.setCorrelationKey(correlationKey);
      stateWriter.appendFollowUpEvent(
          record.getKey(), ScaleIntent.RELOCATE_CORRELATION_KEY_COMPLETED, newScaleRecord);

      commandWriter.appendFollowUpCommand(
          -1, ScaleIntent.RELOCATE_NEXT_CORRELATION_KEY, newScaleRecord);
    }
  }
}
