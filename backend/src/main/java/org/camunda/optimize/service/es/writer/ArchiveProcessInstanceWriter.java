/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.writer;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceArchiveIndex;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;


@AllArgsConstructor
@Component
@Slf4j
public class ArchiveProcessInstanceWriter implements ConfigurationReloadable {

  private final Set<String> existingArchiveInstanceIndexDefinitionKeys = ConcurrentHashMap.newKeySet();

  private final ElasticSearchSchemaManager elasticSearchSchemaManager;
  private final OptimizeElasticsearchClient esClient;

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    existingArchiveInstanceIndexDefinitionKeys.clear();
  }

  public void createInstanceIndicesIfMissing(final Set<String> processDefinitionKeys) {
    final Set<String> missingArchiveInstanceIndexKeys = new HashSet<>(processDefinitionKeys);
    missingArchiveInstanceIndexKeys.removeIf(this::indexExists);
    if (!missingArchiveInstanceIndexKeys.isEmpty()) {
      createMissingInstanceIndices(missingArchiveInstanceIndexKeys);
    }
  }

  private void createMissingInstanceIndices(final Set<String> defKeysOfMissingArchiveIndices) {
    log.debug("Creating process instance archive indices for definition keys [{}].", defKeysOfMissingArchiveIndices);
    defKeysOfMissingArchiveIndices.forEach(defKey -> elasticSearchSchemaManager.createOrUpdateOptimizeIndex(
      esClient,
      new ProcessInstanceArchiveIndex(defKey),
      Collections.singleton(PROCESS_INSTANCE_MULTI_ALIAS)
    ));
    existingArchiveInstanceIndexDefinitionKeys.addAll(defKeysOfMissingArchiveIndices);
  }

  private boolean indexExists(final String definitionKey) {
    return existingArchiveInstanceIndexDefinitionKeys.contains(definitionKey)
      || elasticSearchSchemaManager.indexExists(esClient, new ProcessInstanceArchiveIndex(definitionKey).getIndexName()
    );
  }

}
