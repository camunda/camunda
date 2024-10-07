/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scaling;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.scaling.ScaleRecord;
import io.camunda.zeebe.protocol.record.intent.scaling.ScaleIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class ScaleUpProcessor implements TypedRecordProcessor<ScaleRecord> {
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;

  public ScaleUpProcessor(final KeyGenerator keyGenerator, final Writers writers) {
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
  }

  @Override
  public void processRecord(final TypedRecord<ScaleRecord> record) {
    final var scaleKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(scaleKey, ScaleIntent.SCALED_UP, new ScaleRecord());
  }
}
