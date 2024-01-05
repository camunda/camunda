/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.es;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.es.schema.ElasticSearchMetadataService;
import org.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.db.schema.IndexMappingCreator;
import org.camunda.optimize.service.db.schema.MappingMetadataUtil;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.util.OptimizeDateTimeFormatterFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.elasticsearch.xcontent.XContentBuilder;

import java.util.List;

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

  public static SchemaUpgradeClient createSchemaUpgradeClient(final ElasticSearchMetadataService metadataService,
                                                              final ConfigurationService configurationService,
                                                              final OptimizeIndexNameService indexNameService,
                                                              final OptimizeElasticsearchClient esClient) {
    MappingMetadataUtil mappingUtil = new MappingMetadataUtil(esClient);
    // TODO remove call to convert list with OPT-7238
    return createSchemaUpgradeClient(
      new ElasticSearchSchemaManager(metadataService, configurationService, indexNameService, convertList(mappingUtil.getAllMappings())),
      metadataService,
      configurationService,
      esClient
    );
  }

  public static SchemaUpgradeClient createSchemaUpgradeClient(final ElasticSearchSchemaManager schemaManager,
                                                              final ElasticSearchMetadataService metadataService,
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

  // TODO delete with OPT-7238
  @SuppressWarnings("unchecked") // Suppress unchecked cast warnings
  private static List<IndexMappingCreator<XContentBuilder>> convertList(List<IndexMappingCreator<?>> wildcardList) {
    return wildcardList.stream()
      .map(creator -> (IndexMappingCreator<XContentBuilder>) creator) // Unchecked cast
      .toList();
  }

}
