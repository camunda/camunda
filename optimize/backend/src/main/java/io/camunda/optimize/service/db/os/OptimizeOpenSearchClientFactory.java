/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os;

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

    log.info(
        "Creating OpenSearch connection with configuration {}",
        configurationService.getOpenSearchConfiguration());

    try {
      log.info("Building OpenSearch client from configuration...");
      final ExtendedOpenSearchClient openSearchClient =
          buildOpenSearchClientFromConfig(configurationService, pluginRepository);
      log.info("OpenSearch client created successfully");

      log.info("Building OpenSearch async client from configuration...");
      final OpenSearchAsyncClient openSearchAsyncClient =
          buildOpenSearchAsyncClientFromConfig(configurationService, pluginRepository);
      log.info("OpenSearch async client created successfully");

      log.info("Waiting for OpenSearch to become available...");
      waitForOpenSearch(openSearchClient, backoffCalculator);
      log.info("OpenSearch cluster successfully started");

      log.info("Creating OptimizeOpenSearchClient...");
      final OptimizeOpenSearchClient osClient =
          new OptimizeOpenSearchClient(
              openSearchClient, openSearchAsyncClient, optimizeIndexNameService);
      log.info("OptimizeOpenSearchClient created successfully");

      log.info("Validating database metadata...");
      openSearchSchemaManager.validateDatabaseMetadata(osClient);
      log.info("Database metadata validation completed");

      log.info("Initializing schema...");
      openSearchSchemaManager.initializeSchema(osClient);
      log.info("Schema initialization completed");

      return osClient;
    } catch (Exception e) {
      log.error("Failed to create OpenSearch client: {}", e.getMessage(), e);
      throw e;
    }
  }

  private static void waitForOpenSearch(
      final OpenSearchClient osClient, final BackoffCalculator backoffCalculator)
      throws IOException {
    boolean isConnected = false;
    int retryCount = 0;
    final int maxRetries = 60; // Maximum 60 retries to prevent infinite loops
    final long startTime = System.currentTimeMillis();

    log.info("Starting OpenSearch connection attempts (max {} retries)...", maxRetries);

    // First, try a basic network connectivity check
    try {
      log.info("Testing basic network connectivity to localhost:9200...");
      java.net.Socket socket = new java.net.Socket();
      socket.connect(new java.net.InetSocketAddress("localhost", 9200), 5000);
      socket.close();
      log.info("Basic network connectivity to localhost:9200 successful");
    } catch (Exception e) {
      log.error("Basic network connectivity to localhost:9200 failed: {}", e.getMessage());
      log.error("This indicates OpenSearch is not running or not accessible on port 9200");
    }

    // Try to identify what service is actually running on port 9200
    try {
      log.info("Attempting to identify the service running on localhost:9200...");
      java.net.URL url = new java.net.URL("http://localhost:9200/");
      java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(5000);

      int responseCode = connection.getResponseCode();
      String responseMessage = connection.getResponseMessage();
      log.info("Service on port 9200 responded with HTTP {} {}", responseCode, responseMessage);

      // Read response headers to identify the service
      java.util.Map<String, java.util.List<String>> headers = connection.getHeaderFields();
      for (java.util.Map.Entry<String, java.util.List<String>> header : headers.entrySet()) {
        if (header.getKey() != null && (
            header.getKey().toLowerCase().contains("server") ||
            header.getKey().toLowerCase().contains("x-elastic") ||
            header.getKey().toLowerCase().contains("x-opensearch"))) {
          log.info("Service header: {} = {}", header.getKey(), header.getValue());
        }
      }

      // Try to read the response body to identify Elasticsearch vs OpenSearch
      try (java.io.BufferedReader reader = new java.io.BufferedReader(
          new java.io.InputStreamReader(connection.getInputStream()))) {
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
          response.append(line);
        }
        String responseBody = response.toString();
        log.info("Service root response (first 500 chars): {}",
            responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody);

        // Check if it's Elasticsearch or OpenSearch
        if (responseBody.contains("\"cluster_name\"")) {
          if (responseBody.contains("\"distribution\"") && responseBody.contains("opensearch")) {
            log.info("Detected OpenSearch cluster");
          } else if (responseBody.contains("\"version\"") && responseBody.contains("elasticsearch")) {
            log.error("DETECTED ELASTICSEARCH CLUSTER - This is the problem!");
            log.error("Optimize is configured for OpenSearch but Elasticsearch is running on port 9200");
            log.error("Either:");
            log.error("1. Change Optimize configuration to use Elasticsearch instead of OpenSearch");
            log.error("2. Start OpenSearch containers instead of Elasticsearch containers");
          } else {
            log.warn("Detected unknown search engine cluster: {}", responseBody.substring(0, Math.min(200, responseBody.length())));
          }
        }
      }
    } catch (Exception e) {
      log.warn("Could not identify service on port 9200: {} - {}", e.getClass().getSimpleName(), e.getMessage());
    }

    // Try OpenSearch-specific endpoint to confirm compatibility
    try {
      log.info("Testing OpenSearch-specific endpoint /_cluster/health...");
      java.net.URL url = new java.net.URL("http://localhost:9200/_cluster/health");
      java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(5000);

      int responseCode = connection.getResponseCode();
      if (responseCode == 200) {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
            new java.io.InputStreamReader(connection.getInputStream()))) {
          StringBuilder response = new StringBuilder();
          String line;
          while ((line = reader.readLine()) != null) {
            response.append(line);
          }
          log.info("Cluster health response: {}", response.toString());
        }
      } else {
        log.warn("Cluster health endpoint returned HTTP {}", responseCode);
      }
    } catch (Exception e) {
      log.warn("OpenSearch cluster health check failed: {} - {}", e.getClass().getSimpleName(), e.getMessage());
    }

    while (!isConnected && retryCount < maxRetries) {
      try {
        log.debug("OpenSearch connection attempt {} of {}", retryCount + 1, maxRetries);

        // Add more detailed connection attempt logging
        try {
          log.debug("Attempting to get cluster health...");
          int nodeCount = getNumberOfClusterNodes(osClient);
          isConnected = nodeCount > 0;
          if (isConnected) {
            log.info("Successfully connected to OpenSearch cluster with {} nodes", nodeCount);
          } else {
            log.warn("Connected to OpenSearch but cluster reports 0 nodes");
          }
        } catch (java.net.ConnectException ce) {
          throw new IOException("Connection refused - OpenSearch is not running or not accessible on the configured port", ce);
        } catch (java.net.SocketTimeoutException ste) {
          throw new IOException("Connection timeout - OpenSearch may be starting up or overloaded", ste);
        } catch (org.opensearch.client.ResponseException re) {
          log.warn("OpenSearch responded with error: HTTP {} - {}", re.getResponse().getStatusLine().getStatusCode(), re.getMessage());
          throw re;
        }
      } catch (final Exception e) {
        retryCount++;
        long elapsedTime = System.currentTimeMillis() - startTime;

        // Provide more specific error messaging
        String errorType = e.getClass().getSimpleName();
        String errorMessage = e.getMessage();
        if (e.getCause() != null) {
          errorMessage += " (caused by: " + e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage() + ")";
        }

        log.warn(
            "OpenSearch connection attempt {} failed after {}ms. Error type: {}, Message: {}",
            retryCount, elapsedTime, errorType, errorMessage);

        if (retryCount >= maxRetries) {
          log.error("Failed to connect to OpenSearch after {} attempts and {}ms. Final error: {}",
              maxRetries, elapsedTime, errorMessage);

          // Log troubleshooting information
          log.error("Troubleshooting steps:");
          log.error("1. Check if OpenSearch is running: docker ps | grep opensearch");
          log.error("2. Check if port 9200 is accessible: curl -f http://localhost:9200/_cat/health");
          log.error("3. Check OpenSearch logs: docker logs <opensearch-container-name>");
          log.error("4. Verify the CAMUNDA_OPTIMIZE_OPENSEARCH_HTTP_PORT environment variable is set to 9200");

          throw new IOException("Failed to connect to OpenSearch after " + maxRetries + " attempts. " + errorMessage, e);
        }
      } finally {
        if (!isConnected && retryCount < maxRetries) {
          final long sleepTime = backoffCalculator.calculateSleepTime();
          log.info("No OpenSearch nodes available, waiting [{}] ms before retry {} of {}",
              sleepTime, retryCount + 1, maxRetries);
          try {
            Thread.sleep(sleepTime);
          } catch (final InterruptedException e) {
            log.warn("Got interrupted while waiting to retry connecting to OpenSearch.", e);
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting to connect to OpenSearch", e);
          }
        }
      }
    }

    if (!isConnected) {
      long totalTime = System.currentTimeMillis() - startTime;
      throw new IOException("Failed to connect to OpenSearch after " + retryCount +
          " attempts over " + totalTime + "ms");
    }
  }

  private static int getNumberOfClusterNodes(final OpenSearchClient openSearchClient)
      throws IOException {
    log.debug("Checking OpenSearch cluster health...");

    // Add retry logic for CI environments where connections might be unstable
    int maxRetries = 3;
    long baseDelayMs = 1000;

    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        int nodeCount = openSearchClient.cluster().health().numberOfNodes();
        log.debug("OpenSearch cluster has {} nodes", nodeCount);
        return nodeCount;
      } catch (Exception e) {
        log.warn("Cluster health check attempt {} failed: {} - {}",
                 attempt, e.getClass().getSimpleName(), e.getMessage());

        if (attempt == maxRetries) {
          // On final attempt, provide more detailed error info
          if (e.getMessage().contains("Connection reset") ||
              e.getMessage().contains("Connection refused") ||
              e.getMessage().contains("timeout")) {
            log.error("Connection issue detected in CI environment. This may be due to:");
            log.error("1. Network instability in CI containers");
            log.error("2. OpenSearch container not fully ready despite health checks");
            log.error("3. Connection pool exhaustion");
            log.error("Consider increasing connection timeouts or adding container dependencies");
          }
          throw e;
        }

        // Exponential backoff with jitter for CI environments
        long delay = baseDelayMs * (1L << (attempt - 1)) + (long)(Math.random() * 500);
        log.info("Retrying cluster health check in {}ms (attempt {} of {})", delay, attempt + 1, maxRetries);

        try {
          Thread.sleep(delay);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new IOException("Interrupted during cluster health check retry", ie);
        }
      }
    }

    // This should never be reached due to the throw in the catch block
    throw new IOException("Failed to get cluster node count after " + maxRetries + " attempts");
  }
}
