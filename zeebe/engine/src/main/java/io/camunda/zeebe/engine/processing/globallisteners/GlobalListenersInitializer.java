/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.globallisteners;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.GlobalListenerConfiguration;
import io.camunda.zeebe.engine.GlobalListenersConfiguration;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.state.globallisteners.GlobalListenersState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.globallisteners.GlobalListenerRecord;
import io.camunda.zeebe.protocol.impl.record.value.globallisteners.GlobalListenersRecord;
import io.camunda.zeebe.protocol.record.intent.GlobalListenersIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;

public class GlobalListenersInitializer implements StreamProcessorLifecycleAware {

  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;

  private final EngineConfiguration engineConfiguration;

  private final GlobalListenersState globalListenersState;

  public GlobalListenersInitializer(
      final EngineConfiguration engineConfiguration, final ProcessingState processingState) {
    this.engineConfiguration = engineConfiguration;
    this.globalListenersState = processingState.getGlobalListenersState();
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    if (context.getPartitionId() != Protocol.DEPLOYMENT_PARTITION) {
      // We should only create users on the deployment partition. The command will be
      // distributed to the other partitions using our command distribution mechanism.
      LOG.debug(
          "Skipping global listeners configuration on partition {} as it is not the deployment partition",
          context.getPartitionId());
      return;
    }

    final GlobalListenersRecord configuredListeners =
        validateAndConvertListenersConfig(engineConfiguration.getGlobalListeners());
    final Optional<GlobalListenersRecord> storedListeners = globalListenersState.getCurrentConfig();

    if (storedListeners.isPresent()) {
      final GlobalListenersRecord oldRecord = storedListeners.get();
      // Ignore key for equality check
      oldRecord.setListenersConfigKey(-1L);

      if (oldRecord.equals(configuredListeners)) {
        return;
      }
    }

    // We use a timestamp of 0L to ensure this is runs immediately once the stream processor is
    // started
    context
        .getScheduleService()
        .runAtAsync(
            0L,
            (taskResultBuilder) -> {
              taskResultBuilder.appendCommandRecord(
                  GlobalListenersIntent.CONFIGURE, configuredListeners);
              return taskResultBuilder.build();
            });
  }

  private GlobalListenersRecord validateAndConvertListenersConfig(
      final GlobalListenersConfiguration listeners) {
    final String propertyLocation = "camunda.cluster.global-listeners.user-task";
    final List<String> supportedEventTypes = GlobalListenerConfiguration.TASK_LISTENER_EVENT_TYPES;

    final GlobalListenersRecord record = new GlobalListenersRecord();

    // Validate listeners and ignore invalid ones
    for (int i = 0; i < listeners.userTask().size(); i++) {
      final GlobalListenerConfiguration listener = listeners.userTask().get(i);
      final String propertyPrefix = String.format("%s.%d", propertyLocation, i);

      // Check if type is present
      if (listener.type() == null || listener.type().isBlank()) {
        LOG.warn(
            String.format(
                "Missing job type for global listener; listener will be ignored [%s.type]",
                propertyPrefix));
        continue;
      }

      // Validate event types
      final var eventTypes = // consider event types in lowercase for validation
          listener.eventTypes().stream().map(String::toLowerCase).toList();
      final boolean containsAllEventsKeyword =
          eventTypes.contains(GlobalListenerConfiguration.ALL_EVENT_TYPES);
      final List<String> validEventTypes =
          eventTypes.stream()
              .filter( // check if provided event types have valid values
                  eventType -> {
                    if (GlobalListenerConfiguration.ALL_EVENT_TYPES.equals(eventType)
                        || supportedEventTypes.contains(eventType)) {
                      return true;
                    } else {
                      LOG.warn(
                          String.format(
                              "Invalid event type will be ignored: '%s' [%s.eventTypes]",
                              eventType, propertyPrefix));
                      return false;
                    }
                  })
              .filter(
                  eventType -> { // check if "all" is used alongside other event types
                    if (!GlobalListenerConfiguration.ALL_EVENT_TYPES.equals(eventType)
                        && containsAllEventsKeyword) {
                      LOG.warn(
                          String.format(
                              "Extra event type defined alongside '%s' will be ignored: '%s' [%s.eventTypes]",
                              GlobalListenerConfiguration.ALL_EVENT_TYPES,
                              eventType,
                              propertyPrefix));
                      return false;
                    }
                    return true;
                  })
              .toList();

      // Remove duplicates
      final List<String> uniqueEventTypes = new ArrayList<>();
      validEventTypes.forEach(
          eventType -> {
            if (uniqueEventTypes.contains(eventType)) {
              LOG.warn(
                  String.format(
                      "Duplicated event type will be considered only once: '%s' [%s.eventTypes]",
                      eventType, propertyPrefix));
            } else {
              uniqueEventTypes.add(eventType);
            }
          });

      // Check if valid event types have been provided
      if (uniqueEventTypes.isEmpty()) {
        LOG.warn(
            String.format(
                "Missing event types for global listener; listener will be ignored [%s.eventTypes]",
                propertyPrefix));
        continue;
      }

      // Check if retries actually contains a number
      final int retries;
      try {
        retries = Integer.parseInt(listener.retries());
        if (retries <= 0) {
          throw new NumberFormatException();
        }
      } catch (final NumberFormatException e) {
        LOG.warn(
            String.format(
                "Invalid retries for global listener: '%s'; listener will be ignored [%s.retries]",
                listener.retries(), propertyPrefix));
        continue;
      }
      record.addTaskListener(
          new GlobalListenerRecord()
              .setType(listener.type())
              .setEventTypes(uniqueEventTypes)
              .setRetries(retries)
              .setAfterNonGlobal(listener.afterNonGlobal()));
    }

    return record;
  }
}
