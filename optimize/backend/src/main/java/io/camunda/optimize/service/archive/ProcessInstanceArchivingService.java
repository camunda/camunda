/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.archive;

import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder.PROCESS_INSTANCE_ARCHIVE_INDEX;

import io.camunda.optimize.service.AbstractScheduledService;
import io.camunda.optimize.service.db.reader.ProcessInstanceReader;
import io.camunda.optimize.service.db.repository.IndexRepository;
import io.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
  protected void run() {
    archiveCompletedProcessInstances();
  }

  @Override
  protected Trigger createScheduleTrigger() {
    return new PeriodicTrigger(
        Duration.ofMinutes(
            configurationService.getDataArchiveConfiguration().getArchiveIntervalInMins()));
  }

  public void archiveCompletedProcessInstances() {
    log.debug("Archiving completed process instances.");

    indexRepository.createMissingIndices(
        PROCESS_INSTANCE_ARCHIVE_INDEX,
        Set.of(PROCESS_INSTANCE_MULTI_ALIAS),
        processInstanceReader.getExistingProcessDefinitionKeysFromInstances());
  }
}
