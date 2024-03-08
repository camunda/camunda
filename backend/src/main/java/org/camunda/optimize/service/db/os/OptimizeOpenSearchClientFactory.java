/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os;

import static org.camunda.optimize.service.util.DatabaseVersionChecker.checkOSVersionSupport;
import static org.camunda.optimize.upgrade.os.OpenSearchClientBuilder.buildOpenSearchAsyncClientFromConfig;
import static org.camunda.optimize.upgrade.os.OpenSearchClientBuilder.buildOpenSearchClientFromConfig;

import java.io.IOException;
import java.util.Collections;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.es.schema.RequestOptionsProvider;
import org.camunda.optimize.service.db.os.schema.OpenSearchSchemaManager;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.elasticsearch.client.RequestOptions;
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
      final BackoffCalculator backoffCalculator)
      throws IOException {

    log.info("Creating OpenSearch connection...");
    // TODO Evaluate the need for OpenSearchCustomHeaderProvider with OPT-7400
    final RequestOptionsProvider requestOptionsProvider =
        new RequestOptionsProvider(Collections.emptyList(), configurationService);
    final ExtendedOpenSearchClient openSearchClient =
        buildOpenSearchClientFromConfig(configurationService);
    final OpenSearchAsyncClient openSearchAsyncClient =
        buildOpenSearchAsyncClientFromConfig(configurationService);
    waitForOpenSearch(
        openSearchClient, backoffCalculator, requestOptionsProvider.getRequestOptions());
    log.info("OpenSearch cluster successfully started");

    OptimizeOpenSearchClient osClient =
        new OptimizeOpenSearchClient(
            openSearchClient,
            openSearchAsyncClient,
            optimizeIndexNameService,
            requestOptionsProvider);
    openSearchSchemaManager.validateDatabaseMetadata(osClient);
    openSearchSchemaManager.initializeSchema(osClient);

    return osClient;
  }

  private static void waitForOpenSearch(
      final OpenSearchClient osClient,
      final BackoffCalculator backoffCalculator,
      final RequestOptions requestOptions)
      throws IOException {
    boolean isConnected = false;
    while (!isConnected) {
      try {
        isConnected = getNumberOfClusterNodes(osClient) > 0;
      } catch (final Exception e) {
        log.error(
            "Can't connect to any OpenSearch node {}. Please check the connection!",
            osClient.nodes(),
            e);
      } finally {
        if (!isConnected) {
          long sleepTime = backoffCalculator.calculateSleepTime();
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
    checkOSVersionSupport(osClient, requestOptions);
  }

  private static int getNumberOfClusterNodes(final OpenSearchClient openSearchClient)
      throws IOException {
    return openSearchClient.cluster().health().numberOfNodes();
  }
}
