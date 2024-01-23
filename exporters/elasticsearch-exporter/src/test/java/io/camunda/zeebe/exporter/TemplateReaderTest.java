/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.ValueType;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class TemplateReaderTest {

  private TemplateReader buildDefaultTemplateReader() {
    return buildTemplateReader(config -> {});
  }

  private TemplateReader buildTemplateReader(
      Consumer<ElasticsearchExporterConfiguration> configModifier) {

    final ElasticsearchExporterConfiguration config = new ElasticsearchExporterConfiguration();
    configModifier.accept(config);

    return new TemplateReader(config);
  }

  @Test
  void shouldReadComponentTemplate() {
    // given
    final TemplateReader templateReader = buildDefaultTemplateReader();

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
    final TemplateReader templateReader =
        buildTemplateReader(
            config -> {
              config.index.setNumberOfShards(30);
            });

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
    final TemplateReader templateReader =
        buildTemplateReader(
            config -> {
              config.index.setNumberOfReplicas(20);
            });

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
    final TemplateReader templateReader =
        buildTemplateReader(
            config -> {
              config.index.setNumberOfShards(43);
            });

    final var valueType = ValueType.VARIABLE;

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
    final TemplateReader templateReader =
        buildTemplateReader(
            config -> {
              config.index.setNumberOfReplicas(10);
            });

    final var valueType = ValueType.VARIABLE;

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
    final TemplateReader templateReader = buildDefaultTemplateReader();

    final var valueType = ValueType.VARIABLE;

    // when
    final var template = templateReader.readIndexTemplate(valueType, "searchPattern", "alias");

    // then
    assertThat(template.composedOf())
        .as("index template is composed of the component template")
        .containsExactly("zeebe-record");
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
    final String indexPrefix = "foo-bar";

    final TemplateReader templateReader =
        buildTemplateReader(
            config -> {
              config.index.prefix = indexPrefix;
            });
    final var valueType = ValueType.VARIABLE;

    // when
    final var template = templateReader.readIndexTemplate(valueType, "searchPattern", "alias");

    // then
    assertThat(template.composedOf()).allMatch(composedOf -> composedOf.equals(indexPrefix));
  }

  @Test
  void shouldReadIndexTemplateWithIndexLifecycleManagementPolicy() {
    // given
    final TemplateReader templateReader =
        buildTemplateReader(
            config -> {
              config.retention.setEnabled(true);
              config.retention.setPolicyName("auto-trash");
            });
    final var valueType = ValueType.VARIABLE;

    // when
    final var template = templateReader.readIndexTemplate(valueType, "searchPattern", "alias");

    // then
    assertThat(template.template().settings()).containsEntry("index.lifecycle.name", "auto-trash");
  }
}
