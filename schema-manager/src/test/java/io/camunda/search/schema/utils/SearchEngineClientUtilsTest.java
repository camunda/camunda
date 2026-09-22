/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema.utils;

import static io.camunda.search.schema.utils.SearchEngineClientUtils.MAX_INDEX_PATTERN_REQUEST_LENGTH;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.test.utils.TestObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SearchEngineClientUtilsTest {

  @Test
  void shouldReturnSingleBatchWhenPatternWithinLimit() {
    // given
    final var namePattern = "index-a*,index-b*,index-c*";

    // when
    final var batches = SearchEngineClientUtils.batchPatterns(namePattern);

    // then
    assertThat(batches).hasSize(1);
    assertThat(batches.get(0)).isEqualTo(namePattern);
  }

  @Test
  void shouldReturnEmptyListWhenNullPatternGiven() {
    // when
    final var batches = SearchEngineClientUtils.batchPatterns(null);

    // then
    assertThat(batches).isEmpty();
  }

  @Test
  void shouldReturnEmptyListWhenEmptyPatternGiven() {
    // when
    final var batches = SearchEngineClientUtils.batchPatterns("");

    // then
    assertThat(batches).isEmpty();
  }

  @Test
  void shouldSplitIntoBatchesWhenPatternExceedsMaxLength() {
    // given – create patterns that together exceed the max request length
    final var longPart = "a".repeat(2000);
    final var namePattern = longPart + "*," + longPart + "*," + longPart + "*";

    // when
    final var batches = SearchEngineClientUtils.batchPatterns(namePattern);

    // then
    assertThat(batches).hasSizeGreaterThan(1);
    // Each batch must not exceed the configured max length
    batches.forEach(
        batch -> assertThat(batch.length()).isLessThanOrEqualTo(MAX_INDEX_PATTERN_REQUEST_LENGTH));
    // All patterns must appear across batches
    final var allPatterns = batches.stream().flatMap(b -> Stream.of(b.split(","))).toList();
    assertThat(allPatterns).containsExactlyInAnyOrderElementsOf(List.of(namePattern.split(",")));
  }

  @Test
  void shouldPutOversizedSinglePatternInItsOwnBatch() {
    // given – a single pattern that is itself longer than the limit
    final var largePattern = "x".repeat(MAX_INDEX_PATTERN_REQUEST_LENGTH) + "*";

    // when
    final var batches = SearchEngineClientUtils.batchPatterns(largePattern);

    // then
    assertThat(batches).hasSize(1);
    assertThat(batches.get(0)).isEqualTo(largePattern);
  }

  @Test
  void shouldSkipBatchingWhenPatternContainsExclusionEntries() {
    // given – the exclusion pattern must not be split away from its include
    final var namePattern = "index-a*,-index-a,index-b*,-index-b";

    // when
    final var batches = SearchEngineClientUtils.batchPatterns(namePattern);

    // then – returned as-is so exclusion semantics are preserved
    assertThat(batches).hasSize(1);
    assertThat(batches.get(0)).isEqualTo(namePattern);
  }

  /**
   * Pins the contract of the comparison that decides whether an index template's settings still
   * need writing. Getting it wrong is what caused #63764: the comparison used to diff the whole
   * settings block against the engine's normalized rendering of it, which can never match for a
   * template carrying an {@code analysis} block, so every schema-init attempt re-issued the PUT.
   * These are unit tests on purpose — the engine ITs prove the call sites feed the right values,
   * but only this level can state the rules cheaply and exhaustively.
   */
  @Nested
  class MatchesConfiguredTemplateTest {

    private static final String MANAGED_ONLY =
        """
        {"settings": {"index": {"number_of_shards": 1, "number_of_replicas": 1}}}""";

    /**
     * The shape that used to break: an {@code analysis} block the template JSON owns, which the
     * engine stores in a normalized form (relocated under {@code index}, {@code "type": "custom"}
     * injected, the scalar {@code filter} coerced into a list).
     */
    private static final String WITH_CUSTOM_SETTINGS =
        """
        {"settings": {"index": {"analysis": {"normalizer": {"case_insensitive": \
        {"filter": "lowercase"}}}}}}""";

    private final SearchEngineClientUtils utils =
        new SearchEngineClientUtils(TestObjectMapper.objectMapper());

    @Test
    void shouldMatchWhenOnlyTheEngineSNormalizationOfCustomSettingsDiffers() throws IOException {
      // given - the engine's rendering of the same analysis block, normalized
      final var stored =
          Map.<String, Object>of(
              "index",
              Map.of(
                  "analysis",
                  Map.of(
                      "normalizer",
                      Map.of(
                          "case_insensitive",
                          Map.of("type", "custom", "filter", List.of("lowercase")))),
                  "number_of_shards",
                  1,
                  "number_of_replicas",
                  1));

      // when
      final var matches =
          appender(WITH_CUSTOM_SETTINGS)
              .withNumberOfShards(1)
              .withNumberOfReplicas(1)
              .matchesConfiguredTemplate(null, null, stored);

      // then - the normalized analysis block is ignored, so no rewrite is issued
      assertThat(matches).isTrue();
    }

    @Test
    void shouldMatchWhenStoredSettingsCarryUnmanagedKeysTheEngineAddedItself() throws IOException {
      // given
      final var stored =
          Map.<String, Object>of(
              "index",
              Map.of(
                  "number_of_shards",
                  1,
                  "number_of_replicas",
                  1,
                  "provided_name",
                  "some-index",
                  "creation_date",
                  "1758000000000"));

      // when, then
      assertThat(appender(MANAGED_ONLY).matchesConfiguredTemplate(null, null, stored)).isTrue();
    }

    @Test
    void shouldNotMatchWhenAManagedSettingDiffers() throws IOException {
      // given
      final var stored =
          Map.<String, Object>of("index", Map.of("number_of_shards", 1, "number_of_replicas", 3));

      // when, then
      assertThat(appender(MANAGED_ONLY).matchesConfiguredTemplate(null, null, stored)).isFalse();
    }

    @Test
    void shouldNotMatchWhenARefreshIntervalIsConfiguredButNotStored() throws IOException {
      // given
      final var stored =
          Map.<String, Object>of("index", Map.of("number_of_shards", 1, "number_of_replicas", 1));

      // when
      final var matches =
          appender(MANAGED_ONLY)
              .withRefreshInterval("2s")
              .matchesConfiguredTemplate(null, null, stored);

      // then
      assertThat(matches).isFalse();
    }

    @Test
    void shouldNotMatchWhenStoredSettingsHaveNoIndexBlockAtAll() throws IOException {
      // given, when, then - a template that somehow lost its settings must be rewritten
      assertThat(appender(MANAGED_ONLY).matchesConfiguredTemplate(null, null, Map.of())).isFalse();
    }

    /**
     * Both the body and the settings of a composable index template are optional, so an engine
     * client hands over {@code null} for a template stored without either. That has to read as a
     * mismatch to be repaired, not blow up the comparison and fail every schema-init attempt.
     */
    @Test
    void shouldNotMatchWhenTheTemplateStoresNoSettingsAtAll() throws IOException {
      // given, when, then
      assertThat(appender(MANAGED_ONLY).matchesConfiguredTemplate(null, null, null)).isFalse();
    }

    /**
     * Elasticsearch renders the managed settings back as strings and OpenSearch as numbers, which
     * is why the two engine clients append them differently. Nothing here coerces, so a client that
     * appends the wrong representation would never match and would rewrite every template on every
     * restart.
     */
    @Test
    void shouldNotMatchWhenAManagedSettingOnlyDiffersInItsRepresentation() throws IOException {
      // given - configured as numbers, stored as strings
      final var stored =
          Map.<String, Object>of(
              "index", Map.of("number_of_shards", "1", "number_of_replicas", "1"));

      // when, then
      assertThat(appender(MANAGED_ONLY).matchesConfiguredTemplate(null, null, stored)).isFalse();
    }

    @Test
    void shouldMatchWhenNeitherTheStoredNorTheConfiguredTemplateHasAPriority() throws IOException {
      // given - the shape of a live template: no priority is configured, none is stored
      final var stored =
          Map.<String, Object>of("index", Map.of("number_of_shards", 1, "number_of_replicas", 1));

      // when, then
      assertThat(appender(MANAGED_ONLY).matchesConfiguredTemplate(null, null, stored)).isTrue();
    }

    @Test
    void shouldNotMatchWhenThePriorityDiffers() throws IOException {
      // given
      final var stored =
          Map.<String, Object>of("index", Map.of("number_of_shards", 1, "number_of_replicas", 1));

      // when, then
      assertThat(appender(MANAGED_ONLY).matchesConfiguredTemplate(null, 50L, stored)).isFalse();
      assertThat(appender(MANAGED_ONLY).matchesConfiguredTemplate(50L, null, stored)).isFalse();
    }

    private SearchEngineClientUtils.SchemaSettingsAppender appender(final String templateJson)
        throws IOException {
      return utils
      .new SchemaSettingsAppender(
          new ByteArrayInputStream(templateJson.getBytes(StandardCharsets.UTF_8)));
    }
  }
}
