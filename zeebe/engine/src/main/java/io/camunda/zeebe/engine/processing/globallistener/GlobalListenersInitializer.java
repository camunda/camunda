/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.globallistener;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.GlobalListenersConfiguration;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.state.globallistener.GlobalListenersState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerBatchIntent;
import io.camunda.zeebe.protocol.record.value.GlobalListenerSource;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import java.util.Objects;
import org.slf4j.Logger;

public final class GlobalListenersInitializer implements StreamProcessorLifecycleAware {

  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;

  private final EngineConfiguration engineConfiguration;

  private final GlobalListenersState globalListenersState;

  public GlobalListenersInitializer(
      final EngineConfiguration engineConfiguration, final ProcessingState processingState) {
    this.engineConfiguration = engineConfiguration;
    globalListenersState = processingState.getGlobalListenersState();
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    if (context.getPartitionId() != Protocol.DEPLOYMENT_PARTITION) {
      // We should only initialize the global listeners configuration on the deployment partition.
      // The command will be distributed to the other partitions using our command distribution
      // mechanism.
      LOG.debug(
          "Skipping global listeners configuration on partition {} as it is not the deployment partition",
          context.getPartitionId());
      return;
    }

    final GlobalListenerBatchRecord configuredListeners =
        convertListenersConfig(engineConfiguration.getGlobalListeners());
    final GlobalListenerBatchRecord storedListeners =
        Objects.requireNonNullElse(
            globalListenersState.getCurrentConfig(), new GlobalListenerBatchRecord());

    final GlobalListenerBatchRecord oldRecord = new GlobalListenerBatchRecord();
    oldRecord.copyFrom(storedListeners);
    // Ignore key for equality check
    oldRecord.setGlobalListenerBatchKey(-1L);

    if (oldRecord.equals(configuredListeners)) {
      return;
    }

    // We use a timestamp of 0L to ensure this is runs immediately once the stream processor is
    // started
    context
        .getScheduleService()
        .runAtAsync(
            0L,
            (taskResultBuilder) -> {
              taskResultBuilder.appendCommandRecord(
                  GlobalListenerBatchIntent.CONFIGURE, configuredListeners);
              return taskResultBuilder.build();
            });
  }

  private GlobalListenerBatchRecord convertListenersConfig(
      final GlobalListenersConfiguration listeners) {
    final GlobalListenerBatchRecord record = new GlobalListenerBatchRecord();
    listeners.userTask().stream()
        .map(
            listener ->
                new GlobalListenerRecord()
                    .setId(listener.id())
                    .setType(listener.type())
                    .setEventTypes(listener.eventTypes())
                    .setRetries(Integer.parseInt(listener.retries()))
                    .setAfterNonGlobal(listener.afterNonGlobal())
                    .setPriority(listener.priority())
                    .setSource(GlobalListenerSource.CONFIGURATION)
                    .setListenerType(listener.listenerType()))
        .forEach(record::addListener);
    return record;
  }
}
