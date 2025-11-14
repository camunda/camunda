/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.cache;

import static io.camunda.search.test.utils.SearchDBExtension.DECISIONREQUIREMENTS_INDEX;
import static io.camunda.search.test.utils.SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;

import io.camunda.exporter.DefaultExporterResourceProvider;
import io.camunda.exporter.cache.decision.ElasticSearchDecisionRequirementsCacheLoader;
import io.camunda.exporter.cache.decision.OpenSearchDecisionRequirementsCacheLoader;
import io.camunda.search.schema.config.IndexConfiguration;
import io.camunda.search.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.search.schema.opensearch.OpensearchEngineClient;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.webapps.schema.entities.dmn.definition.DecisionRequirementsEntity;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache.CacheLoaderFailedException;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCacheImpl;
import io.camunda.zeebe.exporter.common.cache.decisionRequirements.CachedDecisionRequirementsEntity;
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

class DecisionRequirementsCacheIT {
  @RegisterExtension private static final SearchDBExtension SEARCH_DB = SearchDBExtension.create();

  @BeforeEach
  void setup() {
    if (System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL, "").isBlank()) {
      new ElasticsearchEngineClient(SEARCH_DB.esClient(), SEARCH_DB.objectMapper())
          .createIndex(DECISIONREQUIREMENTS_INDEX, new IndexConfiguration());
    }
    new OpensearchEngineClient(SEARCH_DB.osClient(), SEARCH_DB.objectMapper())
        .createIndex(DECISIONREQUIREMENTS_INDEX, new IndexConfiguration());
  }

  @AfterEach
  void cleanup() throws IOException {
    if (System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL, "").isBlank()) {
      SEARCH_DB
          .esClient()
          .indices()
          .delete(req -> req.index(DECISIONREQUIREMENTS_INDEX.getFullQualifiedName()));
    }
    SEARCH_DB
        .osClient()
        .indices()
        .delete(req -> req.index(DECISIONREQUIREMENTS_INDEX.getFullQualifiedName()));
  }

  @ParameterizedTest
  @MethodSource("provideDRDCache")
  void shouldReturnEmptyOptionalIfDrdDoesNotExist(final DRDCacheArgument drdCacheArgument) {
    // when
    final var decisionRequirement = drdCacheArgument.drdCache().get(1L);

    // then
    assertThat(decisionRequirement).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("provideDRDCache")
  void shouldLoadDrdEntityFromBackend(final DRDCacheArgument drdCacheArgument) {
    // given
    final var drdEntity =
        new DecisionRequirementsEntity()
            .setId("3")
            .setKey(3L)
            .setDecisionRequirementsId("drd-id")
            .setName("testdrd")
            .setVersion(2);
    drdCacheArgument.indexer().accept(drdEntity);

    // when
    final var drd = drdCacheArgument.drdCache.get(3L);

    // then
    final var expectedCachedDrdEntity = new CachedDecisionRequirementsEntity(3L, "testdrd", 2);
    assertThat(drd).isPresent().get().isEqualTo(expectedCachedDrdEntity);
  }

  @ParameterizedTest
  @MethodSource("provideFailingDRDCache")
  void shouldThrowExceptionIfQueryToElasticFailed(final DRDCacheArgument drdCacheArgument) {
    // given
    final var failingDrdCache = drdCacheArgument.drdCache();

    // when - then
    assertThatException()
        .isThrownBy(() -> failingDrdCache.get(1L))
        .isInstanceOf(CacheLoaderFailedException.class);
  }

  static Stream<Arguments> provideDRDCache() {
    if (System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL, "").isBlank()) {
      return Stream.of(
          Arguments.of(
              Named.of(
                  "ElasticSearch",
                  getESDRDCache(
                      SearchDBExtension.DECISIONREQUIREMENTS_INDEX.getFullQualifiedName()))),
          Arguments.of(
              Named.of(
                  "OpenSearch",
                  getOSDRDCache(
                      SearchDBExtension.DECISIONREQUIREMENTS_INDEX.getFullQualifiedName()))));
    } else {
      return Stream.of(
          Arguments.of(
              Named.of(
                  "OpenSearch",
                  getOSDRDCache(
                      SearchDBExtension.DECISIONREQUIREMENTS_INDEX.getFullQualifiedName()))));
    }
  }

  static Stream<Arguments> provideFailingDRDCache() {
    if (System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL, "").isBlank()) {
      return Stream.of(
          Arguments.of(Named.of("ElasticSearch", getESDRDCache("invalid-index-name"))),
          Arguments.of(Named.of("OpenSearch", getOSDRDCache("invalid-index-name"))));
    } else {
      return Stream.of(Arguments.of(Named.of("OpenSearch", getOSDRDCache("invalid-index-name"))));
    }
  }

  static DRDCacheArgument getESDRDCache(final String indexName) {
    return new DRDCacheArgument(
        new ExporterEntityCacheImpl<>(
            10,
            new ElasticSearchDecisionRequirementsCacheLoader(SEARCH_DB.esClient(), indexName),
            new CaffeineCacheStatsCounter(
                DefaultExporterResourceProvider.NAMESPACE, "ES", new SimpleMeterRegistry())),
        DecisionRequirementsCacheIT::indexInElasticSearch);
  }

  static DRDCacheArgument getOSDRDCache(final String indexName) {
    return new DRDCacheArgument(
        new ExporterEntityCacheImpl<>(
            10,
            new OpenSearchDecisionRequirementsCacheLoader(SEARCH_DB.osClient(), indexName),
            new CaffeineCacheStatsCounter(
                DefaultExporterResourceProvider.NAMESPACE, "OS", new SimpleMeterRegistry())),
        DecisionRequirementsCacheIT::indexInOpenSearch);
  }

  private static void indexInElasticSearch(
      final DecisionRequirementsEntity decisionRequirementsEntity) {
    try {
      SEARCH_DB
          .esClient()
          .index(
              request ->
                  request
                      .index(DECISIONREQUIREMENTS_INDEX.getFullQualifiedName())
                      .id(decisionRequirementsEntity.getId())
                      .document(decisionRequirementsEntity));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void indexInOpenSearch(
      final DecisionRequirementsEntity decisionRequirementsEntity) {
    try {
      SEARCH_DB
          .osClient()
          .index(
              request ->
                  request
                      .index(DECISIONREQUIREMENTS_INDEX.getFullQualifiedName())
                      .id(decisionRequirementsEntity.getId())
                      .document(decisionRequirementsEntity));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  record DRDCacheArgument(
      ExporterEntityCache<Long, CachedDecisionRequirementsEntity> drdCache,
      Consumer<DecisionRequirementsEntity> indexer) {}
}
