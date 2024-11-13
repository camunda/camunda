/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.cache;

import static io.camunda.zeebe.model.bpmn.Bpmn.convertToString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.exporter.cache.ExporterEntityCache.CacheLoaderFailedException;
import io.camunda.exporter.cache.process.CachedProcessEntity;
import io.camunda.exporter.cache.process.ElasticSearchProcessCacheLoader;
import io.camunda.exporter.cache.process.OpenSearchProcessCacheLoader;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.config.ExporterConfiguration.IndexSettings;
import io.camunda.exporter.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.exporter.schema.opensearch.OpensearchEngineClient;
import io.camunda.exporter.utils.XMLUtil;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.entities.operate.ProcessEntity;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ProcessCacheImplIT {

  @Container
  private static final ElasticsearchContainer ELASTICSEARCH_CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer();

  @Container
  private static final OpensearchContainer OPENSEARCH_CONTAINER =
      TestSearchContainers.createDefaultOpensearchContainer();

  private static ElasticsearchClient elsClient;
  private static OpenSearchClient osClient;
  private static final ProcessIndex PROCESS_INDEX = new ProcessIndex("test", true);

  @BeforeAll
  static void init() {
    final var config = new ExporterConfiguration();
    config.getConnect().setUrl(ELASTICSEARCH_CONTAINER.getHttpHostAddress());
    elsClient = new ElasticsearchConnector(config.getConnect()).createClient();

    final var osConfig = new ExporterConfiguration();
    osConfig.getConnect().setType("opensearch");
    osConfig.getConnect().setUrl(OPENSEARCH_CONTAINER.getHttpHostAddress());
    osClient = new OpensearchConnector(osConfig.getConnect()).createClient();
  }

  @BeforeEach
  void setup() {
    new ElasticsearchEngineClient(elsClient).createIndex(PROCESS_INDEX, new IndexSettings());
    new OpensearchEngineClient(osClient).createIndex(PROCESS_INDEX, new IndexSettings());
  }

  @AfterEach
  void cleanup() throws IOException {
    elsClient.indices().delete(req -> req.index(PROCESS_INDEX.getFullQualifiedName()));
    osClient.indices().delete(req -> req.index(PROCESS_INDEX.getFullQualifiedName()));
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
  void shouldLoadProcessEntityFromBackend(final ProcessCacheArgument processCacheArgument)
      throws IOException {
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
    return Stream.of(
        Arguments.of(
            Named.of("ElasticSearch", getESProcessCache(PROCESS_INDEX.getFullQualifiedName()))),
        Arguments.of(
            Named.of("OpenSearch", getOSProcessCache(PROCESS_INDEX.getFullQualifiedName()))));
  }

  static Stream<Arguments> provideFailingProcessCache() {
    return Stream.of(
        Arguments.of(Named.of("ElasticSearch", getESProcessCache("invalid-index-name"))),
        Arguments.of(Named.of("OpenSearch", getOSProcessCache("invalid-index-name"))));
  }

  static ProcessCacheArgument getESProcessCache(final String indexName) {
    return new ProcessCacheArgument(
        new ExporterEntityCacheImpl(
            10, new ElasticSearchProcessCacheLoader(elsClient, indexName, new XMLUtil())),
        ProcessCacheImplIT::indexInElasticSearch);
  }

  static ProcessCacheArgument getOSProcessCache(final String indexName) {
    return new ProcessCacheArgument(
        new ExporterEntityCacheImpl(
            10, new OpenSearchProcessCacheLoader(osClient, indexName, new XMLUtil())),
        ProcessCacheImplIT::indexInOpenSearch);
  }

  private static void indexInElasticSearch(final ProcessEntity processEntity) {
    try {
      elsClient.index(
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
      osClient.index(
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
