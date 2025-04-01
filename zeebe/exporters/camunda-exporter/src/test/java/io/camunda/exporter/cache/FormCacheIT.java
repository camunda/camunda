/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.cache;

import static io.camunda.search.test.utils.SearchDBExtension.FORM_INDEX;
import static io.camunda.search.test.utils.SearchDBExtension.IDX_FORM_PREFIX;
import static io.camunda.search.test.utils.SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;

import io.camunda.exporter.cache.ExporterEntityCache.CacheLoaderFailedException;
import io.camunda.exporter.cache.form.CachedFormEntity;
import io.camunda.exporter.cache.form.ElasticSearchFormCacheLoader;
import io.camunda.exporter.cache.form.OpenSearchFormCacheLoader;
import io.camunda.search.schema.config.IndexConfiguration;
import io.camunda.search.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.search.schema.opensearch.OpensearchEngineClient;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.webapps.schema.entities.form.FormEntity;
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

class FormCacheIT {

  @RegisterExtension private static SearchDBExtension searchDB = SearchDBExtension.create();

  @BeforeEach
  void setup() {
    new ElasticsearchEngineClient(searchDB.esClient(), searchDB.objectMapper())
        .createIndex(FORM_INDEX, new IndexConfiguration());
    new OpensearchEngineClient(searchDB.osClient(), searchDB.objectMapper())
        .createIndex(FORM_INDEX, new IndexConfiguration());
  }

  @AfterEach
  void cleanup() throws IOException {
    searchDB.esClient().indices().delete(req -> req.index(FORM_INDEX.getFullQualifiedName()));
    searchDB.osClient().indices().delete(req -> req.index(FORM_INDEX.getFullQualifiedName()));
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
    final var formEntity = new FormEntity().setId("3").setFormId(IDX_FORM_PREFIX).setVersion(1L);
    formCacheArgument.indexer().accept(formEntity);

    // when
    final var form = formCacheArgument.formCache().get("3");

    // then
    final var expectedCachedFormEntity = new CachedFormEntity(IDX_FORM_PREFIX, 1L);
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
    if (System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL) == null) {
      return Stream.of(
          Arguments.of(
              Named.of("ElasticSearch", getESFormCache(FORM_INDEX.getFullQualifiedName()))),
          Arguments.of(Named.of("OpenSearch", getOSFormCache(FORM_INDEX.getFullQualifiedName()))));
    } else {
      return Stream.of(
          Arguments.of(Named.of("OpenSearch", getOSFormCache(FORM_INDEX.getFullQualifiedName()))));
    }
  }

  static Stream<Arguments> provideFailingFormCache() {
    if (System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL) == null) {
      return Stream.of(
          Arguments.of(Named.of("ElasticSearch", getESFormCache("invalid-index-name"))),
          Arguments.of(Named.of("OpenSearch", getOSFormCache("invalid-index-name"))));
    } else {
      return Stream.of(Arguments.of(Named.of("OpenSearch", getOSFormCache("invalid-index-name"))));
    }
  }

  static FormCacheArgument getESFormCache(final String indexName) {
    return new FormCacheArgument(
        new ExporterEntityCacheImpl<>(
            10,
            new ElasticSearchFormCacheLoader(searchDB.esClient(), indexName),
            new ExporterCacheMetrics("ES", new SimpleMeterRegistry())),
        FormCacheIT::indexInElasticSearch);
  }

  static FormCacheArgument getOSFormCache(final String indexName) {
    return new FormCacheArgument(
        new ExporterEntityCacheImpl<>(
            10,
            new OpenSearchFormCacheLoader(searchDB.osClient(), indexName),
            new ExporterCacheMetrics("ES", new SimpleMeterRegistry())),
        FormCacheIT::indexInOpenSearch);
  }

  private static void indexInElasticSearch(final FormEntity formEntity) {
    try {
      searchDB
          .esClient()
          .index(
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
      searchDB
          .osClient()
          .index(
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
