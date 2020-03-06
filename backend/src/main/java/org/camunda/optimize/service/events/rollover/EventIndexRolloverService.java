/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.events.rollover;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.AbstractScheduledService;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.ElasticsearchHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.client.GetAliasesResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX;
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

  public List<String> triggerRollover() {
    List<String> rolledOverIndexNames = new ArrayList<>();
    if (configurationService.getEventBasedProcessConfiguration().isEnabled()) {
      final Set<String> indicesToConsiderRolling = getCamundaActivityEventsIndexAliases();
      indicesToConsiderRolling.add(EXTERNAL_EVENTS_INDEX_NAME);
      indicesToConsiderRolling
        .forEach(indexName -> {
          boolean isRolledOver = ElasticsearchHelper.triggerRollover(
            esClient,
            indexName,
            configurationService.getEventIndexRolloverConfiguration().getMaxIndexSizeGB()
          );
          if (isRolledOver) {
            rolledOverIndexNames.add(indexName);
          }
        });
    }
    return rolledOverIndexNames;
  }

  @SneakyThrows
  private Set<String> getCamundaActivityEventsIndexAliases() {
    final GetAliasesResponse aliases = esClient.getAlias(
      new GetAliasesRequest(CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX + "*"), RequestOptions.DEFAULT
    );
    return aliases.getAliases()
      .values()
      .stream()
      .flatMap(aliasMetaDataPerIndex -> aliasMetaDataPerIndex.stream().map(AliasMetaData::alias))
      .map(alias -> alias.substring(configurationService.getEsIndexPrefix().length() + 1))
      .collect(Collectors.toSet());
  }

}
