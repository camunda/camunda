/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.db;

import io.camunda.optimize.service.db.es.MappingMetadataUtilES;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.schema.ElasticSearchMetadataService;
import io.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import io.camunda.optimize.service.util.mapper.OptimizeDateTimeFormatterFactory;
import io.camunda.optimize.upgrade.es.SchemaUpgradeClientES;
import io.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.NotImplementedException;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SchemaUpgradeClientFactory {

  public static SchemaUpgradeClient<?, ?> createSchemaUpgradeClient(
      final UpgradeExecutionDependencies upgradeDependencies) {
    if (upgradeDependencies.databaseType().equals(DatabaseType.ELASTICSEARCH)) {
      final OptimizeElasticsearchClient esClient =
          (OptimizeElasticsearchClient) upgradeDependencies.databaseClient();
      final ElasticSearchMetadataService metadataService =
          (ElasticSearchMetadataService) upgradeDependencies.metadataService();
      MappingMetadataUtilES mappingUtil = new MappingMetadataUtilES(esClient);
      return createSchemaUpgradeClient(
          new ElasticSearchSchemaManager(
              metadataService,
              upgradeDependencies.configurationService(),
              upgradeDependencies.indexNameService(),
              mappingUtil.getAllMappings(upgradeDependencies.indexNameService().getIndexPrefix())),
          metadataService,
          upgradeDependencies.configurationService(),
          esClient);
    } else {
      // TODO create the schema client for OS upgrades
      throw new NotImplementedException("Schema client not implemented for Opensearch");
    }
  }

  // TODO at the moment this is a test utility but should be removed/chanegd when OpenSearch tests
  // are possible
  public static SchemaUpgradeClientES createSchemaUpgradeClient(
      final ElasticSearchSchemaManager schemaManager,
      final ElasticSearchMetadataService metadataService,
      final ConfigurationService configurationService,
      final OptimizeElasticsearchClient esClient) {
    return new SchemaUpgradeClientES(
        schemaManager,
        metadataService,
        esClient,
        new ObjectMapperFactory(
                new OptimizeDateTimeFormatterFactory().getObject(), configurationService)
            .createOptimizeMapper());
  }
}
