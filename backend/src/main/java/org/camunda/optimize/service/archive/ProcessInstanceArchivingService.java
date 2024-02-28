/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.archive;

import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder.PROCESS_INSTANCE_ARCHIVE_INDEX;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.AbstractScheduledService;
import org.camunda.optimize.service.db.reader.ProcessInstanceReader;
import org.camunda.optimize.service.db.repository.IndexRepository;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class ProcessInstanceArchivingService extends AbstractScheduledService
    implements ConfigurationReloadable {

  private final ConfigurationService configurationService;
  private final ProcessInstanceReader processInstanceReader;
  private final IndexRepository indexRepository;

  @PostConstruct
  public void init() {
    if (configurationService.getDataArchiveConfiguration().isEnabled()) {
      startArchiving();
    }
  }

  @PreDestroy
  public void stop() {
    stopArchiving();
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    init();
  }

  public synchronized void startArchiving() {
    log.info("Starting process instance archiving");
    startScheduling();
  }

  public synchronized void stopArchiving() {
    log.info("Stopping process instance archiving");
    stopScheduling();
  }

  @Override
  protected Trigger createScheduleTrigger() {
    return new PeriodicTrigger(
        Duration.ofMinutes(
            configurationService.getDataArchiveConfiguration().getArchiveIntervalInMins()));
  }

  @Override
  protected void run() {
    archiveCompletedProcessInstances();
  }

  public void archiveCompletedProcessInstances() {
    log.debug("Archiving completed process instances.");

    indexRepository.createMissingIndices(
        PROCESS_INSTANCE_ARCHIVE_INDEX,
        Set.of(PROCESS_INSTANCE_MULTI_ALIAS),
        processInstanceReader.getExistingProcessDefinitionKeysFromInstances());
  }
}
