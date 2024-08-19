/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import io.camunda.optimize.service.db.repository.IndexRepository;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder;
import io.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.elasticsearch.xcontent.XContentBuilder;
import org.slf4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class IndexRepositoryES implements IndexRepository, ConfigurationReloadable {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(IndexRepositoryES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ElasticSearchSchemaManager elasticSearchSchemaManager;
  private final OptimizeIndexNameService indexNameService;
  private final Set<String> indices = ConcurrentHashMap.newKeySet();

  public IndexRepositoryES(
      final OptimizeElasticsearchClient esClient,
      final ElasticSearchSchemaManager elasticSearchSchemaManager,
      final OptimizeIndexNameService indexNameService) {
    this.esClient = esClient;
    this.elasticSearchSchemaManager = elasticSearchSchemaManager;
    this.indexNameService = indexNameService;
  }

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
  public boolean indexExists(
      final IndexMappingCreatorBuilder indexMappingCreatorBuilder, final String key) {
    return indexExists(indexMappingCreatorBuilder.getElasticsearch().apply(key).getIndexName());
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    indices.clear();
  }

  private String getIndexName(final IndexMappingCreator<XContentBuilder> indexMappingCreator) {
    return indexNameService.getOptimizeIndexNameWithVersion(indexMappingCreator);
  }

  private void createMissingIndex(
      final IndexMappingCreator<XContentBuilder> indexMappingCreator,
      final Set<String> readOnlyAliases) {
    log.debug("Creating index {}.", getIndexName(indexMappingCreator));

    elasticSearchSchemaManager.createOrUpdateOptimizeIndex(
        esClient, indexMappingCreator, readOnlyAliases);

    final String index = getIndexName(indexMappingCreator);

    indices.add(index);
  }

  private boolean indexExists(final String index) {
    return indices.contains(index) || elasticSearchSchemaManager.indexExists(esClient, index);
  }
}
