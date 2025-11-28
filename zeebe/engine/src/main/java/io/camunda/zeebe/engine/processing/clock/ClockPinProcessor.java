/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clock;

import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.protocol.impl.record.value.clock.ClockRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.stream.api.SideEffectProducer;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.time.Instant;

public final class ClockPinProcessor implements DistributedTypedRecordProcessor<ClockRecord> {

  private final ClockProcessorHelper helper;

  public ClockPinProcessor(final ClockProcessorHelper helper) {
    this.helper = helper;
  }

  private SideEffectProducer clockModification(final ClockRecord value) {
    final var pinnedAt = Instant.ofEpochMilli(value.getTime());
    return () -> {
      helper.getClock().pinAt(pinnedAt);
      return true;
    };
  }

  @Override
  public void processNewCommand(final TypedRecord<ClockRecord> command) {
    if (!helper.validateCommand(command)) {
      return;
    }

    final var clockRecord = command.getValue();
    if (clockRecord.getTime() < 0) {
      final var rejectionMessage =
          "Expected pin time to be not negative but it was %d".formatted(clockRecord.getTime());

      helper
          .getRejectionWriter()
          .appendRejection(command, RejectionType.INVALID_ARGUMENT, rejectionMessage);
      helper
          .getResponseWriter()
          .writeRejectionOnCommand(command, RejectionType.INVALID_ARGUMENT, rejectionMessage);
      return;
    }
    helper.processCommand(command, ClockIntent.PINNED, clockModification(clockRecord));
  }

  @Override
  public void processDistributedCommand(final TypedRecord<ClockRecord> command) {
    helper.processDistributedCommand(
        command, ClockIntent.PINNED, clockModification(command.getValue()));
  }
}
