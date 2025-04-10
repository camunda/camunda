/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.cache;

import static io.camunda.search.test.utils.SearchDBExtension.PROCESS_INDEX;
import static io.camunda.search.test.utils.SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL;
import static io.camunda.zeebe.model.bpmn.Bpmn.convertToString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;

import io.camunda.exporter.DefaultExporterResourceProvider;
import io.camunda.exporter.cache.ExporterEntityCache.CacheLoaderFailedException;
import io.camunda.exporter.cache.process.CachedProcessEntity;
import io.camunda.exporter.cache.process.ElasticSearchProcessCacheLoader;
import io.camunda.exporter.cache.process.OpenSearchProcessCacheLoader;
import io.camunda.search.schema.config.IndexConfiguration;
import io.camunda.search.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.search.schema.opensearch.OpensearchEngineClient;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
import io.camunda.zeebe.util.cache.CaffeineCacheStatsCounter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProcessCacheImplIT {

  @RegisterExtension private static final SearchDBExtension SEARCH_DB = SearchDBExtension.create();

  @BeforeEach
  void setup() {
    new ElasticsearchEngineClient(SEARCH_DB.esClient(), SEARCH_DB.objectMapper())
        .createIndex(PROCESS_INDEX, new IndexConfiguration());
    new OpensearchEngineClient(SEARCH_DB.osClient(), SEARCH_DB.objectMapper())
        .createIndex(PROCESS_INDEX, new IndexConfiguration());
  }

  @AfterEach
  void cleanup() throws IOException {
    SEARCH_DB.esClient().indices().delete(req -> req.index(PROCESS_INDEX.getFullQualifiedName()));
    SEARCH_DB.osClient().indices().delete(req -> req.index(PROCESS_INDEX.getFullQualifiedName()));
  }

  @ParameterizedTest
  @MethodSource("provideProcessCache")
  void shouldReturnEmptyOptionalIfProcessDoesNotExist(
      final ProcessCacheArgument processCacheArgument) {
    // when
    final var process = processCacheArgument.processCache().get(1L);

    // then
    assertThat(process).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("provideProcessCache")
  void shouldLoadProcessEntityFromBackend(final ProcessCacheArgument processCacheArgument) {
    // given
    final var processEntity =
        new ProcessEntity()
            .setId("3")
            .setName("test")
            .setVersionTag("v1")
            .setBpmnProcessId("test")
            .setBpmnXml(createBpmnWithCallActivities("test", List.of("Banana", "apple", "Cherry")));
    processCacheArgument.indexer().accept(processEntity);

    // when
    final var process = processCacheArgument.processCache().get(3L);

    // then
    final var expectedCachedProcessEntity =
        new CachedProcessEntity("test", "v1", List.of("Banana", "Cherry", "apple"));
    assertThat(process).isPresent().get().isEqualTo(expectedCachedProcessEntity);
  }

  private String createBpmnWithCallActivities(
      final String bpmnProcessId, final List<String> callActivityIds) {
    final StartEventBuilder seb = Bpmn.createExecutableProcess(bpmnProcessId).startEvent();
    callActivityIds.forEach(ca -> seb.callActivity(ca).zeebeProcessId(ca));
    return convertToString(seb.done());
  }

  @ParameterizedTest
  @MethodSource("provideFailingProcessCache")
  void shouldThrowExceptionIfQueryToElasticFailed(final ProcessCacheArgument processCacheArgument) {
    // given
    final var failingProcessCache = processCacheArgument.processCache();

    // when - then
    assertThatException()
        .isThrownBy(() -> failingProcessCache.get(1L))
        .isInstanceOf(CacheLoaderFailedException.class);
  }

  static Stream<Arguments> provideProcessCache() {
    if (System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL) == null) {
      return Stream.of(
          Arguments.of(
              Named.of(
                  "ElasticSearch",
                  getESProcessCache(SearchDBExtension.PROCESS_INDEX.getFullQualifiedName()))),
          Arguments.of(
              Named.of(
                  "OpenSearch",
                  getOSProcessCache(SearchDBExtension.PROCESS_INDEX.getFullQualifiedName()))));
    } else {
      return Stream.of(
          Arguments.of(
              Named.of(
                  "OpenSearch",
                  getOSProcessCache(SearchDBExtension.PROCESS_INDEX.getFullQualifiedName()))));
    }
  }

  static Stream<Arguments> provideFailingProcessCache() {
    if (System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL) == null) {
      return Stream.of(
          Arguments.of(Named.of("ElasticSearch", getESProcessCache("invalid-index-name"))),
          Arguments.of(Named.of("OpenSearch", getOSProcessCache("invalid-index-name"))));
    } else {
      return Stream.of(
          Arguments.of(Named.of("OpenSearch", getOSProcessCache("invalid-index-name"))));
    }
  }

  static ProcessCacheArgument getESProcessCache(final String indexName) {
    return new ProcessCacheArgument(
        new ExporterEntityCacheImpl<>(
            10,
            new ElasticSearchProcessCacheLoader(SEARCH_DB.esClient(), indexName),
            new CaffeineCacheStatsCounter(
                DefaultExporterResourceProvider.NAMESPACE, "ES", new SimpleMeterRegistry())),
        ProcessCacheImplIT::indexInElasticSearch);
  }

  static ProcessCacheArgument getOSProcessCache(final String indexName) {
    return new ProcessCacheArgument(
        new ExporterEntityCacheImpl<>(
            10,
            new OpenSearchProcessCacheLoader(SEARCH_DB.osClient(), indexName),
            new CaffeineCacheStatsCounter(
                DefaultExporterResourceProvider.NAMESPACE, "OS", new SimpleMeterRegistry())),
        ProcessCacheImplIT::indexInOpenSearch);
  }

  private static void indexInElasticSearch(final ProcessEntity processEntity) {
    try {
      SEARCH_DB
          .esClient()
          .index(
              request ->
                  request
                      .index(PROCESS_INDEX.getFullQualifiedName())
                      .id(processEntity.getId())
                      .document(processEntity));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void indexInOpenSearch(final ProcessEntity processEntity) {
    try {
      SEARCH_DB
          .osClient()
          .index(
              request ->
                  request
                      .index(PROCESS_INDEX.getFullQualifiedName())
                      .id(processEntity.getId())
                      .document(processEntity));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  record ProcessCacheArgument(
      ExporterEntityCache<Long, CachedProcessEntity> processCache,
      Consumer<ProcessEntity> indexer) {}
}
