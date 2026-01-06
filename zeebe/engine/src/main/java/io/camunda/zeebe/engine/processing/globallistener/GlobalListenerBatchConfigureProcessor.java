/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.globallistener;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerBatchRecord;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerBatchIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

@ExcludeAuthorizationCheck
public final class GlobalListenerBatchConfigureProcessor
    implements DistributedTypedRecordProcessor<GlobalListenerBatchRecord> {

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final CommandDistributionBehavior distributionBehavior;

  public GlobalListenerBatchConfigureProcessor(
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior distributionBehavior) {
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    this.distributionBehavior = distributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<GlobalListenerBatchRecord> command) {
    final long key = keyGenerator.nextKey();
    appendConfiguredEvent(key, command.getValue());
    distributeCommand(key, command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<GlobalListenerBatchRecord> command) {
    final var record = command.getValue();
    appendConfiguredEvent(command.getKey(), record);
    distributionBehavior.acknowledgeCommand(command);
  }

  private void appendConfiguredEvent(final long key, final GlobalListenerBatchRecord record) {
    stateWriter.appendFollowUpEvent(key, GlobalListenerBatchIntent.CONFIGURED, record);
  }

  private void distributeCommand(
      final long key, final TypedRecord<GlobalListenerBatchRecord> command) {
    distributionBehavior
        .withKey(key)
        .inQueue(DistributionQueue.GLOBAL_LISTENERS.getQueueId())
        .distribute(command);
  }
}
