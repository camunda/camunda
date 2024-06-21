/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.reimport.preparation;

import static io.camunda.optimize.service.db.DatabaseConstants.CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.DECISION_INSTANCE_INDEX_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_INSTANCE_INDEX_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.EVENT_SEQUENCE_COUNT_INDEX_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.EVENT_TRACE_STATE_INDEX_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_ARCHIVE_INDEX_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_INDEX_PREFIX;
import static io.camunda.optimize.service.util.mapper.ObjectMapperFactory.OPTIMIZE_MAPPER;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.plugin.ElasticsearchCustomHeaderProvider;
import io.camunda.optimize.plugin.PluginJarFileLoader;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.schema.ElasticSearchMetadataService;
import io.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import io.camunda.optimize.service.db.es.schema.RequestOptionsProvider;
import io.camunda.optimize.service.db.es.schema.index.BusinessKeyIndexES;
import io.camunda.optimize.service.db.es.schema.index.DecisionDefinitionIndexES;
import io.camunda.optimize.service.db.es.schema.index.ExternalProcessVariableIndexES;
import io.camunda.optimize.service.db.es.schema.index.ProcessDefinitionIndexES;
import io.camunda.optimize.service.db.es.schema.index.TenantIndexES;
import io.camunda.optimize.service.db.es.schema.index.VariableUpdateInstanceIndexES;
import io.camunda.optimize.service.db.es.schema.index.events.EventProcessDefinitionIndexES;
import io.camunda.optimize.service.db.es.schema.index.events.EventProcessPublishStateIndexES;
import io.camunda.optimize.service.db.es.schema.index.index.ImportIndexIndexES;
import io.camunda.optimize.service.db.es.schema.index.index.TimestampBasedImportIndexES;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import io.camunda.optimize.util.jetty.LoggingConfigurationReader;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.xcontent.XContentBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * Deletes all engine data and the import indexes from Elasticsearch such that Optimize reimports
 * all data from the engine, but keeps all reports, dashboards and alerts that were defined before.
 *
 * <p>External events are kept. Event process data is cleared as well to allow for manual
 * republishing.
 */
@Slf4j
public class ReimportPreparation {

  // TODO deal with this with OPT-7438
  private static final List<IndexMappingCreator<XContentBuilder>> STATIC_INDICES_TO_DELETE =
      List.of(
          new ImportIndexIndexES(),
          new TimestampBasedImportIndexES(),
          new ProcessDefinitionIndexES(),
          new EventProcessDefinitionIndexES(),
          new DecisionDefinitionIndexES(),
          new TenantIndexES(),
          new BusinessKeyIndexES(),
          new VariableUpdateInstanceIndexES(),
          new EventProcessPublishStateIndexES(),
          new ExternalProcessVariableIndexES());

  public static void main(final String[] args) {
    log.info("Start to prepare Elasticsearch such that Optimize reimports engine data!");
    log.info("Reading configuration...");
    final LoggingConfigurationReader loggingConfigurationReader = new LoggingConfigurationReader();
    loggingConfigurationReader.defineLogbackLoggingConfiguration();
    log.info("Successfully read configuration.");
    performReimport(ConfigurationServiceBuilder.createDefaultConfiguration());
  }

  public static void performReimport(final ConfigurationService configurationService) {
    log.info("Creating connection to Elasticsearch...");
    try (final RestHighLevelClient restHighLevelClient =
        ElasticsearchHighLevelRestClientBuilder.build(configurationService)) {
      log.info("Successfully created connection to Elasticsearch.");
      final ElasticsearchCustomHeaderProvider customHeaderProvider =
          new ElasticsearchCustomHeaderProvider(
              configurationService, new PluginJarFileLoader(configurationService));
      customHeaderProvider.initPlugins();
      final OptimizeElasticsearchClient prefixAwareClient =
          new OptimizeElasticsearchClient(
              restHighLevelClient,
              new OptimizeIndexNameService(configurationService, DatabaseType.ELASTICSEARCH),
              new RequestOptionsProvider(customHeaderProvider.getPlugins(), configurationService),
              OPTIMIZE_MAPPER);

      deleteImportAndEngineDataIndices(prefixAwareClient);
      recreateStaticIndices(prefixAwareClient, configurationService);

      log.info(
          "Optimize was successfully prepared such it can reimport the engine data. Feel free to start Optimize again!");
    } catch (final Exception e) {
      log.error("Failed preparing Optimize for reimport.", e);
    }
  }

