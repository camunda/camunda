/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository.es;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.db.repository.IndexRepository;
import org.camunda.optimize.service.db.schema.IndexMappingCreator;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.xcontent.XContentBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class IndexRepositoryES implements IndexRepository, ConfigurationReloadable {
  private final OptimizeElasticsearchClient esClient;
  private final ElasticSearchSchemaManager elasticSearchSchemaManager;
  private final OptimizeIndexNameService indexNameService;
  private final Set<String> indices = ConcurrentHashMap.newKeySet();

  @Override
  public void createMissingIndices(
      final IndexMappingCreatorBuilder indexMappingCreatorBuilder,
      final Set<String> readOnlyAliases,
      final Set<String> keys) {
    keys.stream()
        .map(indexMappingCreatorBuilder.getElasticsearch())
        .filter(indexMappingCreator -> !indexExists(getIndexName(indexMappingCreator)))
        .forEach(indexMappingCreator -> createMissingIndex(indexMappingCreator, readOnlyAliases));
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    indices.clear();
  }

  @Override
  public boolean indexExists(
      final IndexMappingCreatorBuilder indexMappingCreatorBuilder, final String key) {
    return indexExists(indexMappingCreatorBuilder.getElasticsearch().apply(key).getIndexName());
  }

  private String getIndexName(IndexMappingCreator<XContentBuilder> indexMappingCreator) {
    return indexNameService.getOptimizeIndexNameWithVersion(indexMappingCreator);
  }

  private void createMissingIndex(
      IndexMappingCreator<XContentBuilder> indexMappingCreator, final Set<String> readOnlyAliases) {
    log.debug("Creating index {}.", getIndexName(indexMappingCreator));

    elasticSearchSchemaManager.createOrUpdateOptimizeIndex(
        esClient, indexMappingCreator, readOnlyAliases);

    String index = getIndexName(indexMappingCreator);

    indices.add(index);
  }

  private boolean indexExists(final String index) {
    return indices.contains(index) || elasticSearchSchemaManager.indexExists(esClient, index);
  }
}
