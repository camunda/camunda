/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.events.rollover;

import lombok.SneakyThrows;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.db.DatabaseConstants.CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX;
import static org.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_EVENTS_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.VARIABLE_UPDATE_INSTANCE_INDEX_NAME;

@Component
public class EventIndexRolloverService extends AbstractIndexRolloverService {

  private final ConfigurationService configurationService;

  protected EventIndexRolloverService(final DatabaseClient databaseClient,
                                   final ConfigurationService configurationService) {
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
    return databaseClient.getAliasesForIndex(CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX + "*")
      .values()
      .stream()
      .flatMap(Set::stream)
      .map(alias -> alias.substring(databaseClient.getIndexNameService().getIndexPrefix().length() + 1))
      .collect(Collectors.toSet());
  }

}
