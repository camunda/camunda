/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.exporter.cache.ExporterEntityCache.CacheLoaderFailedException;
import io.camunda.exporter.cache.form.CachedFormEntity;
import io.camunda.exporter.cache.form.ElasticSearchFormCacheLoader;
import io.camunda.exporter.cache.form.OpenSearchFormCacheLoader;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.config.ExporterConfiguration.IndexSettings;
import io.camunda.exporter.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.exporter.schema.opensearch.OpensearchEngineClient;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.webapps.schema.descriptors.tasklist.index.FormIndex;
import io.camunda.webapps.schema.entities.tasklist.FormEntity;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
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
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class FormCacheIT {

  @Container
  private static final ElasticsearchContainer ELASTICSEARCH_CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer();

  @Container
  private static final OpensearchContainer OPENSEARCH_CONTAINER =
      TestSearchContainers.createDefaultOpensearchContainer();

  private static ElasticsearchClient elsClient;
  private static OpenSearchClient osClient;
  private static final FormIndex FORM_INDEX = new FormIndex("test", true);

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
    new ElasticsearchEngineClient(elsClient).createIndex(FORM_INDEX, new IndexSettings());
    new OpensearchEngineClient(osClient).createIndex(FORM_INDEX, new IndexSettings());
  }

  @AfterEach
  void cleanup() throws IOException {
    elsClient.indices().delete(req -> req.index(FORM_INDEX.getFullQualifiedName()));
    osClient.indices().delete(req -> req.index(FORM_INDEX.getFullQualifiedName()));
  }

  @ParameterizedTest
  @MethodSource("provideFormCache")
  void shouldReturnEmptyOptionalIfFormDoesNotExist(final FormCacheArgument formCacheArgument) {
    // when
    final var form = formCacheArgument.formCache().get("1");

    // then
    assertThat(form).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("provideFormCache")
  void shouldLoadFormEntityFromBackend(final FormCacheArgument formCacheArgument)
      throws IOException {
    // given
    final var formEntity = new FormEntity().setId("3").setFormId("test").setVersion(1L);
    formCacheArgument.indexer().accept(formEntity);

    // when
    final var form = formCacheArgument.formCache().get("3");

    // then
    final var expectedCachedFormEntity = new CachedFormEntity("test", 1L);
    assertThat(form).isPresent().get().isEqualTo(expectedCachedFormEntity);
  }

  @ParameterizedTest
  @MethodSource("provideFailingFormCache")
  void shouldThrowExceptionIfQueryToElasticFailed(final FormCacheArgument formCacheArgument) {
    // given
    final var failingFormCache = formCacheArgument.formCache();

    // when - then
    assertThatException()
        .isThrownBy(() -> failingFormCache.get("1"))
        .isInstanceOf(CacheLoaderFailedException.class);
  }

  static Stream<Arguments> provideFormCache() {
    return Stream.of(
        Arguments.of(Named.of("ElasticSearch", getESFormCache(FORM_INDEX.getFullQualifiedName()))),
        Arguments.of(Named.of("OpenSearch", getOSFormCache(FORM_INDEX.getFullQualifiedName()))));
  }

  static Stream<Arguments> provideFailingFormCache() {
    return Stream.of(
        Arguments.of(Named.of("ElasticSearch", getESFormCache("invalid-index-name"))),
        Arguments.of(Named.of("OpenSearch", getOSFormCache("invalid-index-name"))));
  }

  static FormCacheArgument getESFormCache(final String indexName) {
    return new FormCacheArgument(
        new ExporterEntityCacheImpl<String, CachedFormEntity>(
            10, new ElasticSearchFormCacheLoader(elsClient, indexName)),
        FormCacheIT::indexInElasticSearch);
  }

  static FormCacheArgument getOSFormCache(final String indexName) {
    return new FormCacheArgument(
        new ExporterEntityCacheImpl<String, CachedFormEntity>(
            10, new OpenSearchFormCacheLoader(osClient, indexName)),
        FormCacheIT::indexInOpenSearch);
  }

  private static void indexInElasticSearch(final FormEntity formEntity) {
    try {
      elsClient.index(
          request ->
              request
                  .index(FORM_INDEX.getFullQualifiedName())
                  .id(formEntity.getId())
                  .document(formEntity)
                  .refresh(co.elastic.clients.elasticsearch._types.Refresh.True));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void indexInOpenSearch(final FormEntity formEntity) {
    try {
      osClient.index(
          request ->
              request
                  .index(FORM_INDEX.getFullQualifiedName())
                  .id(formEntity.getId())
                  .document(formEntity)
                  .refresh(Refresh.True));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  record FormCacheArgument(
      ExporterEntityCache<String, CachedFormEntity> formCache, Consumer<FormEntity> indexer) {}
}
