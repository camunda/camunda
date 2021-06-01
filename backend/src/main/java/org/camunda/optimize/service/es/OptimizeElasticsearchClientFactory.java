/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.plugin.ElasticsearchCustomHeaderProvider;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.RequestOptionsProvider;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.client.RestHighLevelClient;

import java.io.IOException;

import static org.camunda.optimize.service.util.ESVersionChecker.checkESVersionSupport;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OptimizeElasticsearchClientFactory {

  public static OptimizeElasticsearchClient create(final ConfigurationService configurationService,
                                                   final OptimizeIndexNameService optimizeIndexNameService,
                                                   final ElasticSearchSchemaManager elasticSearchSchemaManager,
                                                   final ElasticsearchCustomHeaderProvider elasticsearchCustomHeaderProvider,
                                                   final BackoffCalculator backoffCalculator) throws IOException {
    log.info("Initializing Elasticsearch rest client...");
    final RestHighLevelClient esClient = ElasticsearchHighLevelRestClientBuilder.build(configurationService);

    final RequestOptionsProvider requestOptionsProvider =
      new RequestOptionsProvider(elasticsearchCustomHeaderProvider.getPlugins());
    waitForElasticsearch(esClient, backoffCalculator, requestOptionsProvider);
    log.info("Elasticsearch client has successfully been started");

    final OptimizeElasticsearchClient prefixedClient = new OptimizeElasticsearchClient(
      esClient, optimizeIndexNameService, requestOptionsProvider
    );

    elasticSearchSchemaManager.validateExistingSchemaVersion(prefixedClient);
    elasticSearchSchemaManager.initializeSchema(prefixedClient);
    return prefixedClient;
  }

  private static void waitForElasticsearch(final RestHighLevelClient esClient,
                                           final BackoffCalculator backoffCalculator,
                                           final RequestOptionsProvider requestOptionsProvider) throws IOException {
    boolean isConnected = false;
    while (!isConnected) {
      try {
        isConnected = getNumberOfClusterNodes(esClient, requestOptionsProvider) > 0;
        if (!isConnected) {
          long sleepTime = backoffCalculator.calculateSleepTime();
          log.info("No Elasticsearch nodes available, waiting [{}] ms to retry connecting", sleepTime);
          Thread.sleep(sleepTime);
        }
      } catch (final InterruptedException e) {
        log.warn("Got interrupted while waiting to retry connecting to Elasticsearch.", e);
        Thread.currentThread().interrupt();
      } catch (final Exception e) {
        String message = "Can't connect to Elasticsearch. Please check the connection!";
        log.error(message, e);
        throw new OptimizeRuntimeException(message, e);
      }
    }
    checkESVersionSupport(esClient);
  }

  private static int getNumberOfClusterNodes(final RestHighLevelClient esClient,
                                             final RequestOptionsProvider requestOptionsProvider) {
    try {
      return esClient.cluster()
        .health(new ClusterHealthRequest(), requestOptionsProvider.getRequestOptions())
        .getNumberOfNodes();
    } catch (IOException e) {
      log.error("Failed getting number of cluster nodes.", e);
      return 0;
    }
  }

}
