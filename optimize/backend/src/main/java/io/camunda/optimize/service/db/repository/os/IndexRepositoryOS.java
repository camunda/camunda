/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.os;

import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.schema.OpenSearchSchemaManager;
import io.camunda.optimize.service.db.repository.IndexRepository;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder;
import io.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.slf4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class IndexRepositoryOS implements IndexRepository, ConfigurationReloadable {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(IndexRepositoryOS.class);
  private final OptimizeOpenSearchClient osClient;
  private final OpenSearchSchemaManager openSearchSchemaManager;
  private final OptimizeIndexNameService indexNameService;
  private final Set<String> indices = ConcurrentHashMap.newKeySet();

  public IndexRepositoryOS(
      final OptimizeOpenSearchClient osClient,
      final OpenSearchSchemaManager openSearchSchemaManager,
      final OptimizeIndexNameService indexNameService) {
    this.osClient = osClient;
    this.openSearchSchemaManager = openSearchSchemaManager;
    this.indexNameService = indexNameService;
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    indices.clear();
  }

  @Override
  public void createMissingIndices(
      final IndexMappingCreatorBuilder indexMappingCreatorBuilder,
      final Set<String> readOnlyAliases,
      final Set<String> keys) {
    keys.stream()
        .map(indexMappingCreatorBuilder.getOpensearch())
        .filter(indexMappingCreator -> !indexExists(getIndexName(indexMappingCreator)))
        .forEach(indexMappingCreator -> createMissingIndex(indexMappingCreator, readOnlyAliases));
  }

  @Override
  public boolean indexExists(
      final IndexMappingCreatorBuilder indexMappingCreatorBuilder, final String key) {
    return indexExists(indexMappingCreatorBuilder.getOpensearch().apply(key).getIndexName());
  }

  private void createMissingIndex(
      final IndexMappingCreator<IndexSettings.Builder> indexMappingCreator,
      final Set<String> readOnlyAliases) {
    log.debug("Creating index {}.", getIndexName(indexMappingCreator));
    openSearchSchemaManager.createOrUpdateOptimizeIndex(
        osClient, indexMappingCreator, readOnlyAliases);
    indices.add(getIndexName(indexMappingCreator));
  }

  private boolean indexExists(final String index) {
    return indices.contains(index) || openSearchSchemaManager.indexExists(osClient, index);
  }

  private String getIndexName(
      final IndexMappingCreator<IndexSettings.Builder> indexMappingCreator) {
    return indexNameService.getOptimizeIndexNameWithVersion(indexMappingCreator);
  }
}
