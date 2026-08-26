/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.beans.Introspector;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class NumberOfShardsPerIndexTest {

  private static NumberOfShardsPerIndex bind(final Map<String, Object> properties) {
    final ConfigurationPropertySource source = new MapConfigurationPropertySource(properties);
    return new Binder(source)
        .bind("shards", NumberOfShardsPerIndex.class)
        .orElseGet(NumberOfShardsPerIndex::new);
  }

  @Test
  void shouldLeaveUnsetIndicesOutOfTheMap() {
    // given
    final var shards = new NumberOfShardsPerIndex();

    // when
    shards.setListView(3);

    // then — an absent entry is what lets the descriptor default stay in play
    assertThat(shards.toIndexNameMap()).containsExactly(Map.entry("list-view", 3));
  }

  @Test
  void shouldKeyTheMapByRawIndexName() {
    // given
    final var shards = new NumberOfShardsPerIndex();

    // when
    shards.setPostImporterQueue(1);
    shards.setFlownodeInstance(4);
    shards.setUsageMetricTu(2);
    shards.setWebSession(5);

    // then
    assertThat(shards.toIndexNameMap())
        .containsOnly(
            Map.entry("post-importer-queue", 1),
            Map.entry("flownode-instance", 4),
            Map.entry("usage-metric-tu", 2),
            Map.entry("web-session", 5));
  }

  @Nested
  class RelaxedBinding {

    @Test
    void shouldBindKebabCaseYamlKeys() {
      // given — the shape existing deployments already have in their YAML
      final var properties =
          Map.<String, Object>of("shards.list-view", "3", "shards.post-importer-queue", "1");

      // when
      final var shards = bind(properties);

      // then
      assertThat(shards.toIndexNameMap())
          .containsOnly(Map.entry("list-view", 3), Map.entry("post-importer-queue", 1));
    }

    @Test
    void shouldBindEnvironmentVariableStyleKeys() {
      // given — the shape a Map keyed by raw index name could never bind, since the dashes in
      // `post-importer-queue` are not expressible in an environment variable name
      final var environment =
          new SystemEnvironmentPropertySource(
              StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
              Map.of("SHARDS_LISTVIEW", "3", "SHARDS_POSTIMPORTERQUEUE", "1"));

      // when
      final var shards =
          new Binder(ConfigurationPropertySources.from(environment))
              .bind("shards", NumberOfShardsPerIndex.class)
              .orElseGet(NumberOfShardsPerIndex::new);

      // then
      assertThat(shards.toIndexNameMap())
          .containsOnly(Map.entry("list-view", 3), Map.entry("post-importer-queue", 1));
    }
  }

  @Nested
  class ShardCountValidation {

    @Test
    void shouldRejectAnIndexConfiguredWithFewerThanOneShard() {
      // given
      final var elasticsearch = new Elasticsearch();
      elasticsearch.getNumberOfShardsPerIndex().setListView(0);

      // when / then — the search engine would otherwise reject schema creation with an error
      // naming neither the property nor the index
      assertThatThrownBy(elasticsearch::resolveNumberOfShardsPerIndex)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage(
              "camunda.data.secondary-storage.elasticsearch.number-of-shards-per-index.list-view"
                  + " must be at least 1, but was 0");
    }

    @Test
    void shouldAcceptASingleShard() {
      // given
      final var elasticsearch = new Elasticsearch();
      elasticsearch.getNumberOfShardsPerIndex().setListView(1);

      // when / then
      assertThat(elasticsearch.resolveNumberOfShardsPerIndex())
          .containsExactly(Map.entry("list-view", 1));
    }
  }

  @Nested
  class Coverage {

    /**
     * Guards the field-name to index-name projection: a field whose {@code toIndexNameMap} entry is
     * missing would silently swallow that index's override.
     */
    @Test
    void shouldProjectEveryFieldOntoTheMap() {
      // given
      final var shards = new NumberOfShardsPerIndex();
      final var fieldNames =
          Arrays.stream(NumberOfShardsPerIndex.class.getDeclaredFields())
              .filter(f -> f.getType() == Integer.class)
              .map(java.lang.reflect.Field::getName)
              .collect(Collectors.toSet());

      // when — set every field through its setter
      Arrays.stream(NumberOfShardsPerIndex.class.getMethods())
          .filter(m -> m.getName().startsWith("set") && m.getParameterCount() == 1)
          .filter(m -> fieldNames.contains(Introspector.decapitalize(m.getName().substring(3))))
          .forEach(
              m -> {
                try {
                  m.invoke(shards, 2);
                } catch (final ReflectiveOperationException e) {
                  throw new AssertionError(e);
                }
              });

      // then
      assertThat(shards.toIndexNameMap()).hasSize(fieldNames.size());
    }
  }
}
