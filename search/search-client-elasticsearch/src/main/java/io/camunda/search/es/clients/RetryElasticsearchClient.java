/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.search.es.clients;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch.core.ClearScrollRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.ScrollRequest;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.IndexState;
import co.elastic.clients.elasticsearch.indices.put_index_template.IndexTemplateMapping;
import co.elastic.clients.json.JsonpDeserializer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.function.CheckedSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryElasticsearchClient {

  public static final int SCROLL_KEEP_ALIVE_MS = 60_000;
  public static final int DEFAULT_NUMBER_OF_RETRIES =
      30 * 10; // 30*10 with 2 seconds = 10 minutes retry loop
  public static final int DEFAULT_DELAY_INTERVAL_IN_SECONDS = 2;
  private static final Logger LOGGER = LoggerFactory.getLogger(RetryElasticsearchClient.class);

  private final ElasticsearchClient client;

  private final ObjectMapper objectMapper;

  private final int numberOfRetries = DEFAULT_NUMBER_OF_RETRIES;
  private final int delayIntervalInSeconds = DEFAULT_DELAY_INTERVAL_IN_SECONDS;

  public RetryElasticsearchClient(
      final ElasticsearchClient client, final ObjectMapper objectMapper) {
    this.client = client;
    this.objectMapper = objectMapper;
  }

  public <T> T deserializeJson(final JsonpDeserializer<T> deserializer, final InputStream json) {
    final var mapper = client._jsonpMapper();
    try (final var parser = mapper.jsonProvider().createParser(json)) {
      return deserializer.deserialize(parser, client._jsonpMapper());
    }
  }

  public InputStream appendToFileSchemaSettings(
      final InputStream file, final int numberOfShards, final int numberOfReplicas)
      throws IOException {

    final var map = objectMapper.readValue(file, new TypeReference<Map<String, Object>>() {});

    final var settingsBlock =
        (Map<String, Object>) map.computeIfAbsent("settings", k -> new HashMap<>());
    final var indexBlock =
        (Map<String, Object>) settingsBlock.computeIfAbsent("index", k -> new HashMap<>());

    indexBlock.put("number_of_shards", numberOfShards);
    indexBlock.put("number_of_replicas", numberOfReplicas);

    return new ByteArrayInputStream(objectMapper.writeValueAsBytes(map));
  }

  public boolean createOrUpdateDocument(final String indexName, final String id, final Map source) {
    return executeWithRetries(
        () -> {
          client.index(i -> i.index(indexName).id(id).document(source));
          return true;
        });
  }

  public boolean createOrUpdateDocument(
      final String indexName, final String id, final String source) {
    return executeWithRetries(
        () -> {
          client.index(i -> i.index(indexName).id(id).document(source));
          return true;
        });
  }

  public Map<String, Object> getDocument(final String name, final String id) {
    return executeWithGivenRetries(
        10,
        String.format("Get document from %s with id %s", name, id),
        () -> {
          final GetResponse<Map> response = client.get(s -> s.index(name).id(id), Map.class);
          return (Map<String, Object>) response.source();
        },
        null);
  }

  public boolean deleteDocument(final String name, final String id) {
    return executeWithRetries(
        () -> {
          client.delete(i -> i.index(name).id(id));
          return true;
        });
  }

  private Set<String> getFilteredIndices(final String indexPattern) throws IOException {
    return client.indices().get(i -> i.index(indexPattern)).result().values().stream()
        .map(IndexState::toString)
        .collect(Collectors.toSet());
  }

  public boolean deleteIndicesFor(final String indexPattern) {
    return executeWithRetries(
        "DeleteIndices " + indexPattern,
        () -> {
          for (final var index : getFilteredIndices(indexPattern)) {
            client.indices().delete(i -> i.index(index));
          }
          return true;
        });
  }

  // ------------------- Retry part ------------------
  private <T> T executeWithRetries(final CheckedSupplier<T> supplier) {
    return executeWithRetries("", supplier, null);
  }

  private <T> T executeWithRetries(final String operationName, final CheckedSupplier<T> supplier) {
    return executeWithRetries(operationName, supplier, null);
  }

  private <T> T executeWithRetries(
      final String operationName,
      final CheckedSupplier<T> supplier,
      final Predicate<T> retryPredicate) {
    return executeWithGivenRetries(numberOfRetries, operationName, supplier, retryPredicate);
  }

  private <T> T executeWithGivenRetries(
      final int retries,
      final String operationName,
      final CheckedSupplier<T> operation,
      final Predicate<T> predicate) {
    try {
      final RetryPolicy<T> retryPolicy =
          new RetryPolicy<T>()
              .handle(IOException.class, ElasticsearchException.class)
              .withDelay(Duration.ofSeconds(delayIntervalInSeconds))
              .withMaxAttempts(retries)
              .onRetry(
                  e ->
                      LOGGER.info(
                          "Retrying #{} {} due to {}",
                          e.getAttemptCount(),
                          operationName,
                          e.getLastFailure()))
              .onAbort(e -> LOGGER.error("Abort {} by {}", operationName, e.getFailure()))
              .onRetriesExceeded(
                  e ->
                      LOGGER.error(
                          "Retries {} exceeded for {}", e.getAttemptCount(), operationName));
      if (predicate != null) {
        retryPolicy.handleResultIf(predicate);
      }
      return Failsafe.with(retryPolicy)
          .get(
              () -> {
                try {
                  return operation.get();
                } catch (final ElasticsearchException e) {
                  if (e.status() == 404) {
                    return null;
                  }
                  throw e;
                }
              });
    } catch (final Exception e) {
      throw new RuntimeException(
          "Couldn't execute operation "
              + operationName
              + " on elasticsearch for "
              + numberOfRetries
              + " attempts with "
              + delayIntervalInSeconds
              + " seconds waiting.",
          e);
    }
  }

  public int doWithEachSearchResult(
      final SearchRequest searchRequest, final Consumer<Hit> searchHitConsumer) {
    return executeWithRetries(
        "RetryElasticsearchClient#doWithEachSearchResult",
        () -> {
          final SearchResponse<Map> searchResponse = client.search(searchRequest, Map.class);

          final List<Hit<Map>> hits = searchResponse.hits().hits();

          int doneOnSearchHits = 0;
          searchRequest.scroll();
          final SearchResponse<Map> response = client.search(searchRequest, Map.class);
          hits.forEach(searchHitConsumer);
          doneOnSearchHits += response.hits().hits().size();

          String scrollId = null;
          ScrollResponse<Map> scrollResponse = null;
          do {
            scrollId = scrollResponse != null ? scrollResponse.scrollId() : response.scrollId();
            if (scrollId != null) {
              scrollResponse =
                  client.scroll(
                      new ScrollRequest.Builder()
                          .scrollId(scrollId)
                          .scroll(
                              Time.of(
                                  builder ->
                                      builder.time(
                                          Instant.ofEpochMilli(SCROLL_KEEP_ALIVE_MS).toString())))
                          .build(),
                      Map.class);
              searchResponse.hits().hits().forEach(searchHitConsumer);
              doneOnSearchHits += scrollResponse.hits().hits().size();
            }
          } while (scrollResponse != null && !scrollResponse.hits().hits().isEmpty());
          if (scrollId != null) {
            final ClearScrollRequest clearScrollRequest =
                new ClearScrollRequest.Builder().scrollId(scrollId).build();
            client.clearScroll(clearScrollRequest);
          }
          return doneOnSearchHits;
        });
  }

  public boolean createIndex(
      final String indexName,
      final String alias,
      final int shards,
      final int replicas,
      final String mappingFile)
      throws IOException {
    if (!indicesExist(indexName)) {
      final var templateFile = getClass().getResourceAsStream(mappingFile);
      final var templateFields =
          deserializeJson(
              IndexTemplateMapping._DESERIALIZER,
              appendToFileSchemaSettings(templateFile, shards, replicas));

      final CreateIndexRequest request =
          new CreateIndexRequest.Builder()
              .index(indexName)
              .aliases(alias, a -> a.isWriteIndex(false))
              .mappings(templateFields.mappings())
              .settings(templateFields.settings())
              .build();

      return client.indices().create(request).acknowledged();
    }
    return true;
  }

  private boolean indicesExist(final String indexPattern) throws IOException {
    return client.indices().exists(s -> s.index(indexPattern)).value();
  }
}
