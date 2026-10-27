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
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.globallistener.GlobalListenersState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerBatchIntent;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerIntent;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import io.camunda.zeebe.protocol.record.value.GlobalListenerSource;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@ExcludeAuthorizationCheck
public final class GlobalListenerBatchConfigureProcessor
    implements DistributedTypedRecordProcessor<GlobalListenerBatchRecord> {

  private static final String CONFIG_EXISTS_ERROR_MESSAGE =
      "Expected to update the global listeners configuration, but a configuration with the same key '%d' is already applied";

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

    // Generate a new key for the updated configuration,
    // so that it can be referenced when configuration pinning is performed
    final var configKey = keyGenerator.nextKey();
    final GlobalListenerBatchRecord listenerBatchRecord = command.getValue();
    listenerBatchRecord.setGlobalListenerBatchKey(configKey);

    // Compare the new configuration with the existing one to determine which listeners are created,
    // updated or deleted.
    // Then, enrich the record with this information so that it can be applied on the followers when
    // the command is distributed.
    fillConfigurationChangeMetadata(listenerBatchRecord);

    // Apply the configuration changes defined in the previous step
    emitChangeEvents(listenerBatchRecord);

    // Distribute the command to the other partitions
    distributionBehavior
        .withKey(configKey)
        .inQueue(DistributionQueue.GLOBAL_LISTENERS.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<GlobalListenerBatchRecord> command) {
    final var record = command.getValue();

    // Idempotency check: if the provided configuration has already been applied to the state,
    // we can skip applying it again.
    final var currentConfigKey = globalListenersState.getCurrentConfigKey();
    if (currentConfigKey != null && record.getGlobalListenerBatchKey() == currentConfigKey) {
      final var message = CONFIG_EXISTS_ERROR_MESSAGE.formatted(currentConfigKey);
      writers.rejection().appendRejection(command, RejectionType.ALREADY_EXISTS, message);
    } else {
      // Apply the configuration changes defined in the record, which were determined by the leader
      // when the command was first processed and included in the record before distribution
      emitChangeEvents(record);
    }

    distributionBehavior.acknowledgeCommand(command);
  }

  private void fillConfigurationChangeMetadata(
      final GlobalListenerBatchRecord listenerBatchRecord) {
    // Retrieve existing listeners from state, mapped by ID
    final GlobalListenerBatchRecord currentConfig = globalListenersState.getCurrentConfig();
    final Map<String, GlobalListenerRecord> existingListeners =
        currentConfig == null
            ? Collections.emptyMap()
            : currentConfig.getTaskListeners().stream()
                .collect(Collectors.toMap(GlobalListenerRecordValue::getId, Function.identity()));
    // Retrieve new requested listeners from command, mapped by ID
    final Map<String, GlobalListenerRecord> newListeners =
        listenerBatchRecord.getTaskListeners().stream()
            .collect(Collectors.toMap(GlobalListenerRecordValue::getId, Function.identity()));

    // The new configuration should completely replace the old one.
    // This means that any existing configuration-defined listener which is no longer present in the
    // new configuration should be deleted.
    existingListeners.values().stream()
        // only consider configuration-defined listeners
        .filter(l -> l.getSource() == GlobalListenerSource.CONFIGURATION)
        // filter out listeners which are still present in the new configuration
        .filter(l -> !newListeners.containsKey(l.getId()))
        .forEach(
            listener -> {
              listenerBatchRecord.addDeletedListener(listener);
              // Add the listener information to the record since it is missing
              listenerBatchRecord.addListener(listener);
            });
    // New or updated listeners should be created or updated accordingly
    newListeners
        .values()
        .forEach(
            listener -> {
              // Note: the old listener is replaced even if it was API-defined
              if (existingListeners.containsKey(listener.getId())) {
                // Ensure the old key is kept for updated listeners to correlate with existing state
                listener.setGlobalListenerKey(
                    existingListeners.get(listener.getId()).getGlobalListenerKey());
                listenerBatchRecord.addUpdatedListener(listener);
              } else {
                // Generate a new key for created listeners
                listener.setGlobalListenerKey(keyGenerator.nextKey());
                listenerBatchRecord.addCreatedListener(listener);
              }
            });
  }

  private void emitChangeEvents(final GlobalListenerBatchRecord listenerBatchRecord) {
    final Map<Long, GlobalListenerRecord> listenersByKey =
        listenerBatchRecord.getTaskListeners().stream()
            .collect(
                Collectors.toMap(
                    GlobalListenerRecordValue::getGlobalListenerKey, Function.identity()));

    // Apply individual listener changes
    listenerBatchRecord
        .getDeletedListenerKeys()
        .forEach(
            key ->
                writers
                    .state()
                    .appendFollowUpEvent(
                        key, GlobalListenerIntent.DELETED, listenersByKey.get(key)));
    listenerBatchRecord
        .getUpdatedListenerKeys()
        .forEach(
            key ->
                writers
                    .state()
                    .appendFollowUpEvent(
                        key, GlobalListenerIntent.UPDATED, listenersByKey.get(key)));
    listenerBatchRecord
        .getCreatedListenerKeys()
        .forEach(
            key ->
                writers
                    .state()
                    .appendFollowUpEvent(
                        key, GlobalListenerIntent.CREATED, listenersByKey.get(key)));

    // Finally, update the configuration key so that following commands can correctly reference it
    writers
        .state()
        .appendFollowUpEvent(
            listenerBatchRecord.getGlobalListenerBatchKey(),
            GlobalListenerBatchIntent.CONFIGURED,
            listenerBatchRecord);
  }
}
