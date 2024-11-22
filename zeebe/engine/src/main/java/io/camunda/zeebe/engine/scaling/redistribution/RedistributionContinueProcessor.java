/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scaling.redistribution;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.protocol.impl.record.value.scaling.RedistributionRecord;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public class RedistributionContinueProcessor implements TypedRecordProcessor<RedistributionRecord> {
  private final RedistributionBehavior redistributionBehavior;

  public RedistributionContinueProcessor(final RedistributionBehavior redistributionBehavior) {
    this.redistributionBehavior = redistributionBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<RedistributionRecord> record) {
    redistributionBehavior.continueRedistribution(record.getKey());
  }
}
