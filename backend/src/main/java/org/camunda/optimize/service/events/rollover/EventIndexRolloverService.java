/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.events.rollover;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.AbstractScheduledService;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.ElasticsearchHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_NAME;

@RequiredArgsConstructor
@Component
@Slf4j
public class EventIndexRolloverService extends AbstractScheduledService {

  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;

  @Override
  protected void run() {
    triggerRollover();
  }

  @Override
  protected Trigger getScheduleTrigger() {
    return new PeriodicTrigger(
      configurationService.getEventBasedProcessConfiguration()
        .getEventIndexRollover()
        .getScheduleIntervalInMinutes(),
      TimeUnit.MINUTES
    );
  }

  public boolean triggerRollover() {
    if (configurationService.getEventBasedProcessConfiguration().isEnabled()) {
      return ElasticsearchHelper.triggerRollover(
        esClient,
        EXTERNAL_EVENTS_INDEX_NAME,
        configurationService.getEventIndexRolloverConfiguration().getMaxIndexSizeGB()
      );
    }
    return false;
  }

}
