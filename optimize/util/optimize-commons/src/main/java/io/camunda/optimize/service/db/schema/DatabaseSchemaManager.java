/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public abstract class DatabaseSchemaManager<CLIENT extends DatabaseClient, BUILDER> {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(DatabaseSchemaManager.class);
  protected ConfigurationService configurationService;
  protected OptimizeIndexNameService indexNameService;
  protected ObjectMapper optimizeObjectMapper;

  protected final List<IndexMappingCreator<BUILDER>> mappings;

  protected DatabaseSchemaManager(
      final ConfigurationService configurationService,
      final OptimizeIndexNameService indexNameService,
      final List<IndexMappingCreator<BUILDER>> mappings,
      final ObjectMapper optimizeObjectMapper) {
    this.configurationService = configurationService;
    this.indexNameService = indexNameService;
    this.mappings = mappings;
    this.optimizeObjectMapper = optimizeObjectMapper;
  }

  public abstract void validateDatabaseMetadata(final CLIENT dbClient);

  public abstract void initializeSchema(final CLIENT dbClient);

  public abstract boolean schemaExists(CLIENT dbClient);

  public abstract boolean indexExists(
      final CLIENT dbClient, final IndexMappingCreator<BUILDER> mapping);

  public abstract boolean indexExists(final CLIENT dbClient, final String indexName);

  public abstract boolean indicesExist(
      final CLIENT dbClient, final List<IndexMappingCreator<BUILDER>> mappings);

  public abstract void createIndexIfMissing(
      final CLIENT dbClient, final IndexMappingCreator<BUILDER> indexMapping);

  public abstract void createIndexIfMissing(
      final CLIENT dbClient,
      final IndexMappingCreator<BUILDER> indexMapping,
      final Set<String> additionalReadOnlyAliases);

  public abstract void createOrUpdateOptimizeIndex(
      final CLIENT dbClient,
      final IndexMappingCreator<BUILDER> mapping,
      final Set<String> readOnlyAliases);

  public abstract void deleteOptimizeIndex(
      final CLIENT dbClient, final IndexMappingCreator<BUILDER> mapping);

  public abstract void createOrUpdateTemplateWithoutAliases(
      final CLIENT dbClient, final IndexMappingCreator<BUILDER> mappingCreator);

  public abstract void updateDynamicSettingsAndMappings(
      CLIENT dbClient, IndexMappingCreator<BUILDER> indexMapping);

  public void addMapping(final IndexMappingCreator<BUILDER> mapping) {
    mappings.add(mapping);
  }

  public void createOptimizeIndices(final CLIENT dbClient) {
    for (final IndexMappingCreator<BUILDER> mapping : mappings) {
      createOrUpdateOptimizeIndex(dbClient, mapping);
    }
  }

  public void createOrUpdateOptimizeIndex(
      final CLIENT dbClient, final IndexMappingCreator<BUILDER> mapping) {
    createOrUpdateOptimizeIndex(dbClient, mapping, Collections.emptySet());
  }

  public void setConfigurationService(final ConfigurationService configurationService) {
    this.configurationService = configurationService;
  }

  public void setIndexNameService(final OptimizeIndexNameService indexNameService) {
    this.indexNameService = indexNameService;
  }

  public List<IndexMappingCreator<BUILDER>> getMappings() {
    return mappings;
  }
}
