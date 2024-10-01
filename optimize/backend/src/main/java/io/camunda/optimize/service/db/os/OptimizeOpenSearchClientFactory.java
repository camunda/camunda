/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os;

import static io.camunda.optimize.service.util.DatabaseVersionChecker.checkOSVersionSupport;
import static io.camunda.optimize.upgrade.os.OpenSearchClientBuilder.buildOpenSearchAsyncClientFromConfig;
import static io.camunda.optimize.upgrade.os.OpenSearchClientBuilder.buildOpenSearchClientFromConfig;

import io.camunda.optimize.service.db.os.schema.OpenSearchSchemaManager;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import io.camunda.search.connect.plugin.PluginRepository;
import java.io.IOException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.context.annotation.Conditional;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Conditional(OpenSearchCondition.class)
@Slf4j
public class OptimizeOpenSearchClientFactory {

  public static OptimizeOpenSearchClient create(
      final ConfigurationService configurationService,
      final OptimizeIndexNameService optimizeIndexNameService,
      final OpenSearchSchemaManager openSearchSchemaManager,
      final BackoffCalculator backoffCalculator,
      final PluginRepository pluginRepository)
      throws IOException {

    log.info("Creating OpenSearch connection...");
    final ExtendedOpenSearchClient openSearchClient =
        buildOpenSearchClientFromConfig(configurationService, pluginRepository);
    final OpenSearchAsyncClient openSearchAsyncClient =
        buildOpenSearchAsyncClientFromConfig(configurationService, pluginRepository);
    waitForOpenSearch(openSearchClient, backoffCalculator);
    log.info("OpenSearch cluster successfully started");

    final OptimizeOpenSearchClient osClient =
        new OptimizeOpenSearchClient(
            openSearchClient, openSearchAsyncClient, optimizeIndexNameService);
    openSearchSchemaManager.validateDatabaseMetadata(osClient);
    openSearchSchemaManager.initializeSchema(osClient);

    return osClient;
  }

  private static void waitForOpenSearch(
      final OpenSearchClient osClient, final BackoffCalculator backoffCalculator)
      throws IOException {
    boolean isConnected = false;
    int connectionAttempts = 0;
    while (!isConnected) {
      connectionAttempts++;
      try {
        isConnected = getNumberOfClusterNodes(osClient) > 0;
      } catch (final Exception e) {
        final String errorMessage =
            "Can't connect to any OpenSearch node {}. Please check the connection!";
        if (connectionAttempts < 10) {
          log.warn(errorMessage, osClient.nodes());
        } else {
          log.error(errorMessage, osClient.nodes(), e);
        }
      } finally {
        if (!isConnected) {
          final long sleepTime = backoffCalculator.calculateSleepTime();
          log.info("No OpenSearch nodes available, waiting [{}] ms to retry connecting", sleepTime);
          try {
            Thread.sleep(sleepTime);
          } catch (final InterruptedException e) {
            log.warn("Got interrupted while waiting to retry connecting to OpenSearch.", e);
            Thread.currentThread().interrupt();
          }
        }
      }
    }
    checkOSVersionSupport(osClient);
  }

  private static int getNumberOfClusterNodes(final OpenSearchClient openSearchClient)
      throws IOException {
    return openSearchClient.cluster().health().numberOfNodes();
  }
}
