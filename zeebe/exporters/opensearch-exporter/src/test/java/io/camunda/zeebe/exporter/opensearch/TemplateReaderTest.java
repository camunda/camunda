/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration.IndexConfiguration;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.VersionUtil;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class TemplateReaderTest {
  private final IndexConfiguration config = new IndexConfiguration();
  private final TemplateReader templateReader = new TemplateReader(config);

  @Test
  void shouldReadComponentTemplate() {
    // given

    // when
    final var template = templateReader.readComponentTemplate();

    // then
    assertThat(template.composedOf())
        .as("component template is not composed of anything")
        .isNullOrEmpty();
    assertThat(template.patterns())
        .as(
            "component template has no search patterns as it's meant to be combined"
                + " with one or more index templates")
        .isNullOrEmpty();
    assertThat(template.template().aliases())
        .as("component template has no need for aliases")
        .isNullOrEmpty();
    assertThat(template.template().settings())
        .containsEntry("number_of_shards", 1)
        .containsEntry("number_of_replicas", 0)
        .containsEntry("index.queries.cache.enabled", false);
  }

  @Test
  void shouldSetNumberOfShardsInComponentTemplate() {
    // given
    config.setNumberOfShards(30);

    // when
    final var template = templateReader.readComponentTemplate();

    // then
    assertThat(template.template().settings())
        .as("should have the configured number of shards")
        .containsEntry("number_of_shards", 30);
  }

  @Test
  void shouldSetNumberOfReplicasInComponentTemplate() {
    // given
    config.setNumberOfReplicas(20);

    // when
    final var template = templateReader.readComponentTemplate();

    // then
    assertThat(template.template().settings())
        .as("should have the configured number of replicas")
        .containsEntry("number_of_replicas", 20);
  }

  @Test
  void shouldSetNumberOfShardsInIndexTemplate() {
    // given
    final var valueType = ValueType.VARIABLE;
    config.setNumberOfShards(43);

    // when
    final var template = templateReader.readIndexTemplate(valueType, "searchPattern", "alias");

    // then
    assertThat(template.template().settings())
        .as("should have the configured number of shards")
        .containsEntry("number_of_shards", 43);
  }

  @Test
  void shouldSetNumberOfReplicasInIndexTemplate() {
    // given
    final var valueType = ValueType.VARIABLE;
    config.setNumberOfReplicas(10);

    // when
    final var template = templateReader.readIndexTemplate(valueType, "searchPattern", "alias");

    // then
    assertThat(template.template().settings())
        .as("should have the configured number of replicas")
        .containsEntry("number_of_replicas", 10);
  }

  @Test
  void shouldReadIndexTemplate() {
    // given
    final var valueType = ValueType.VARIABLE;

    // when
    final var template = templateReader.readIndexTemplate(valueType, "searchPattern", "alias");

    // then
    assertThat(template.composedOf())
        .as("index template is composed of the component template")
        .containsExactly(config.prefix + "-" + VersionUtil.getVersionLowerCase());
    assertThat(template.patterns()).containsExactly("searchPattern");
    assertThat(template.template().aliases())
        .containsExactlyEntriesOf(Map.of("alias", Collections.emptyMap()));
    assertThat(template.priority()).isEqualTo(20);
    assertThat(template.template().settings())
        .containsEntry("number_of_shards", 1)
        .containsEntry("number_of_replicas", 0)
        .containsEntry("index.queries.cache.enabled", false);
  }

  @Test
  void shouldReadIndexTemplateWithDifferentPrefix() {
    // given
    config.prefix = "foo-bar";
    final var valueType = ValueType.VARIABLE;

    // when
    final var template = templateReader.readIndexTemplate(valueType, "searchPattern", "alias");

    // then
    assertThat(template.composedOf())
        .allMatch(
            composedOf ->
                composedOf.equals(config.prefix + "-" + VersionUtil.getVersionLowerCase()));
  }
}
