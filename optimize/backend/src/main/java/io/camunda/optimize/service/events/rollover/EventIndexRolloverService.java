/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.events.rollover;

import static io.camunda.optimize.service.db.DatabaseConstants.CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_EVENTS_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.VARIABLE_UPDATE_INSTANCE_INDEX_NAME;

import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

@Component
public class EventIndexRolloverService extends AbstractIndexRolloverService {

  private final ConfigurationService configurationService;

  protected EventIndexRolloverService(
      final DatabaseClient databaseClient, final ConfigurationService configurationService) {
    super(databaseClient);
    this.configurationService = configurationService;
  }

  @Override
  protected Set<String> getAliasesToConsiderRolling() {
    final Set<String> aliasesToConsiderRolling = getCamundaActivityEventsIndexAliases();
    aliasesToConsiderRolling.add(EXTERNAL_EVENTS_INDEX_NAME);
    aliasesToConsiderRolling.add(VARIABLE_UPDATE_INSTANCE_INDEX_NAME);
    return aliasesToConsiderRolling;
  }

  @Override
  protected int getMaxIndexSizeGB() {
    return configurationService.getEventIndexRolloverConfiguration().getMaxIndexSizeGB();
  }

  @Override
  protected int getScheduleIntervalInMinutes() {
    return configurationService.getEventIndexRolloverConfiguration().getScheduleIntervalInMinutes();
  }

  @SneakyThrows
  private Set<String> getCamundaActivityEventsIndexAliases() {
    return databaseClient
        .getAliasesForIndexPattern(CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX + "*")
        .values()
        .stream()
        .flatMap(Set::stream)
        .map(
            alias ->
                alias.substring(databaseClient.getIndexNameService().getIndexPrefix().length() + 1))
        .collect(Collectors.toSet());
  }
}
