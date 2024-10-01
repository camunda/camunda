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

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.TransportOptions;
import io.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import io.camunda.optimize.service.db.es.schema.TransportOptionsProvider;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import io.camunda.optimize.upgrade.es.ElasticsearchClientBuilder;
import io.camunda.search.connect.plugin.PluginRepository;
import java.io.IOException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Conditional(ElasticSearchCondition.class)
public class OptimizeElasticsearchClientFactory {

  public static OptimizeElasticsearchClient create(
      final ConfigurationService configurationService,
      final OptimizeIndexNameService optimizeIndexNameService,
      final ElasticSearchSchemaManager elasticSearchSchemaManager,
      final BackoffCalculator backoffCalculator,
      final PluginRepository pluginRepository)
      throws IOException {
    log.info("Initializing Elasticsearch rest client...");
    final TransportOptionsProvider transportOptionsProvider =
        new TransportOptionsProvider(configurationService);
    final ElasticsearchClient build =
        ElasticsearchClientBuilder.build(configurationService, OPTIMIZE_MAPPER, pluginRepository);

    waitForElasticsearch(build, backoffCalculator, transportOptionsProvider.getTransportOptions());
    log.info("Elasticsearch client has successfully been started");

    final OptimizeElasticsearchClient prefixedClient =
        new OptimizeElasticsearchClient(
            ElasticsearchClientBuilder.restClient(configurationService, pluginRepository),
            OPTIMIZE_MAPPER,
            build,
            optimizeIndexNameService,
            transportOptionsProvider);

    elasticSearchSchemaManager.validateDatabaseMetadata(prefixedClient);
    elasticSearchSchemaManager.initializeSchema(prefixedClient);
    return prefixedClient;
  }

  private static void waitForElasticsearch(
      final ElasticsearchClient esClient,
      final BackoffCalculator backoffCalculator,
      final TransportOptions requestOptions)
      throws IOException {
    boolean isConnected = false;
    int connectionAttempts = 0;
    while (!isConnected) {
      connectionAttempts++;
      try {
        isConnected = getNumberOfClusterNodes(esClient, requestOptions) > 0;
      } catch (final Exception e) {
        final String errorMessage =
            "Can't connect to any Elasticsearch node. Please check the connection!";
        if (connectionAttempts < 10) {
          log.warn(errorMessage);
        } else {
          log.error(errorMessage, e);
        }
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
      final ElasticsearchClient esClient, final TransportOptions requestOptions)
      throws IOException {
    return esClient.withTransportOptions(requestOptions).cluster().health(c -> c).numberOfNodes();
  }
}
