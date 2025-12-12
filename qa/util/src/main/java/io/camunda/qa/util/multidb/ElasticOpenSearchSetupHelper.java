/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ElasticOpenSearchSetupHelper implements MultiDbSetupHelper {

  protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  protected final Collection<IndexDescriptor> expectedDescriptors;
  protected final String endpoint;
  protected final HttpClient httpClient = HttpClient.newHttpClient();
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final AtomicBoolean hasClusterSettingsChanged = new AtomicBoolean(false);

  public ElasticOpenSearchSetupHelper(
      final String endpoint, final Collection<IndexDescriptor> expectedDescriptors) {
    this.endpoint = endpoint;
    this.expectedDescriptors = expectedDescriptors;
  }

  @Override
  public void close() {
    httpClient.close();
  }

  private boolean sendHttpDeleteRequest(final HttpClient httpClient, final URI deleteEndpoint)
      throws IOException, InterruptedException {
    final var httpRequest = HttpRequest.newBuilder().DELETE().uri(deleteEndpoint).build();
    final var response = httpClient.send(httpRequest, BodyHandlers.ofString());
    final var statusCode = response.statusCode();
    if (statusCode / 100 == 2) {
      logger.info("Deletion on {} was successful", deleteEndpoint.toString());
      return true;
    } else {
      logger.warn(
          "Failure on deletion at {}. Status code: {} [{}]",
          deleteEndpoint.toString(),
          statusCode,
          response.body());
    }
    return false;
  }

  @Override
  public boolean validateConnection() {
    final HttpRequest httpRequest =
        HttpRequest.newBuilder().GET().uri(URI.create(String.format("%s/", endpoint))).build();
    try (final HttpClient httpClient = HttpClient.newHttpClient()) {
      final HttpResponse<String> response = httpClient.send(httpRequest, BodyHandlers.ofString());
      final int statusCode = response.statusCode();
      return statusCode / 100 == 2;
    } catch (final IOException | InterruptedException e) {
      logger.debug("Exception on validating exception", e);
    }
    return false;
  }

  @Override
  public void applyIndexPoliciesPollInterval(final Duration pollInterval) {
    applyClusterSettings(getLifecyclePollIntervalSettings(pollInterval));
    hasClusterSettingsChanged.set(true); // mark cluster settings changed to reset after the test
  }

  /**
   * Validate the schema creation. Expects harmonized indices to be created. Optimize indices and ES
   * Exporter are not included.
   *
   * <p>ES exporter indices are only created, on first exporting, so we expect at least this amount
   * or more (to fight race conditions).
   *
   * @param testPrefix the test prefix of the actual indices
   */
  @Override
  public boolean validateSchemaCreation(final String testPrefix) {
    try {
      final int count = getCountOfIndicesWithPrefix(endpoint, testPrefix);
      if (expectedDescriptors.size() > count) {
        logger.debug(
            "[{}/{}] indices with prefix {} in secondary storage, retry...",
            count,
            expectedDescriptors.size(),
            testPrefix);
        return false;
      }

      final int templateCount = getCountOfIndexTemplatesWithPrefix(endpoint, testPrefix);
      if (templateCount <= 0) {
        logger.debug("{} templates found for prefix {}, retry...", templateCount, testPrefix);
        return false;
      }

      logger.debug(
          "Found {} indices and {} index templates. Schema creation validated.",
          count,
          templateCount);
      return true;
    } catch (final IOException | InterruptedException e) {
      logger.debug("Exception on retrieving schema with prefix {}", testPrefix, e);
    }
    return false;
  }

  @Override
  public void cleanup(final String prefix) {
    try (final HttpClient httpClient = HttpClient.newHttpClient()) {
      // reset cluster settings if changed
      resetLifecyclePollInterval();

      // delete indices
      // https://www.elastic.co/guide/en/elasticsearch/reference/current/indices-delete-index.html
      withRetry(
          () -> {
            final URI deleteIndicesEndpoint = URI.create(String.format("%s/%s*", endpoint, prefix));
            return sendHttpDeleteRequest(httpClient, deleteIndicesEndpoint);
          },
          5);

      // Deleting index templates are separate from deleting indices, and we need to make sure
      // that we also get rid of the template, so we can properly recreate them
      // https://www.elastic.co/guide/en/elasticsearch/reference/current/indices-delete-template.html
      //
      // See related CI incident https://github.com/camunda/camunda/pull/27985
      withRetry(
          () -> {
            final URI deleteIndexTemplatesEndpoint =
                URI.create(String.format("%s/_index_template/%s*", endpoint, prefix));
            return sendHttpDeleteRequest(httpClient, deleteIndexTemplatesEndpoint);
          },
          5);
    }
  }

  protected void resetLifecyclePollInterval() {
    if (hasClusterSettingsChanged.getAndSet(false)) {
      final Map<String, Object> settings = getResetLifecyclePollIntervalSettings();
      applyClusterSettings(settings);
    }
  }

  protected abstract Map<String, Object> getLifecyclePollIntervalSettings(
      final Duration pollInterval);

  protected abstract Map<String, Object> getResetLifecyclePollIntervalSettings();

  protected void applyClusterSettings(final Map<String, Object> settings) {
    withRetry(
        () -> {
          final HttpRequest httpRequest =
              HttpRequest.newBuilder()
                  .PUT(BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(settings)))
                  .uri(URI.create(String.format("%s/_cluster/settings", endpoint)))
                  .header("Content-Type", "application/json")
                  .build();

          final HttpResponse<String> response =
              httpClient.send(httpRequest, BodyHandlers.ofString());
          if (response.statusCode() / 100 == 2) {
            logger.info("Applied cluster settings successfully");
            return true;
          } else {
            logger.warn(
                "Failed to apply cluster settings. Status code: {} [{}], retrying...",
                response.statusCode(),
                response.body());
            return false;
          }
        },
        5);
  }

  protected int getCountOfIndicesWithPrefix(final String url, final String testPrefix)
      throws IOException, InterruptedException {
    final HttpRequest httpRequest =
        HttpRequest.newBuilder()
            .GET()
            .uri(URI.create(String.format("%s/%s*", url, testPrefix)))
            .build();
    final HttpResponse<String> response = httpClient.send(httpRequest, BodyHandlers.ofString());
    final int statusCode = response.statusCode();
    assertThat(statusCode).isBetween(200, 299);

    // Get how many indices with given prefix we have
    final JsonNode jsonNode = OBJECT_MAPPER.readTree(response.body());
    return jsonNode.size();
  }

  protected int getCountOfIndexTemplatesWithPrefix(final String url, final String testPrefix)
      throws IOException, InterruptedException {
    final HttpRequest httpRequest =
        HttpRequest.newBuilder()
            .GET()
            .uri(URI.create(String.format("%s/_index_template/%s*", url, testPrefix)))
            .build();
    final HttpResponse<String> response = httpClient.send(httpRequest, BodyHandlers.ofString());
    final int statusCode = response.statusCode();
    if (statusCode == 404) {
      return 0;
    }

    assertThat(statusCode).isBetween(200, 299);

    // Get how many indices with given prefix we have
    final JsonNode jsonNode = OBJECT_MAPPER.readTree(response.body());
    return jsonNode.get("index_templates").size();
  }

  /**
   * Run given callable with retry. Callable should return true, if operation succeeded, to stop
   * retrying.
   *
   * @param operation operation to be executed with retry, returning true will indicate success and
   *     stop retrying
   * @param maxAttempt the maximum attempts to retry given operation
   */
  protected void withRetry(final Callable<Boolean> operation, final int maxAttempt) {
    int attempt = 0;
    boolean shouldRetry = true;
    while (shouldRetry) {
      try {
        // if we succeed we don't want to retry
        shouldRetry = !operation.call();
      } catch (final Exception ex) {
        logger.debug(
            "Failed to execute {}. Attempts: [{}/{}]", operation, attempt + 1, maxAttempt, ex);
      } finally {
        // if we reached the max attempt we stop
        if (++attempt >= maxAttempt) {
          shouldRetry = false;
        }
      }

      if (shouldRetry) {
        try {
          // wait a little between retries
          Thread.sleep(100);
        } catch (final InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }
}
