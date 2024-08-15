/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es;

import static io.camunda.optimize.service.util.DatabaseVersionChecker.checkESVersionSupport;
import static io.camunda.optimize.service.util.mapper.ObjectMapperFactory.OPTIMIZE_MAPPER;

import io.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import io.camunda.optimize.service.db.es.schema.RequestOptionsProvider;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import io.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import java.io.IOException;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;

@Conditional(ElasticSearchCondition.class)
public class OptimizeElasticsearchClientFactory {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(OptimizeElasticsearchClientFactory.class);

  private OptimizeElasticsearchClientFactory() {}

  public static OptimizeElasticsearchClient create(
      final ConfigurationService configurationService,
      final OptimizeIndexNameService optimizeIndexNameService,
      final ElasticSearchSchemaManager elasticSearchSchemaManager,
      final BackoffCalculator backoffCalculator)
      throws IOException {
    log.info("Initializing Elasticsearch rest client...");
    final RequestOptionsProvider requestOptionsProvider =
        new RequestOptionsProvider(configurationService);
    final RestHighLevelClient esClient =
        ElasticsearchHighLevelRestClientBuilder.build(configurationService);
    waitForElasticsearch(esClient, backoffCalculator, requestOptionsProvider.getRequestOptions());
    log.info("Elasticsearch client has successfully been started");

    final OptimizeElasticsearchClient prefixedClient =
        new OptimizeElasticsearchClient(
            esClient, optimizeIndexNameService, requestOptionsProvider, OPTIMIZE_MAPPER);

    elasticSearchSchemaManager.validateDatabaseMetadata(prefixedClient);
    elasticSearchSchemaManager.initializeSchema(prefixedClient);
    return prefixedClient;
  }

  private static void waitForElasticsearch(
      final RestHighLevelClient esClient,
      final BackoffCalculator backoffCalculator,
      final RequestOptions requestOptions)
      throws IOException {
    boolean isConnected = false;
    while (!isConnected) {
      try {
        isConnected = getNumberOfClusterNodes(esClient, requestOptions) > 0;
      } catch (final Exception e) {
        log.error(
            "Can't connect to any Elasticsearch node {}. Please check the connection!",
            esClient.getLowLevelClient().getNodes(),
            e);
      } finally {
        if (!isConnected) {
          final long sleepTime = backoffCalculator.calculateSleepTime();
          log.info(
              "No Elasticsearch nodes available, waiting [{}] ms to retry connecting", sleepTime);
          try {
            Thread.sleep(sleepTime);
          } catch (final InterruptedException e) {
            log.warn("Got interrupted while waiting to retry connecting to Elasticsearch.", e);
            Thread.currentThread().interrupt();
          }
        }
      }
    }
    checkESVersionSupport(esClient, requestOptions);
  }

  private static int getNumberOfClusterNodes(
      final RestHighLevelClient esClient, final RequestOptions requestOptions) throws IOException {
    return esClient.cluster().health(new ClusterHealthRequest(), requestOptions).getNumberOfNodes();
  }
}
