/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.reimport.preparation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import org.camunda.optimize.jetty.util.LoggingConfigurationReader;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.index.ImportIndexIndex;
import org.camunda.optimize.service.es.schema.index.index.TimestampBasedImportIndex;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Deletes all engine data and the import indexes from Elasticsearch
 * such that Optimize reimports all data from the engine, but keeps
 * all reports, dashboards and alerts that were defined before.
 */
public class ReimportPreparation {

  private static Logger logger = LoggerFactory.getLogger(ReimportPreparation.class);

  private static ConfigurationService configurationService = ConfigurationServiceBuilder.createDefaultConfiguration();

  private static final List<IndexMappingCreator> TYPES_TO_CLEAR = Lists.newArrayList(
    new ImportIndexIndex(), new TimestampBasedImportIndex(),
    new ProcessDefinitionIndex(), new ProcessInstanceIndex(),
    new DecisionDefinitionIndex(), new DecisionInstanceIndex()
  );

  public static void main(String[] args) {
    logger.info("Start to prepare Elasticsearch such that Optimize reimports engine data!");

    logger.info("Reading configuration...");
    LoggingConfigurationReader loggingConfigurationReader = new LoggingConfigurationReader();
    loggingConfigurationReader.defineLogbackLoggingConfiguration();
    logger.info("Successfully read configuration.");

    logger.info("Creating connection to Elasticsearch...");
    try (RestHighLevelClient restHighLevelClient =
           ElasticsearchHighLevelRestClientBuilder.build(configurationService)) {
      logger.info("Successfully created connection to Elasticsearch.");

      final OptimizeElasticsearchClient prefixAwareClient = new OptimizeElasticsearchClient(
        restHighLevelClient,
        new OptimizeIndexNameService(configurationService)
      );

      deleteImportAndEngineDataIndexes(prefixAwareClient);
      recreateImportAndEngineDataIndexes(prefixAwareClient);

      logger.info(
        "Optimize was successfully prepared such it can reimport the engine data. Feel free to start Optimize again!"
      );
    } catch (Exception e) {
      logger.error("Failed preparing Optimize for reimport.", e);
    }

  }

  private static void deleteImportAndEngineDataIndexes(OptimizeElasticsearchClient prefixAwareClient) {
    logger.info("Deleting import and engine data indexes from Optimize...");

    TYPES_TO_CLEAR.stream()
      .map(type -> prefixAwareClient.getIndexNameService().getVersionedOptimizeIndexNameForTypeMapping(type))
      .forEach(indexName -> {
        final Request request = new Request("DELETE", "/" + indexName);
        try {
          prefixAwareClient.getLowLevelClient().performRequest(request);
        } catch (IOException e) {
          logger.warn("Failed to delete index {}, reason: {}", indexName, e.getMessage());
          throw new RuntimeException(e);
        }
      });

    logger.info("Finished deleting import and engine data indexes from Elasticsearch.");
  }

  private static void recreateImportAndEngineDataIndexes(OptimizeElasticsearchClient prefixAwareClient) {
    logger.info("Recreating import indexes and engine data from Optimize...");

    final ObjectMapper objectMapper = new ObjectMapper();
    final ElasticSearchSchemaManager schemaManager = new ElasticSearchSchemaManager(
      new ElasticsearchMetadataService(objectMapper),
      configurationService,
      prefixAwareClient.getIndexNameService(),
      TYPES_TO_CLEAR,
      objectMapper
    );

    schemaManager.createOptimizeIndices(prefixAwareClient.getHighLevelClient());

    logger.info("Finished recreating import and engine data indexes from Elasticsearch.");
  }
}
