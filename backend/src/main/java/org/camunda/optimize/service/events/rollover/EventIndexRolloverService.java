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
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.indices.rollover.RolloverRequest;
import org.elasticsearch.client.indices.rollover.RolloverResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_INDEX_NAME;

@RequiredArgsConstructor
@Component
@Slf4j
public class EventIndexRolloverService extends AbstractScheduledService {
  private final OptimizeElasticsearchClient esClient;
  private final OptimizeIndexNameService optimizeIndexNameService;
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
    return triggerRollover(getMaxAge(), getMaxDocs());
  }

  public boolean triggerRollover(final TimeValue maxAge, final int maxDocs) {
    final String eventIndexAliasName = optimizeIndexNameService.getOptimizeIndexAliasForIndex(EVENT_INDEX_NAME);

    RolloverRequest rolloverRequest = new RolloverRequest(eventIndexAliasName, null);
    rolloverRequest.addMaxIndexAgeCondition(maxAge);
    rolloverRequest.addMaxIndexDocsCondition(maxDocs);

    log.info("Executing Rollover Request..");

    RolloverResponse rolloverResponse = null;
    try {
      rolloverResponse = esClient.rollover(rolloverRequest);
    } catch (Exception e) {
      log.error("Failed to execute rollover request", e);
    }

    if (rolloverResponse.isRolledOver()) {
      log.info("Event index has been rolled over. New index name: {}", rolloverResponse.getNewIndex());
    } else {
      log.info("Event index has not been rolled over.");
    }

    return rolloverResponse.isRolledOver();
  }

  private TimeValue getMaxAge() {
    return new TimeValue(configurationService.getEventIndexRolloverConfiguration().getMaxAgeInDays(), TimeUnit.DAYS);
  }

  private int getMaxDocs() {
    return configurationService.getEventIndexRolloverConfiguration().getMaxDocs();
  }
}
