/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clock;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.clock.ClockControlRecord;
import io.camunda.zeebe.protocol.record.intent.ClockControlIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class ClockControlProcessor implements DistributedTypedRecordProcessor<ClockControlRecord> {

  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;

  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public ClockControlProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final CommandDistributionBehavior commandDistributionBehavior) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    this.keyGenerator = keyGenerator;
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<ClockControlRecord> command) {
    final long eventKey = keyGenerator.nextKey();
    final var clockRecord = command.getValue();

    stateWriter.appendFollowUpEvent(eventKey, ClockControlIntent.PINED, clockRecord);
    responseWriter.writeEventOnCommand(eventKey, ClockControlIntent.PINED, clockRecord, command);

    commandDistributionBehavior.distributeCommand(eventKey, command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<ClockControlRecord> command) {
    stateWriter.appendFollowUpEvent(command.getKey(), ClockControlIntent.PINED, command.getValue());
    commandDistributionBehavior.acknowledgeCommand(command);
  }
}