  private static void deleteImportAndEngineDataIndices(
      final OptimizeElasticsearchClient prefixAwareClient) {
    log.info("Deleting import and engine data indices from Elasticsearch...");
    STATIC_INDICES_TO_DELETE.forEach(prefixAwareClient::deleteIndex);
    log.info("Finished deleting import and engine data indices from Elasticsearch.");

    log.info("Deleting process instance indices from Elasticsearch...");
    deleteIndices(
        prefixAwareClient,
        getAllIndices(prefixAwareClient).stream()
            .filter(
                index ->
                    index.contains(PROCESS_INSTANCE_INDEX_PREFIX)
                        || index.contains(PROCESS_INSTANCE_ARCHIVE_INDEX_PREFIX))
            .toArray(String[]::new));
    log.info("Finished deleting process instance indices from Elasticsearch.");

    log.info("Deleting event process indices and Camunda event data from Elasticsearch...");
    deleteIndices(
        prefixAwareClient,
        getAllIndices(prefixAwareClient).stream()
            .filter(
                index ->
                    index.contains(EVENT_PROCESS_INSTANCE_INDEX_PREFIX)
                        || index.contains(CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX))
            .toArray(String[]::new));
    log.info("Finished deleting event process indices and Camunda event data from Elasticsearch.");

    log.info("Deleting decision instance indices from Elasticsearch...");
    deleteIndices(
        prefixAwareClient,
        getAllIndices(prefixAwareClient).stream()
            .filter(index -> index.contains(DECISION_INSTANCE_INDEX_PREFIX))
            .toArray(String[]::new));
    log.info("Finished deleting decision instance indices from Elasticsearch.");

    log.info("Deleting Camunda event count/trace indices from Elasticsearch...");
    deleteIndices(
        prefixAwareClient,
        getAllIndices(prefixAwareClient).stream()
            .filter(
                index ->
                    !index.contains(EXTERNAL_EVENTS_INDEX_SUFFIX)
                        && (index.contains(EVENT_SEQUENCE_COUNT_INDEX_PREFIX)
                        || index.contains(EVENT_TRACE_STATE_INDEX_PREFIX)))
            .toArray(String[]::new));
    log.info("Finished Camunda event count/trace indices from Elasticsearch.");
  }

  @NotNull
  private static List<String> getAllIndices(final OptimizeElasticsearchClient prefixAwareClient) {
    final List<String> indices;
    try {
      indices = prefixAwareClient.getAllIndexNames();
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(
          "Error while fetching indices. Could not perform Reimport", e);
    }
    return indices;
  }

  private static void deleteIndices(
      final OptimizeElasticsearchClient prefixAwareClient, final String[] indexNames) {
    if (indexNames.length > 0) {
      prefixAwareClient.deleteIndexByRawIndexNames(indexNames);
    }
  }

  private static void recreateStaticIndices(
      final OptimizeElasticsearchClient prefixAwareClient,
      final ConfigurationService configurationService) {
    log.info("Recreating import indices and engine data...");

    final ObjectMapper objectMapper = new ObjectMapper();
    final ElasticSearchSchemaManager schemaManager =
        new ElasticSearchSchemaManager(
            new ElasticSearchMetadataService(objectMapper),
            configurationService,
            prefixAwareClient.getIndexNameService(),
            STATIC_INDICES_TO_DELETE);

    schemaManager.createOptimizeIndices(prefixAwareClient);

    log.info("Finished recreating import and engine data indices from Elasticsearch.");
  }
}
