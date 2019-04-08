/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.reimport.preparation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import org.camunda.optimize.jetty.util.LoggingConfigurationReader;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper;
import org.camunda.optimize.service.es.schema.TypeMappingCreator;
import org.camunda.optimize.service.es.schema.type.DecisionDefinitionType;
import org.camunda.optimize.service.es.schema.type.DecisionInstanceType;
import org.camunda.optimize.service.es.schema.type.ProcessDefinitionType;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.es.schema.type.index.ImportIndexType;
import org.camunda.optimize.service.es.schema.type.index.TimestampBasedImportIndexType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
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

  private static ConfigurationService configurationService = new ConfigurationService();

  private static final List<TypeMappingCreator> TYPES_TO_CLEAR = Lists.newArrayList(
    new ImportIndexType(), new TimestampBasedImportIndexType(),
    new ProcessDefinitionType(), new ProcessInstanceType(),
    new DecisionDefinitionType(), new DecisionInstanceType()
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

      deleteImportAndEngineDataIndexes(restHighLevelClient);
      recreateImportAndEngineDataIndexes(restHighLevelClient);

      logger.info(
        "Optimize was successfully prepared such it can reimport the engine data. Feel free to start Optimize again!"
      );
    } catch (Exception e) {
      logger.error("Failed preparing Optimize for reimport.", e);
    }

  }

  private static void deleteImportAndEngineDataIndexes(RestHighLevelClient restHighLevelClient) {
    logger.info("Deleting import and engine data indexes from Optimize...");

    TYPES_TO_CLEAR.stream()
      .map(OptimizeIndexNameHelper::getVersionedOptimizeIndexNameForTypeMapping)
      .forEach(indexName -> {
        final Request request = new Request("DELETE", "/" + indexName);
        try {
          restHighLevelClient.getLowLevelClient().performRequest(request);
        } catch (IOException e) {
          logger.warn("Failed to delete index {}, reason: {}", indexName, e.getMessage());
          throw new RuntimeException(e);
        }
      });

    logger.info("Finished deleting import and engine data indexes from Elasticsearch.");
  }

  private static void recreateImportAndEngineDataIndexes(RestHighLevelClient restHighLevelClient) {
    logger.info("Recreating import indexes and engine data from Optimize...");

    final ObjectMapper objectMapper = new ObjectMapper();
    final ElasticSearchSchemaManager schemaManager = new ElasticSearchSchemaManager(
      new ElasticsearchMetadataService(objectMapper),
      configurationService,
      TYPES_TO_CLEAR,
      objectMapper
    );

    schemaManager.createOptimizeIndices(restHighLevelClient);

    logger.info("Finished recreating import and engine data indexes from Elasticsearch.");
  }
}
