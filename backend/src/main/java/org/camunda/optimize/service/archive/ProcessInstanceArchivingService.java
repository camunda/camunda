/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.archive;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.AbstractScheduledService;
import org.camunda.optimize.service.es.reader.ProcessInstanceReader;
import org.camunda.optimize.service.es.writer.ArchiveProcessInstanceWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Component
@Slf4j
public class ProcessInstanceArchivingService extends AbstractScheduledService implements ConfigurationReloadable {

  private final ConfigurationService configurationService;
  private final ProcessInstanceReader processInstanceReader;
  private final ArchiveProcessInstanceWriter archiveProcessInstanceWriter;

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
      configurationService.getDataArchiveConfiguration().getArchiveIntervalInMins(),
      TimeUnit.MINUTES
    );
  }

  @Override
  protected void run() {
    archiveCompletedProcessInstances();
  }

  public void archiveCompletedProcessInstances() {
    log.debug("Archiving completed process instances.");

    archiveProcessInstanceWriter.createInstanceIndicesIfMissing(
      processInstanceReader.getExistingProcessDefinitionKeysFromInstances());
  }

}
