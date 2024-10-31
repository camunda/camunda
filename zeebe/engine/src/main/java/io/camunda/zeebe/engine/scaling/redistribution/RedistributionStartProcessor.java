/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scaling.redistribution;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.scaling.RedistributionRecord;
import io.camunda.zeebe.protocol.record.intent.scaling.RedistributionIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public class RedistributionStartProcessor implements TypedRecordProcessor<RedistributionRecord> {
  private final RedistributionBehavior redistributionBehavior;
  private final StateWriter stateWriter;

  public RedistributionStartProcessor(
      final RedistributionBehavior redistributionBehavior, final Writers writers) {
    this.redistributionBehavior = redistributionBehavior;
    stateWriter = writers.state();
  }

  @Override
  public void processRecord(final TypedRecord<RedistributionRecord> record) {
    stateWriter.appendFollowUpEvent(
        record.getKey(), RedistributionIntent.STARTED, record.getValue());
    redistributionBehavior.continueRedistribution(record.getKey());
  }
}
