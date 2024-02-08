/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.schema;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Component
@Slf4j
public abstract class DatabaseSchemaManager<CLIENT extends DatabaseClient, BUILDER> {

  protected final ConfigurationService configurationService;
  protected final OptimizeIndexNameService indexNameService;

  @Getter
  protected final List<IndexMappingCreator<BUILDER>> mappings;

  protected DatabaseSchemaManager(final ConfigurationService configurationService,
                                  final OptimizeIndexNameService indexNameService,
                                  final List<IndexMappingCreator<BUILDER>> mappings) {
    this.configurationService = configurationService;
    this.indexNameService = indexNameService;
    this.mappings = mappings;
  }

  public abstract void validateExistingSchemaVersion(final CLIENT dbClient);

  public abstract void initializeSchema(final CLIENT dbClient);

  public abstract boolean schemaExists(CLIENT dbClient);

  public abstract boolean indexExists(final CLIENT dbClient, final IndexMappingCreator<BUILDER> mapping);

  public abstract boolean indexExists(final CLIENT dbClient, final String indexName);

  public abstract boolean indicesExist(final CLIENT dbClient, final List<IndexMappingCreator<BUILDER>> mappings);

  public abstract void createIndexIfMissing(final CLIENT dbClient, final IndexMappingCreator<BUILDER> indexMapping);

  public abstract void createIndexIfMissing(final CLIENT dbClient, final IndexMappingCreator<BUILDER> indexMapping,
                                            final Set<String> additionalReadOnlyAliases);

  public abstract void createOrUpdateOptimizeIndex(final CLIENT dbClient,
                                                   final IndexMappingCreator<BUILDER> mapping,
                                                   final Set<String> readOnlyAliases);

  public abstract void deleteOptimizeIndex(final CLIENT dbClient, final IndexMappingCreator<BUILDER> mapping);

  public abstract void createOrUpdateTemplateWithoutAliases(final CLIENT dbClient,
                                                            final IndexMappingCreator<BUILDER> mappingCreator);

  public abstract void updateDynamicSettingsAndMappings(CLIENT dbClient, IndexMappingCreator<BUILDER> indexMapping);

  public void addMapping(final IndexMappingCreator<BUILDER> mapping) {
    mappings.add(mapping);
  }

  public void createOptimizeIndices(CLIENT dbClient) {
    for (IndexMappingCreator<BUILDER> mapping : mappings) {
      createOrUpdateOptimizeIndex(dbClient, mapping);
    }
  }

  public void createOrUpdateOptimizeIndex(final CLIENT dbClient,
                                          final IndexMappingCreator<BUILDER> mapping) {
    createOrUpdateOptimizeIndex(dbClient, mapping, Collections.emptySet());
  }

}
