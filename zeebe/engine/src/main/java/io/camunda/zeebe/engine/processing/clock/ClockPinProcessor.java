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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.clock.ClockRecord;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.time.Instant;

public final class ClockPinProcessor implements DistributedTypedRecordProcessor<ClockRecord> {
  private final SideEffectWriter sideEffectWriter;
  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;
  private final ControllableStreamClock clock;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final TypedResponseWriter responseWriter;

  public ClockPinProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ControllableStreamClock clock,
      final CommandDistributionBehavior commandDistributionBehavior) {
    sideEffectWriter = writers.sideEffect();
    stateWriter = writers.state();
    this.keyGenerator = keyGenerator;
    responseWriter = writers.response();
    this.clock = clock;

    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<ClockRecord> command) {
    final long eventKey = keyGenerator.nextKey();
    final var clockRecord = command.getValue();

    applyClockModification(eventKey, clockRecord);
    if (command.hasRequestMetadata()) {
      responseWriter.writeEventOnCommand(eventKey, ClockIntent.PINNED, clockRecord, command);
    }

    commandDistributionBehavior.distributeCommand(eventKey, command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<ClockRecord> command) {
    applyClockModification(command.getKey(), command.getValue());
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private void applyClockModification(final long key, final ClockRecord clockRecord) {
    final long pinnedAtEpoch = clockRecord.getPinnedAtEpoch();

    sideEffectWriter.appendSideEffect(
        () -> {
          clock.pinAt(Instant.ofEpochMilli(pinnedAtEpoch));
          return true;
        });
    stateWriter.appendFollowUpEvent(key, ClockIntent.PINNED, clockRecord);
  }
}
