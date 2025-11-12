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
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.stream.api.SideEffectProducer;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public final class ClockResetProcessor implements DistributedTypedRecordProcessor<ClockRecord> {

  private final ClockProcessorHelper helper;

  public ClockResetProcessor(final ClockProcessorHelper helper) {
    this.helper = helper;
  }

  private SideEffectProducer clockModification() {
    return () -> {
      helper.getClock().reset();
      return true;
    };
  }

  @Override
  public void processNewCommand(final TypedRecord<ClockRecord> command) {
    if (!helper.validateCommand(command)) {
      return;
    }
    helper.processCommand(command, ClockIntent.RESETTED, clockModification());
  }

  @Override
  public void processDistributedCommand(final TypedRecord<ClockRecord> command) {
    helper.processDistributedCommand(command, ClockIntent.RESETTED, clockModification());
  }
}
