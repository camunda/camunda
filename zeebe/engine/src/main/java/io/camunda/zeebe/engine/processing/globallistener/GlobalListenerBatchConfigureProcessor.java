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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.globallistener.GlobalListenersState;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerBatchRecord;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerBatchIntent;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerIntent;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@ExcludeAuthorizationCheck
public final class GlobalListenerBatchConfigureProcessor
    implements DistributedTypedRecordProcessor<GlobalListenerBatchRecord> {

  private final KeyGenerator keyGenerator;
  private final Writers writers;
  private final CommandDistributionBehavior distributionBehavior;
  private final GlobalListenersState globalListenersState;

  public GlobalListenerBatchConfigureProcessor(
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior distributionBehavior,
      final ProcessingState processingState) {
    this.keyGenerator = keyGenerator;
    this.writers = writers;
    this.distributionBehavior = distributionBehavior;
    globalListenersState = processingState.getGlobalListenersState();
  }

  @Override
  public void processNewCommand(final TypedRecord<GlobalListenerBatchRecord> command) {
    // Note: key generation is done here to have a single key for all resulting commands/events
    // since they are all related to the same configuration change, generating a single new
    // configuration version.
    final long key = keyGenerator.nextKey();

    // Retrieve existing listeners from state, mapped by ID
    final GlobalListenerBatchRecord currentConfig = globalListenersState.getCurrentConfig();
    final Map<String, GlobalListenerRecordValue> existingListeners =
        currentConfig == null
            ? Collections.emptyMap()
            : currentConfig.getTaskListeners().stream()
                .collect(Collectors.toMap(GlobalListenerRecordValue::getId, l -> l));
    // Retrieve new requested listeners from command, mapped by ID
    final Map<String, GlobalListenerRecordValue> newListeners =
        command.getValue().getTaskListeners().stream()
            .collect(Collectors.toMap(GlobalListenerRecordValue::getId, l -> l));

    // The new configuration should completely replace the old one.
    // This means that any existing listener which is no longer present in the new configuration
    // should be deleted.
    existingListeners.values().stream()
        // filter out listeners which are still present in the new configuration
        .filter(l -> !newListeners.containsKey(l.getId()))
        .forEach(
            listener ->
                writers
                    .command()
                    .appendFollowUpCommand(key, GlobalListenerIntent.DELETE, listener));
    // New or updated listeners should be created or updated accordingly
    newListeners
        .values()
        .forEach(
            listener -> {
              if (existingListeners.containsKey(listener.getId())) {
                writers.command().appendFollowUpCommand(key, GlobalListenerIntent.UPDATE, listener);
              } else {
                writers.command().appendFollowUpCommand(key, GlobalListenerIntent.CREATE, listener);
              }
            });

    // Finally, write the CONFIGURED event for the batch, marking the completion of the
    // configuration change
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
    writers
        .state()
        .appendFollowUpEvent(key, GlobalListenerBatchIntent.CONFIGURED, record);
  }

  private void distributeCommand(
      final long key, final TypedRecord<GlobalListenerBatchRecord> command) {
    distributionBehavior
        .withKey(key)
        .inQueue(DistributionQueue.GLOBAL_LISTENERS.getQueueId())
        .distribute(command);
  }
}
