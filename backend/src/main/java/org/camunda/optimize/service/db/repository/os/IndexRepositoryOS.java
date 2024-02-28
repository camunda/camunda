/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository.os;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.os.schema.OpenSearchSchemaManager;
import org.camunda.optimize.service.db.repository.IndexRepository;
import org.camunda.optimize.service.db.schema.IndexMappingCreator;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(OpenSearchCondition.class)
public class IndexRepositoryOS implements IndexRepository, ConfigurationReloadable {
  private final OptimizeOpenSearchClient osClient;
  private final OpenSearchSchemaManager openSearchSchemaManager;
  private final OptimizeIndexNameService indexNameService;
  private final Set<String> indices = ConcurrentHashMap.newKeySet();

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
      IndexMappingCreator<IndexSettings.Builder> indexMappingCreator,
      final Set<String> readOnlyAliases) {
    log.debug("Creating index {}.", getIndexName(indexMappingCreator));
    openSearchSchemaManager.createOrUpdateOptimizeIndex(
        osClient, indexMappingCreator, readOnlyAliases);
    indices.add(getIndexName(indexMappingCreator));
  }

  private boolean indexExists(final String index) {
    return indices.contains(index) || openSearchSchemaManager.indexExists(osClient, index);
  }

  private String getIndexName(IndexMappingCreator<IndexSettings.Builder> indexMappingCreator) {
    return indexNameService.getOptimizeIndexNameWithVersion(indexMappingCreator);
  }
}
