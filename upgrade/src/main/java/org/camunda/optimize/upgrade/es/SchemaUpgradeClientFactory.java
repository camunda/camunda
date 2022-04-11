/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.es;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.util.OptimizeDateTimeFormatterFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;

import static org.camunda.optimize.service.es.schema.MappingMetadataUtil.getAllMappings;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SchemaUpgradeClientFactory {
  public static SchemaUpgradeClient createSchemaUpgradeClient(final UpgradeExecutionDependencies upgradeDependencies) {
    return createSchemaUpgradeClient(
      upgradeDependencies.getMetadataService(),
      upgradeDependencies.getConfigurationService(),
      upgradeDependencies.getIndexNameService(),
      upgradeDependencies.getEsClient()
    );
  }

  public static SchemaUpgradeClient createSchemaUpgradeClient(final ElasticsearchMetadataService metadataService,
                                                              final ConfigurationService configurationService,
                                                              final OptimizeIndexNameService indexNameService,
                                                              final OptimizeElasticsearchClient esClient) {
    return createSchemaUpgradeClient(
      new ElasticSearchSchemaManager(metadataService, configurationService, indexNameService, getAllMappings(esClient)),
      metadataService,
      configurationService,
      esClient
    );
  }

  public static SchemaUpgradeClient createSchemaUpgradeClient(final ElasticSearchSchemaManager schemaManager,
                                                              final ElasticsearchMetadataService metadataService,
                                                              final ConfigurationService configurationService,
                                                              final OptimizeElasticsearchClient esClient) {
    return new SchemaUpgradeClient(
      schemaManager,
      metadataService,
      esClient,
      new ObjectMapperFactory(new OptimizeDateTimeFormatterFactory().getObject(), configurationService)
        .createOptimizeMapper()
    );
  }
}
