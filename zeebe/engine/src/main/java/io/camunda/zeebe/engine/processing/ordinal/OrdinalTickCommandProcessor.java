/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.ordinal;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.mutable.MutableOrdinalState;
import io.camunda.zeebe.protocol.impl.record.value.ordinal.OrdinalRecord;
import io.camunda.zeebe.protocol.record.intent.OrdinalIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.time.InstantSource;

@ExcludeAuthorizationCheck
public final class OrdinalTickCommandProcessor implements TypedRecordProcessor<OrdinalRecord> {

  private final MutableOrdinalState ordinalState;
  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;
  private final InstantSource clock;

  public OrdinalTickCommandProcessor(
      final MutableOrdinalState ordinalState,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final InstantSource clock) {
    this.ordinalState = ordinalState;
    stateWriter = writers.state();
    this.keyGenerator = keyGenerator;
    this.clock = clock;
  }

  @Override
  public void processRecord(final TypedRecord<OrdinalRecord> command) {
    final long now = clock.millis();
    final var nextOrdinal = ordinalState.getCurrentOrdinal() + 1;
    final var ordinalRecord = new OrdinalRecord().setDateTime(now).setOrdinal(nextOrdinal);
    final long key = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(key, OrdinalIntent.TICKED, ordinalRecord);
  }
}
