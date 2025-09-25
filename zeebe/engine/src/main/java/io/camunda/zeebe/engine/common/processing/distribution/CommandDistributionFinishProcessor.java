/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.distribution;

import io.camunda.zeebe.engine.common.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.common.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.stream.api.records.TypedRecord;

@ExcludeAuthorizationCheck
public class CommandDistributionFinishProcessor
    implements TypedRecordProcessor<CommandDistributionRecord> {
  private final CommandDistributionBehavior commandDistributionBehavior;

  public CommandDistributionFinishProcessor(
      final CommandDistributionBehavior commandDistributionBehavior) {
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<CommandDistributionRecord> record) {
    commandDistributionBehavior.onFinish(record.getKey(), record.getValue());
  }
}
