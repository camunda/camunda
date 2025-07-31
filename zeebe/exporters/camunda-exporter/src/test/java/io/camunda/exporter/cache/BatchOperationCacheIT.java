/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.cache;

import static io.camunda.search.test.utils.SearchDBExtension.BATCH_OPERATION_INDEX;
import static io.camunda.search.test.utils.SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;

import io.camunda.exporter.DefaultExporterResourceProvider;
import io.camunda.exporter.cache.batchoperation.ElasticSearchBatchOperationCacheLoader;
import io.camunda.exporter.cache.batchoperation.OpenSearchBatchOperationCacheLoader;
import io.camunda.search.entities.BatchOperationType;
import io.camunda.search.schema.config.IndexConfiguration;
import io.camunda.search.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.search.schema.opensearch.OpensearchEngineClient;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCacheImpl;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.util.cache.CaffeineCacheStatsCounter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.client.opensearch._types.Refresh;

class BatchOperationCacheIT {

  @RegisterExtension private static final SearchDBExtension SEARCH_DB = SearchDBExtension.create();

  @BeforeEach
  void setup() {
    if (System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL, "").isBlank()) {
      new ElasticsearchEngineClient(SEARCH_DB.esClient(), SEARCH_DB.objectMapper())
          .createIndex(BATCH_OPERATION_INDEX, new IndexConfiguration());
    }
    new OpensearchEngineClient(SEARCH_DB.osClient(), SEARCH_DB.objectMapper())
        .createIndex(BATCH_OPERATION_INDEX, new IndexConfiguration());
  }

  @AfterEach
  void cleanup() throws IOException {
    if (System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL, "").isBlank()) {
      SEARCH_DB
          .esClient()
          .indices()
          .delete(req -> req.index(BATCH_OPERATION_INDEX.getFullQualifiedName()));
    }
    SEARCH_DB
        .osClient()
        .indices()
        .delete(req -> req.index(BATCH_OPERATION_INDEX.getFullQualifiedName()));
  }

  @ParameterizedTest
  @MethodSource("provideBatchOperationCache")
  void shouldReturnEmptyOptionalIfBatchOperationDoesNotExist(
      final BatchOperationCacheArgument batchOperationCacheArgument) {
    // when
    final var batchOperation = batchOperationCacheArgument.batchOperationCache().get("1");

    // then
    assertThat(batchOperation).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("provideBatchOperationCache")
  void shouldLoadBatchOperationEntityFromBackend(
      final BatchOperationCacheArgument batchOperationCacheArgument) throws IOException {
    // given
    final var batchOperationEntity =
        new BatchOperationEntity().setId("3").setType(OperationType.RESOLVE_INCIDENT);
    batchOperationCacheArgument.indexer().accept(batchOperationEntity);

    // when
    final var batchOperation = batchOperationCacheArgument.batchOperationCache().get("3");

    // then
    final var expectedCachedBatchOperationEntity =
        new CachedBatchOperationEntity("3", BatchOperationType.RESOLVE_INCIDENT);
    assertThat(batchOperation).isPresent().get().isEqualTo(expectedCachedBatchOperationEntity);
  }

  @ParameterizedTest
  @MethodSource("provideFailingBatchOperationCache")
  void shouldThrowExceptionIfQueryToElasticFailed(
      final BatchOperationCacheArgument batchOperationCacheArgument) {
    // given
    final var failingBatchOperationCache = batchOperationCacheArgument.batchOperationCache();

    // when - then
    assertThatException()
        .isThrownBy(() -> failingBatchOperationCache.get("1"))
        .isInstanceOf(ExporterEntityCache.CacheLoaderFailedException.class);
  }

  static Stream<Arguments> provideBatchOperationCache() {
    if (System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL, "").isBlank()) {
      return Stream.of(
          Arguments.of(
              Named.of(
                  "ElasticSearch",
                  getESBatchOperationCache(BATCH_OPERATION_INDEX.getFullQualifiedName()))),
          Arguments.of(
              Named.of(
                  "OpenSearch",
                  getOSBatchOperationCache(BATCH_OPERATION_INDEX.getFullQualifiedName()))));
    } else {
      return Stream.of(
          Arguments.of(
              Named.of(
                  "OpenSearch",
                  getOSBatchOperationCache(BATCH_OPERATION_INDEX.getFullQualifiedName()))));
    }
  }

  static Stream<Arguments> provideFailingBatchOperationCache() {
    if (System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL, "").isBlank()) {
      return Stream.of(
          Arguments.of(Named.of("ElasticSearch", getESBatchOperationCache("invalid-index-name"))),
          Arguments.of(Named.of("OpenSearch", getOSBatchOperationCache("invalid-index-name"))));
    } else {
      return Stream.of(
          Arguments.of(Named.of("OpenSearch", getOSBatchOperationCache("invalid-index-name"))));
    }
  }

  static BatchOperationCacheArgument getESBatchOperationCache(final String indexName) {
    return new BatchOperationCacheArgument(
        new ExporterEntityCacheImpl<>(
            10,
            new ElasticSearchBatchOperationCacheLoader(SEARCH_DB.esClient(), indexName),
            new CaffeineCacheStatsCounter(
                DefaultExporterResourceProvider.NAMESPACE, "ES", new SimpleMeterRegistry())),
        BatchOperationCacheIT::indexInElasticSearch);
  }

  static BatchOperationCacheArgument getOSBatchOperationCache(final String indexName) {
    return new BatchOperationCacheArgument(
        new ExporterEntityCacheImpl<>(
            10,
            new OpenSearchBatchOperationCacheLoader(SEARCH_DB.osClient(), indexName),
            new CaffeineCacheStatsCounter(
                DefaultExporterResourceProvider.NAMESPACE, "OS", new SimpleMeterRegistry())),
        BatchOperationCacheIT::indexInOpenSearch);
  }

  private static void indexInElasticSearch(final BatchOperationEntity batchOperationEntity) {
    try {
      SEARCH_DB
          .esClient()
          .index(
              request ->
                  request
                      .index(BATCH_OPERATION_INDEX.getFullQualifiedName())
                      .id(batchOperationEntity.getId())
                      .document(batchOperationEntity)
                      .refresh(co.elastic.clients.elasticsearch._types.Refresh.True));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void indexInOpenSearch(final BatchOperationEntity batchOperationEntity) {
    try {
      SEARCH_DB
          .osClient()
          .index(
              request ->
                  request
                      .index(BATCH_OPERATION_INDEX.getFullQualifiedName())
                      .id(batchOperationEntity.getId())
                      .document(batchOperationEntity)
                      .refresh(Refresh.True));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  record BatchOperationCacheArgument(
      ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache,
      Consumer<BatchOperationEntity> indexer) {}
}
