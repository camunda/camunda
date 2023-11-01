/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.events.rollover;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.AbstractScheduledService;
import org.camunda.optimize.service.db.DatabaseClient;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
public abstract class AbstractIndexRolloverService extends AbstractScheduledService {

  protected final DatabaseClient databaseClient;

  protected AbstractIndexRolloverService(final DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public List<String> triggerRollover() {
    List<String> rolledOverIndexAliases = new ArrayList<>();
    getAliasesToConsiderRolling()
      .forEach(indexAlias -> {
        try {
          boolean isRolledOver = databaseClient.triggerRollover(indexAlias, getMaxIndexSizeGB());
          if (isRolledOver) {
            rolledOverIndexAliases.add(indexAlias);
          }
        } catch (Exception e) {
          log.warn("Failed rolling over index {}, will try again next time.", indexAlias, e);
        }
      });
    return rolledOverIndexAliases;
  }

  @Override
  protected void run() {
    triggerRollover();
  }

  @Override
  protected Trigger createScheduleTrigger() {
    return new PeriodicTrigger(Duration.ofMinutes(getScheduleIntervalInMinutes()));
  }

  protected abstract Set<String> getAliasesToConsiderRolling();

  protected abstract int getMaxIndexSizeGB();

  protected abstract int getScheduleIntervalInMinutes();

}
