package org.camunda.optimize.reimport.preparation;

import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.camunda.optimize.jetty.util.LoggingConfigurationReader;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchRestClientBuilder;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.type.index.TimestampBasedImportIndexType.TIMESTAMP_BASED_IMPORT_INDEX_TYPE;

/**
 * Deletes all engine data and the import indexes from Elasticsearch
 * such that Optimize reimports all data from the engine, but keeps
 * all reports, dashboards and alerts that were defined before.
 */
public class ReimportPreparation {

  private static Logger logger = LoggerFactory.getLogger(ReimportPreparation.class);

  public static void main(String[] args) throws IOException {
    logger.info("Start to prepare Elasticsearch such that Optimize reimports engine data!");
    logger.info("Reading configuration...");
    LoggingConfigurationReader loggingConfigurationReader = new LoggingConfigurationReader();
    loggingConfigurationReader.defineLogbackLoggingConfiguration();
    ConfigurationService configurationService = new ConfigurationService();
    logger.info("Successfully read configuration.");
    logger.info("Creating connection to Elasticsearch...");
    try (RestClient restClient = ElasticsearchRestClientBuilder.build(configurationService)) {
      logger.info("Successfully created connection to Elasticsearch.");
      prepareElasticsearchSuchThatOptimizeReimportsDataFromEngine(configurationService, restClient);
      logger.info("Optimize was successfully prepared such it can reimport the engine data. " +
                    "Feel free to start Optimize again!");
    }
  }

  private static void prepareElasticsearchSuchThatOptimizeReimportsDataFromEngine(ConfigurationService configurationService,
                                                                                  RestClient restClient) throws IOException {

    logger.info("Deleting import indexes and engine data from Optimize...");
    List<String> types = new ArrayList<>();
    types.add(TIMESTAMP_BASED_IMPORT_INDEX_TYPE);
    types.add(configurationService.getImportIndexType());
    types.add(configurationService.getProcessDefinitionType());
    types.add(configurationService.getProcessInstanceType());

    List<String> indexNames = types
      .stream()
      .map(configurationService::getOptimizeIndex)
      .collect(Collectors.toList());

    String commaSeparatedTypes = String.join(",", types);
    String commaSeparatedIndexes = String.join(",", indexNames);

    String matchAll =
      "{" +
        "  \"query\": {" +
        "    \"match_all\": {}" +
        "  }" +
      "}";

    HttpEntity entity = new NStringEntity(matchAll, ContentType.APPLICATION_JSON);
    Response response = restClient.performRequest(
      "POST",
      commaSeparatedIndexes + "/" + commaSeparatedTypes + "/_delete_by_query",
      getParamsWithRefresh(),
      entity
    );

    int statusCode = response.getStatusLine().getStatusCode();
    if (statusCode != 200) {
      throw new OptimizeRuntimeException("Could not prepare Elasticsearch such that " +
                                           "Optimize reimports the data from the engine " +
                                           "Wrong status code was returned!");
    }
    logger.info("Successfully deleted import indexes and engine data from Elasticsearch.");
  }

  private static Map<String, String> getParamsWithRefresh() {
    HashMap<String, String> reindexParams = new HashMap<>();
    reindexParams.put("refresh", "true");
    return reindexParams;
  }
}
