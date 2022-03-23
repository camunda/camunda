/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.cleanup;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import org.camunda.optimize.service.es.reader.EventProcessPublishStateReader;
import org.camunda.optimize.service.es.writer.EventProcessInstanceWriter;
import org.camunda.optimize.service.es.writer.EventProcessInstanceWriterFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import org.camunda.optimize.service.util.configuration.cleanup.CleanupMode;
import org.camunda.optimize.service.util.configuration.cleanup.ProcessDefinitionCleanupConfiguration;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Collection;

@AllArgsConstructor
@Component
@Slf4j
public class EventProcessCleanupService implements CleanupService {
  private final ConfigurationService configurationService;
  private final EventProcessPublishStateReader eventProcessPublishStateReader;
  private final EventProcessInstanceWriterFactory eventProcessInstanceWriterFactory;

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

  private void performCleanupForEventProcess(final OffsetDateTime startTime,
                                             final EventProcessPublishStateDto eventProcess) {
    final ProcessDefinitionCleanupConfiguration cleanupConfiguration = getCleanupConfiguration()
      .getProcessDefinitionCleanupConfigurationForKey(eventProcess.getProcessKey());
    final CleanupMode cleanupMode = cleanupConfiguration.getCleanupMode();
    log.info(
      "Performing cleanup on event process instances for processDefinitionKey: {} with ttl: {} and mode:{}",
      eventProcess.getProcessKey(),
      cleanupConfiguration.getTtl(),
      cleanupMode
    );

    final EventProcessInstanceWriter eventProcessInstanceWriter =
      eventProcessInstanceWriterFactory.createEventProcessInstanceWriter(eventProcess);
    final OffsetDateTime endDate = startTime.minus(cleanupConfiguration.getTtl());
    switch (cleanupMode) {
      case ALL:
        eventProcessInstanceWriter.deleteInstancesThatEndedBefore(endDate);
        break;
      case VARIABLES:
        eventProcessInstanceWriter.deleteVariablesOfInstancesThatEndedBefore(endDate);
        break;
      default:
        throw new IllegalStateException("Unsupported cleanup mode " + cleanupMode);
    }

    log.info(
      "Finished cleanup on event process instances for processDefinitionKey: {}, with ttl: {} and mode:{}",
      eventProcess.getProcessKey(),
      cleanupConfiguration.getTtl(),
      cleanupMode
    );
  }

  private CleanupConfiguration getCleanupConfiguration() {
    return this.configurationService.getCleanupServiceConfiguration();
  }
}
