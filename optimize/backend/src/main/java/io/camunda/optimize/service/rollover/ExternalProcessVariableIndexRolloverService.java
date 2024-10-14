/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.rollover;

import static io.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_PROCESS_VARIABLE_INDEX_NAME;

import io.camunda.optimize.service.AbstractScheduledService;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

@Component
public class ExternalProcessVariableIndexRolloverService extends AbstractScheduledService {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ExternalProcessVariableIndexRolloverService.class);
  private final DatabaseClient databaseClient;
  private final ConfigurationService configurationService;

  public ExternalProcessVariableIndexRolloverService(
      final DatabaseClient databaseClient, final ConfigurationService configurationService) {
    this.databaseClient = databaseClient;
    this.configurationService = configurationService;
  }

  @Override
  protected void run() {
    triggerRollover();
  }

  @Override
  protected Trigger createScheduleTrigger() {
    return new PeriodicTrigger(Duration.ofMinutes(getScheduleIntervalInMinutes()));
  }

  public List<String> triggerRollover() {
    List<String> rolledOverIndexAliases = new ArrayList<>();
    getAliasesToConsiderRolling()
        .forEach(
            indexAlias -> {
              try {
                boolean isRolledOver =
                    databaseClient.triggerRollover(indexAlias, getMaxIndexSizeGB());
                if (isRolledOver) {
                  rolledOverIndexAliases.add(indexAlias);
                }
              } catch (Exception e) {
                log.warn("Failed rolling over index {}, will try again next time.", indexAlias, e);
              }
            });
    return rolledOverIndexAliases;
  }

  private Set<String> getAliasesToConsiderRolling() {
    return Collections.singleton(EXTERNAL_PROCESS_VARIABLE_INDEX_NAME);
  }

  private int getMaxIndexSizeGB() {
    return configurationService.getVariableIndexRolloverConfiguration().getMaxIndexSizeGB();
  }

  private int getScheduleIntervalInMinutes() {
    return configurationService
        .getVariableIndexRolloverConfiguration()
        .getScheduleIntervalInMinutes();
  }
}
