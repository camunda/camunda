/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.cleanup;

import io.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import io.camunda.optimize.service.db.es.schema.index.events.EventProcessInstanceIndexES;
import io.camunda.optimize.service.db.reader.EventProcessPublishStateReader;
import io.camunda.optimize.service.db.writer.EventProcessInstanceWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import io.camunda.optimize.service.util.configuration.cleanup.CleanupMode;
import io.camunda.optimize.service.util.configuration.cleanup.ProcessDefinitionCleanupConfiguration;
import java.time.OffsetDateTime;
import java.util.Collection;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class EventProcessCleanupService extends CleanupService {

  private final ConfigurationService configurationService;
  private final EventProcessPublishStateReader eventProcessPublishStateReader;
  private final EventProcessInstanceWriter eventProcessInstanceWriter;

  @Override
  public boolean isEnabled() {
    return getCleanupConfiguration().getProcessDataCleanupConfiguration().isEnabled();
  }

  @Override
  public void doCleanup(final OffsetDateTime startTime) {
    final Collection<EventProcessPublishStateDto> publishedEventProcesses =
        eventProcessPublishStateReader.getAllEventProcessPublishStates();

    int i = 1;
    for (EventProcessPublishStateDto currentEventProcess : publishedEventProcesses) {
      log.info("Event Process History Cleanup step {}/{}", i, publishedEventProcesses.size());
      performCleanupForEventProcess(startTime, currentEventProcess);
      i++;
    }
  }

  private void performCleanupForEventProcess(
      final OffsetDateTime startTime, final EventProcessPublishStateDto eventProcess) {
    final ProcessDefinitionCleanupConfiguration cleanupConfiguration =
        getCleanupConfiguration()
            .getProcessDefinitionCleanupConfigurationForKey(eventProcess.getProcessKey());
    final CleanupMode cleanupMode = cleanupConfiguration.getCleanupMode();
    log.info(
        "Performing cleanup on event process instances for processDefinitionKey: {} with ttl: {} and mode:{}",
        eventProcess.getProcessKey(),
        cleanupConfiguration.getTtl(),
        cleanupMode);

    final String index = new EventProcessInstanceIndexES(eventProcess.getId()).getIndexName();
    final OffsetDateTime endDate = startTime.minus(cleanupConfiguration.getTtl());
    switch (cleanupMode) {
      case ALL:
        eventProcessInstanceWriter.deleteInstancesThatEndedBefore(index, endDate);
        break;
      case VARIABLES:
        eventProcessInstanceWriter.deleteVariablesOfInstancesThatEndedBefore(index, endDate);
        break;
      default:
        throw new IllegalStateException("Unsupported cleanup mode " + cleanupMode);
    }

    log.info(
        "Finished cleanup on event process instances for processDefinitionKey: {}, with ttl: {} and mode:{}",
        eventProcess.getProcessKey(),
        cleanupConfiguration.getTtl(),
        cleanupMode);
  }

  private CleanupConfiguration getCleanupConfiguration() {
    return this.configurationService.getCleanupServiceConfiguration();
  }
}
