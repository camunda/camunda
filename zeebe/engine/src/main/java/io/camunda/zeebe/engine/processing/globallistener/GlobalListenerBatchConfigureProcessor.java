/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.globallistener;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.globallistener.GlobalListenersState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerBatchRecord;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerBatchIntent;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerIntent;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import io.camunda.zeebe.protocol.record.value.GlobalListenerSource;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@ExcludeAuthorizationCheck
public final class GlobalListenerBatchConfigureProcessor
    implements TypedRecordProcessor<GlobalListenerBatchRecord> {

  private final KeyGenerator keyGenerator;
  private final Writers writers;
  private final GlobalListenersState globalListenersState;

  public GlobalListenerBatchConfigureProcessor(
      final KeyGenerator keyGenerator,
      final Writers writers,
      final ProcessingState processingState) {
    this.keyGenerator = keyGenerator;
    this.writers = writers;
    globalListenersState = processingState.getGlobalListenersState();
  }

  @Override
  public void processRecord(final TypedRecord<GlobalListenerBatchRecord> command) {
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
    // This means that any existing configuration-defined listener which is no longer present in the
    // new configuration should be deleted.
    existingListeners.values().stream()
        // only consider configuration-defined listeners
        .filter(l -> l.getSource() == GlobalListenerSource.CONFIGURATION)
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
                // Note: the old listener is replaced even if it was API-defined
                writers.command().appendFollowUpCommand(key, GlobalListenerIntent.UPDATE, listener);
              } else {
                writers.command().appendFollowUpCommand(key, GlobalListenerIntent.CREATE, listener);
              }
            });

    // Finally, write the CONFIGURED event for the batch, marking the completion of the
    // configuration change
    writers
        .state()
        .appendFollowUpEvent(key, GlobalListenerBatchIntent.CONFIGURED, command.getValue());
  }
}
