/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.reimport.preparation;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.plugin.ElasticsearchCustomHeaderProvider;
import org.camunda.optimize.plugin.PluginJarFileLoader;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.RequestOptionsProvider;
import org.camunda.optimize.service.es.schema.index.BusinessKeyIndex;
import org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.ExternalProcessVariableIndex;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.TenantIndex;
import org.camunda.optimize.service.es.schema.index.VariableUpdateInstanceIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessPublishStateIndex;
import org.camunda.optimize.service.es.schema.index.index.ImportIndexIndex;
import org.camunda.optimize.service.es.schema.index.index.TimestampBasedImportIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import org.camunda.optimize.util.jetty.LoggingConfigurationReader;
import org.elasticsearch.client.RestHighLevelClient;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_SEQUENCE_COUNT_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_TRACE_STATE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_ARCHIVE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_PREFIX;

/**
 * Deletes all engine data and the import indexes from Elasticsearch such that Optimize reimports all data from the
 * engine, but keeps all reports, dashboards and alerts that were defined before.
 * <p>
 * External events are kept. Event process data is cleared as well to allow for manual republishing.
 */
@Slf4j
public class ReimportPreparation {

  private static final List<IndexMappingCreator> STATIC_INDICES_TO_DELETE = List.of(
    new ImportIndexIndex(),
    new TimestampBasedImportIndex(),
    new ProcessDefinitionIndex(),
    new EventProcessDefinitionIndex(),
    new DecisionDefinitionIndex(),
    new TenantIndex(),
    new BusinessKeyIndex(),
    new VariableUpdateInstanceIndex(),
    new EventProcessPublishStateIndex(),
    new ExternalProcessVariableIndex()
  );

  public static void main(String[] args) {
    log.info("Start to prepare Elasticsearch such that Optimize reimports engine data!");

    log.info("Reading configuration...");
    LoggingConfigurationReader loggingConfigurationReader = new LoggingConfigurationReader();
    loggingConfigurationReader.defineLogbackLoggingConfiguration();
    log.info("Successfully read configuration.");

    performReimport(ConfigurationServiceBuilder.createDefaultConfiguration());
  }

  public static void performReimport(final ConfigurationService configurationService) {
    log.info("Creating connection to Elasticsearch...");
    try (final RestHighLevelClient restHighLevelClient =
           ElasticsearchHighLevelRestClientBuilder.build(configurationService)) {
      log.info("Successfully created connection to Elasticsearch.");

      ElasticsearchCustomHeaderProvider customHeaderProvider = new ElasticsearchCustomHeaderProvider(
        configurationService, new PluginJarFileLoader(configurationService));
      customHeaderProvider.initPlugins();
      final OptimizeElasticsearchClient prefixAwareClient = new OptimizeElasticsearchClient(
        restHighLevelClient,
        new OptimizeIndexNameService(configurationService),
        new RequestOptionsProvider(customHeaderProvider.getPlugins(), configurationService)
      );

      deleteImportAndEngineDataIndices(prefixAwareClient);
      recreateStaticIndices(prefixAwareClient, configurationService);

      log.info(
        "Optimize was successfully prepared such it can reimport the engine data. Feel free to start Optimize again!"
      );
    } catch (Exception e) {
      log.error("Failed preparing Optimize for reimport.", e);
    }
  }

  private static void deleteImportAndEngineDataIndices(final OptimizeElasticsearchClient prefixAwareClient) {
    log.info("Deleting import and engine data indices from Elasticsearch...");
    STATIC_INDICES_TO_DELETE.forEach(prefixAwareClient::deleteIndex);
    log.info("Finished deleting import and engine data indices from Elasticsearch.");

    log.info("Deleting process instance indices from Elasticsearch...");
    deleteIndices(
      prefixAwareClient,
      getAllIndices(prefixAwareClient).stream()
        .filter(index -> index.contains(PROCESS_INSTANCE_INDEX_PREFIX) || index.contains(PROCESS_INSTANCE_ARCHIVE_INDEX_PREFIX))
        .toArray(String[]::new)
    );
    log.info("Finished deleting process instance indices from Elasticsearch.");

    log.info("Deleting event process indices and Camunda event data from Elasticsearch...");
    deleteIndices(
      prefixAwareClient,
      getAllIndices(prefixAwareClient).stream()
        .filter(index -> index.contains(EVENT_PROCESS_INSTANCE_INDEX_PREFIX)
          || index.contains(CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX)).toArray(String[]::new)
    );
    log.info("Finished deleting event process indices and Camunda event data from Elasticsearch.");

    log.info("Deleting decision instance indices from Elasticsearch...");
    deleteIndices(
      prefixAwareClient,
      getAllIndices(prefixAwareClient).stream()
        .filter(index -> index.contains(DECISION_INSTANCE_INDEX_PREFIX))
        .toArray(String[]::new)
    );
    log.info("Finished deleting decision instance indices from Elasticsearch.");

    log.info("Deleting Camunda event count/trace indices from Elasticsearch...");
    deleteIndices(
      prefixAwareClient,
      getAllIndices(prefixAwareClient).stream().filter(index -> !index.contains(EXTERNAL_EVENTS_INDEX_SUFFIX) &&
        (index.contains(EVENT_SEQUENCE_COUNT_INDEX_PREFIX) || index.contains(EVENT_TRACE_STATE_INDEX_PREFIX))
      ).toArray(String[]::new)
    );
    log.info("Finished Camunda event count/trace indices from Elasticsearch.");
  }

  @NotNull
  private static List<String> getAllIndices(final OptimizeElasticsearchClient prefixAwareClient) {
    List<String> indices;
    try {
      indices = prefixAwareClient.getAllIndexNames();
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Error while fetching indices. Could not perform Reimport", e);
    }
    return indices;
  }

  private static void deleteIndices(final OptimizeElasticsearchClient prefixAwareClient, String[] indexNames) {
    if (indexNames.length > 0) {
      prefixAwareClient.deleteIndexByRawIndexNames(indexNames);
    }
  }

  private static void recreateStaticIndices(final OptimizeElasticsearchClient prefixAwareClient,
                                            final ConfigurationService configurationService) {
    log.info("Recreating import indices and engine data...");

    final ObjectMapper objectMapper = new ObjectMapper();
    final ElasticSearchSchemaManager schemaManager = new ElasticSearchSchemaManager(
      new ElasticsearchMetadataService(objectMapper),
      configurationService,
      prefixAwareClient.getIndexNameService(),
      STATIC_INDICES_TO_DELETE
    );

    schemaManager.createOptimizeIndices(prefixAwareClient);

    log.info("Finished recreating import and engine data indices from Elasticsearch.");
  }

}
