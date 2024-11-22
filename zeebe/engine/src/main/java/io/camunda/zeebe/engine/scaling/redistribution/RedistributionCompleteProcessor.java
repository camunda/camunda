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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.scaling.RedistributionRecord;
import io.camunda.zeebe.protocol.impl.record.value.scaling.ScaleRecord;
import io.camunda.zeebe.protocol.record.intent.scaling.RedistributionIntent;
import io.camunda.zeebe.protocol.record.intent.scaling.ScaleIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public class RedistributionCompleteProcessor implements TypedRecordProcessor<RedistributionRecord> {
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;

  public RedistributionCompleteProcessor(final Writers writers) {
    stateWriter = writers.state();
    commandWriter = writers.command();
  }

  @Override
  public void processRecord(final TypedRecord<RedistributionRecord> record) {
    stateWriter.appendFollowUpEvent(
        record.getKey(), RedistributionIntent.COMPLETED, record.getValue());
    stateWriter.appendFollowUpEvent(record.getKey(), ScaleIntent.SCALED_UP, new ScaleRecord());
  }
}
