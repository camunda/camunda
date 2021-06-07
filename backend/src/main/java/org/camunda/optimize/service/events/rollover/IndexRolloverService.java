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
import org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.client.GetAliasesResponse;
import org.elasticsearch.cluster.metadata.AliasMetadata;
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
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.VARIABLE_UPDATE_INSTANCE_INDEX_NAME;

@RequiredArgsConstructor
@Component
@Slf4j
public class IndexRolloverService extends AbstractScheduledService {

  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;

  @Override
  protected void run() {
    triggerRollover();
  }

  @Override
  protected Trigger createScheduleTrigger() {
    return new PeriodicTrigger(
      configurationService.getEventBasedProcessConfiguration()
        .getEventIndexRollover()
        .getScheduleIntervalInMinutes(),
      TimeUnit.MINUTES
    );
  }

  public List<String> triggerRollover() {
    List<String> rolledOverIndexAliases = new ArrayList<>();
    final Set<String> aliasesToConsiderRolling = getCamundaActivityEventsIndexAliases();
    aliasesToConsiderRolling.add(EXTERNAL_EVENTS_INDEX_NAME);
    aliasesToConsiderRolling.add(VARIABLE_UPDATE_INSTANCE_INDEX_NAME);
    aliasesToConsiderRolling
      .forEach(indexAlias -> {
        try {
          boolean isRolledOver = ElasticsearchWriterUtil.triggerRollover(
            esClient,
            indexAlias,
            configurationService.getEventIndexRolloverConfiguration().getMaxIndexSizeGB()
          );
          if (isRolledOver) {
            rolledOverIndexAliases.add(indexAlias);
          }
        } catch (Exception e) {
          log.warn("Failed rolling over index {}, will try again next time.", indexAlias, e);
        }
      });
    return rolledOverIndexAliases;
  }

  @SneakyThrows
  private Set<String> getCamundaActivityEventsIndexAliases() {
    final GetAliasesResponse aliases =
      esClient.getAlias(new GetAliasesRequest(CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX + "*"));
    return aliases.getAliases()
      .values()
      .stream()
      .flatMap(aliasMetaDataPerIndex -> aliasMetaDataPerIndex.stream().map(AliasMetadata::alias))
      .map(alias -> alias.substring(configurationService.getEsIndexPrefix().length() + 1))
      .collect(Collectors.toSet());
  }

}
