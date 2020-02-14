/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.event;

import com.google.common.collect.ImmutableList;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.importing.event.mediator.EventTraceImportMediator;
import org.camunda.optimize.service.importing.event.mediator.EventTraceImportMediatorFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.client.GetAliasesResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;

@AllArgsConstructor
@Component
public class EventTraceImportMediatorManager implements ConfigurationReloadable {

  private final OptimizeElasticsearchClient elasticsearchClient;
  private final EventTraceImportMediatorFactory eventTraceImportMediatorFactory;

  private final Map<String, EventTraceImportMediator> mediators = new ConcurrentHashMap<>();

  @SneakyThrows
  public List<EventTraceImportMediator> getEventTraceImportMediators() {
    refreshMediators();
    return ImmutableList.copyOf(mediators.values());
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    mediators.clear();
  }

  private void refreshMediators() {
    mediators.computeIfAbsent(
      EXTERNAL_EVENTS_INDEX_SUFFIX,
      key -> eventTraceImportMediatorFactory.createExternalEventTraceImportMediator()
    );

    final Set<String> definitionKeysOfActivityEvents = getDefinitionKeysOfCurrentActivityIndices();
    definitionKeysOfActivityEvents
      .stream()
      .filter(definitionKey -> !mediators.containsKey(definitionKey))
      .forEach(definitionKey -> {
        mediators.put(
          definitionKey, eventTraceImportMediatorFactory.createCamundaEventTraceImportMediator(definitionKey)
        );
      });
  }

  @SneakyThrows
  private Set<String> getDefinitionKeysOfCurrentActivityIndices() {
    final GetAliasesResponse aliases = elasticsearchClient.getAlias(
      new GetAliasesRequest(CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX + "*"), RequestOptions.DEFAULT
    );
    return aliases.getAliases()
      .values()
      .stream()
      .flatMap(aliasMetaDataPerIndex -> aliasMetaDataPerIndex.stream().map(AliasMetaData::alias))
      .map(fullAliasName -> fullAliasName.substring(
        fullAliasName.lastIndexOf(CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX) + CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX.length()
      ))
      .collect(Collectors.toSet());
  }
}
