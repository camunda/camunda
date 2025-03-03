/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.db;

import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.es.MappingMetadataUtilES;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.schema.ElasticSearchMetadataService;
import io.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import io.camunda.optimize.service.db.os.MappingMetadataUtilOS;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.schema.OpenSearchMetadataService;
import io.camunda.optimize.service.db.os.schema.OpenSearchSchemaManager;
import io.camunda.optimize.service.db.schema.DatabaseMetadataService;
import io.camunda.optimize.service.db.schema.DatabaseSchemaManager;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import io.camunda.optimize.service.util.mapper.OptimizeDateTimeFormatterFactory;
import io.camunda.optimize.upgrade.es.SchemaUpgradeClientES;
import io.camunda.optimize.upgrade.os.SchemaUpgradeClientOS;
import io.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;

public final class SchemaUpgradeClientFactory {

  private SchemaUpgradeClientFactory() {}

  public static SchemaUpgradeClient<?, ?, ?> createSchemaUpgradeClient(
      final UpgradeExecutionDependencies upgradeDependencies) {
    if (upgradeDependencies.databaseType().equals(DatabaseType.ELASTICSEARCH)) {
      final OptimizeElasticsearchClient esClient =
          (OptimizeElasticsearchClient) upgradeDependencies.databaseClient();
      final ElasticSearchMetadataService metadataService =
          (ElasticSearchMetadataService) upgradeDependencies.metadataService();
      final MappingMetadataUtilES mappingUtil = new MappingMetadataUtilES(esClient);
      return new SchemaUpgradeClientES(
          new ElasticSearchSchemaManager(
              metadataService,
              upgradeDependencies.configurationService(),
              upgradeDependencies.indexNameService(),
              mappingUtil.getAllMappings(upgradeDependencies.indexNameService().getIndexPrefix())),
          metadataService,
          upgradeDependencies.configurationService(),
          esClient,
          new ObjectMapperFactory(new OptimizeDateTimeFormatterFactory().getObject())
              .createOptimizeMapper());
    } else if (upgradeDependencies.databaseType().equals(DatabaseType.OPENSEARCH)) {
      final OptimizeOpenSearchClient osClient =
          (OptimizeOpenSearchClient) upgradeDependencies.databaseClient();
      final OpenSearchMetadataService metadataService =
          (OpenSearchMetadataService) upgradeDependencies.metadataService();
      final MappingMetadataUtilOS mappingUtil = new MappingMetadataUtilOS(osClient);
      return new SchemaUpgradeClientOS(
          new OpenSearchSchemaManager(
              metadataService,
              upgradeDependencies.configurationService(),
              upgradeDependencies.indexNameService(),
              mappingUtil.getAllMappings(upgradeDependencies.indexNameService().getIndexPrefix())),
          metadataService,
          osClient,
          new ObjectMapperFactory(new OptimizeDateTimeFormatterFactory().getObject())
              .createOptimizeMapper());
    } else {
      throw new UnsupportedOperationException(
          "Database type "
              + upgradeDependencies.databaseType()
              + " not supported for schema upgrade");
    }
  }

  public static SchemaUpgradeClient createSchemaUpgradeClient(
      final DatabaseSchemaManager schemaManager,
      final DatabaseMetadataService metadataService,
      final ConfigurationService configurationService,
      final DatabaseClient dbClient) {

    if (dbClient instanceof final OptimizeElasticsearchClient esClient) {
      return new SchemaUpgradeClientES(
          (ElasticSearchSchemaManager) schemaManager,
          (ElasticSearchMetadataService) metadataService,
          configurationService,
          esClient,
          new ObjectMapperFactory(new OptimizeDateTimeFormatterFactory().getObject())
              .createOptimizeMapper());
    } else if (dbClient instanceof final OptimizeOpenSearchClient osClient) {
      return new SchemaUpgradeClientOS(
          (OpenSearchSchemaManager) schemaManager,
          (OpenSearchMetadataService) metadataService,
          osClient,
          new ObjectMapperFactory(new OptimizeDateTimeFormatterFactory().getObject())
              .createOptimizeMapper());
    } else {
      throw new UnsupportedOperationException(
          "Database type " + dbClient.getClass() + " not supported for schema upgrade");
    }
  }
}
