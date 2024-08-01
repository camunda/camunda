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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedEventWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.immutable.RelocationState;
import io.camunda.zeebe.protocol.impl.record.value.scale.ScaleRecord;
import io.camunda.zeebe.protocol.record.intent.ScaleIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.agrona.collections.MutableBoolean;

public class ScaleRelocateMessageCompleteProcessor implements TypedRecordProcessor<ScaleRecord> {

  private final KeyGenerator keyGenerator;
  private final TypedEventWriter stateWriter;
  private final MessageState messageState;
  private final RelocationState relocationState;
  private final TypedCommandWriter commandWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public ScaleRelocateMessageCompleteProcessor(
      final KeyGenerator keyGenerator,
      final Writers writers,
      final MessageState messageState,
      final RelocationState relocationState,
      final CommandDistributionBehavior commandDistributionBehavior) {
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    this.messageState = messageState;
    commandWriter = writers.command();
    this.relocationState = relocationState;
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<ScaleRecord> record) {
    // TODO: implement applier for this
    stateWriter.appendFollowUpEvent(
        record.getKey(), ScaleIntent.RELOCATE_MESSAGE_COMPLETED, record.getValue());
    final var correlationKey =
        record.getValue().getMessageSubscriptionRecord().getCorrelationKeyBuffer();

    final var foundAny = new MutableBoolean();
    messageState.visitMessages(
        message -> {
          if (message.getMessage().getCorrelationKeyBuffer().equals(correlationKey)) {
            foundAny.set(true);
            return false;
          } else {
            return true;
          }
        });

    if (!foundAny.get()) {
      MessageRelocationHelper.completeRelocationOfCorrelationKey(
          keyGenerator,
          commandDistributionBehavior,
          relocationState,
          correlationKey,
          stateWriter,
          commandWriter,
          record.getKey());
    }
  }
}
