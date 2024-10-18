/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.search.es.clients;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.function.CheckedSupplier;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsRequest;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsResponse;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.*;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryElasticsearchClient {

  public static final String NUMBERS_OF_REPLICA = "index.number_of_replicas";
  public static final String NO_REPLICA = "0";
  public static final int SCROLL_KEEP_ALIVE_MS = 60_000;
  public static final int DEFAULT_NUMBER_OF_RETRIES =
      30 * 10; // 30*10 with 2 seconds = 10 minutes retry loop
  public static final int DEFAULT_DELAY_INTERVAL_IN_SECONDS = 2;
  private static final Logger LOGGER = LoggerFactory.getLogger(RetryElasticsearchClient.class);

  private final RestHighLevelClient esClient;

  private final ObjectMapper objectMapper;

  private RequestOptions requestOptions = RequestOptions.DEFAULT;
  private int numberOfRetries = DEFAULT_NUMBER_OF_RETRIES;
  private int delayIntervalInSeconds = DEFAULT_DELAY_INTERVAL_IN_SECONDS;
  private final int numberOfReplicas = 1;

  public RetryElasticsearchClient(
      final RestHighLevelClient esClient, final ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
  }

  public boolean isHealthy() {
    try {
      final ClusterHealthResponse response =
          esClient
              .cluster()
              .health(
                  new ClusterHealthRequest().timeout(TimeValue.timeValueMillis(500)),
                  RequestOptions.DEFAULT);
      final ClusterHealthStatus status = response.getStatus();
      return !response.isTimedOut() && !status.equals(ClusterHealthStatus.RED);
    } catch (final IOException e) {
      LOGGER.error(
          String.format(
              "Couldn't connect to Elasticsearch due to %s. Return unhealthy state.",
              e.getMessage()),
          e);
      return false;
    }
  }

  public int getNumberOfRetries() {
    return numberOfRetries;
  }

  public RetryElasticsearchClient setNumberOfRetries(final int numberOfRetries) {
    this.numberOfRetries = numberOfRetries;
    return this;
  }

  public int getDelayIntervalInSeconds() {
    return delayIntervalInSeconds;
  }

  public RetryElasticsearchClient setDelayIntervalInSeconds(final int delayIntervalInSeconds) {
    this.delayIntervalInSeconds = delayIntervalInSeconds;
    return this;
  }

  public RetryElasticsearchClient setRequestOptions(final RequestOptions requestOptions) {
    this.requestOptions = requestOptions;
    return this;
  }

  public boolean createOrUpdateDocument(final String name, final String id, final Map source) {
    return executeWithRetries(
        () -> {
          final IndexResponse response =
              esClient.index(
                  new IndexRequest(name).id(id).source(source, XContentType.JSON), requestOptions);
          final DocWriteResponse.Result result = response.getResult();
          return result.equals(DocWriteResponse.Result.CREATED)
              || result.equals(DocWriteResponse.Result.UPDATED);
        });
  }

  public boolean createOrUpdateDocument(final String name, final String id, final String source) {
    return executeWithRetries(
        () -> {
          final IndexResponse response =
              esClient.index(
                  new IndexRequest(name).id(id).source(source, XContentType.JSON), requestOptions);
          final DocWriteResponse.Result result = response.getResult();
          return result.equals(DocWriteResponse.Result.CREATED)
              || result.equals(DocWriteResponse.Result.UPDATED);
        });
  }

  public Map<String, Object> getDocument(final String name, final String id) {
    return executeWithGivenRetries(
        10,
        String.format("Get document from %s with id %s", name, id),
        () -> {
          final GetRequest request = new GetRequest(name).id(id);
          final GetResponse response = esClient.get(request, requestOptions);
          if (response.isExists()) {
            return response.getSourceAsMap();
          } else {
            return null;
          }
        },
        null);
  }

  public boolean deleteDocument(final String name, final String id) {
    return executeWithRetries(
        () -> {
          final DeleteResponse response =
              esClient.delete(new DeleteRequest(name).id(id), requestOptions);
          final DocWriteResponse.Result result = response.getResult();
          return result.equals(DocWriteResponse.Result.DELETED);
        });
  }

  private Set<String> getFilteredIndices(final String indexPattern) throws IOException {
    return Arrays.stream(
            esClient
                .indices()
                .get(new GetIndexRequest(indexPattern), RequestOptions.DEFAULT)
                .getIndices())
        .sequential()
        .collect(Collectors.toSet());
  }

  public boolean deleteIndicesFor(final String indexPattern) {
    return executeWithRetries(
        "DeleteIndices " + indexPattern,
        () -> {
          for (final var index : getFilteredIndices(indexPattern)) {
            esClient.indices().delete(new DeleteIndexRequest(index), RequestOptions.DEFAULT);
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
                  if (e.status().equals(RestStatus.NOT_FOUND)) {
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
      final SearchRequest searchRequest, final Consumer<SearchHit> searchHitConsumer) {
    return executeWithRetries(
        "RetryElasticsearchClient#doWithEachSearchResult",
        () -> {
          int doneOnSearchHits = 0;
          searchRequest.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));
          SearchResponse response = esClient.search(searchRequest, requestOptions);

          String scrollId = null;
          while (response.getHits().getHits().length > 0) {
            Arrays.stream(response.getHits().getHits()).sequential().forEach(searchHitConsumer);
            doneOnSearchHits += response.getHits().getHits().length;

            scrollId = response.getScrollId();
            final SearchScrollRequest scrollRequest =
                new SearchScrollRequest(scrollId)
                    .scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));
            response = esClient.scroll(scrollRequest, requestOptions);
          }
          if (scrollId != null) {
            final ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
            clearScrollRequest.addScrollId(scrollId);
            esClient.clearScroll(clearScrollRequest, requestOptions);
          }
          return doneOnSearchHits;
        });
  }

  public boolean createIndex(final CreateIndexRequest createIndexRequest) {
    return executeWithRetries(
        "CreateIndex " + createIndexRequest.index(),
        () -> {
          if (!indicesExist(createIndexRequest.index())) {
            return esClient.indices().create(createIndexRequest, requestOptions).isAcknowledged();
          }

          final String replicas =
              getOrDefaultNumbersOfReplica(createIndexRequest.index(), NO_REPLICA);
          if (!replicas.equals(String.valueOf(numberOfReplicas))) {
            final UpdateSettingsRequest updateSettingsRequest =
                new UpdateSettingsRequest(createIndexRequest.index());
            final Settings settings =
                Settings.builder().put(NUMBERS_OF_REPLICA, numberOfReplicas).build();
            updateSettingsRequest.settings(settings);
            esClient.indices().putSettings(updateSettingsRequest, requestOptions).isAcknowledged();
          }

          try {
            if (createIndexRequest.aliases() != null
                && !createIndexRequest.aliases().isEmpty()
                && !aliasExist(
                    createIndexRequest.aliases().iterator().next(), createIndexRequest.index())) {
              final IndicesAliasesRequest request = new IndicesAliasesRequest();
              final IndicesAliasesRequest.AliasActions aliasAction =
                  new IndicesAliasesRequest.AliasActions(
                          IndicesAliasesRequest.AliasActions.Type.ADD)
                      .index(createIndexRequest.index())
                      .alias(createIndexRequest.aliases().iterator().next().name())
                      .writeIndex(false);
              request.addAliasAction(aliasAction);

              esClient.indices().updateAliases(request, RequestOptions.DEFAULT);
              LOGGER.info(
                  "Alias is created. Index: {}, alias: {} ",
                  createIndexRequest.index(),
                  createIndexRequest.aliases().iterator().next().name());

              return true;
            }
          } catch (final Exception ex) {
            LOGGER.error(
                String.format(
                    "Exception occurred when creating an alias. Index: %s, alias: %s, error: %s ",
                    createIndexRequest.index(),
                    createIndexRequest.aliases().iterator().next().name(),
                    ex.getMessage()),
                ex);
          }
          return true;
        });
  }

  private boolean indicesExist(final String indexPattern) throws IOException {
    return esClient
        .indices()
        .exists(
            new GetIndexRequest(indexPattern)
                .indicesOptions(IndicesOptions.fromOptions(true, false, true, false)),
            requestOptions);
  }

  public String getOrDefaultNumbersOfReplica(final String indexName, final String defaultValue) {
    final Map<String, String> settings = getIndexSettingsFor(indexName, NUMBERS_OF_REPLICA);
    String numbersOfReplica = getOrDefaultForNullValue(settings, NUMBERS_OF_REPLICA, defaultValue);
    if (numbersOfReplica.trim().equals(NO_REPLICA)) {
      numbersOfReplica = defaultValue;
    }
    return numbersOfReplica;
  }

  protected Map<String, String> getIndexSettingsFor(
      final String indexName, final String... fields) {
    return executeWithRetries(
        "GetIndexSettings " + indexName,
        () -> {
          final Map<String, String> settings = new HashMap<>();
          final GetSettingsResponse response =
              esClient
                  .indices()
                  .getSettings(new GetSettingsRequest().indices(indexName), requestOptions);
          for (final String field : fields) {
            settings.put(field, response.getSetting(indexName, field));
          }
          return settings;
        });
  }

  public static <K, V> V getOrDefaultForNullValue(
      final Map<K, V> map, final K key, final V defaultValue) {
    final V value = map.get(key);
    return value == null ? defaultValue : value;
  }

  private boolean aliasExist(final Alias alias, final String index) throws IOException {
    final GetAliasesRequest aliasExistsReq = new GetAliasesRequest(alias.name()).indices(index);
    return esClient.indices().existsAlias(aliasExistsReq, RequestOptions.DEFAULT);
  }
}
