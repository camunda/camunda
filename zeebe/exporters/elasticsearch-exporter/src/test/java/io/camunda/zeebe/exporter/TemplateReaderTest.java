/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.VersionUtil;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class TemplateReaderTest {
  private final ElasticsearchExporterConfiguration config =
      new ElasticsearchExporterConfiguration();
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
    config.index.setNumberOfShards(30);

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
    config.index.setNumberOfReplicas(20);

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
    config.index.setNumberOfShards(43);

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
    config.index.setNumberOfReplicas(10);

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
        .containsExactly(config.index.prefix + "-" + VersionUtil.getVersionLowerCase());
    assertThat(template.patterns()).containsExactly("searchPattern");
    assertThat(template.template().aliases())
        .containsExactlyEntriesOf(Map.of("alias", Collections.emptyMap()));
    assertThat(template.priority()).isEqualTo(20);
    assertThat(template.template().settings())
        .containsEntry("number_of_shards", 3)
        .containsEntry("number_of_replicas", 0)
        .containsEntry("index.queries.cache.enabled", false);
  }

  @Test
  void shouldReadIndexTemplateWithDifferentPrefix() {
    // given
    config.index.prefix = "foo-bar";
    final var valueType = ValueType.VARIABLE;

    // when
    final var template = templateReader.readIndexTemplate(valueType, "searchPattern", "alias");

    // then
    assertThat(template.composedOf())
        .allMatch(
            composedOf ->
                composedOf.equals(config.index.prefix + "-" + VersionUtil.getVersionLowerCase()));
  }

  @Test
  void shouldReadIndexTemplateWithIndexLifecycleManagementPolicy() {
    // given
    config.retention.setEnabled(true);
    config.retention.setPolicyName("auto-trash");
    final var valueType = ValueType.VARIABLE;

    // when
    final var template = templateReader.readIndexTemplate(valueType, "searchPattern", "alias");

    // then
    assertThat(template.template().settings()).containsEntry("index.lifecycle.name", "auto-trash");
  }

  @Test
  void shouldUseThreeShardsForHighTrafficIndices() {
    // given - high traffic value types that should have 3 shards for better load distribution
    final var highTrafficValueTypes =
        new ValueType[] {
          ValueType.PROCESS_INSTANCE,
          ValueType.JOB,
          ValueType.USER_TASK,
          ValueType.VARIABLE,
          ValueType.INCIDENT,
          ValueType.MESSAGE,
          ValueType.MESSAGE_SUBSCRIPTION,
          ValueType.DECISION_EVALUATION,
          ValueType.TIMER,
          ValueType.GLOBAL_LISTENER,
          ValueType.GLOBAL_LISTENER_BATCH
        };

    // when/then - verify each high traffic type uses 3 shards
    for (final var valueType : highTrafficValueTypes) {
      final var template = templateReader.readIndexTemplate(valueType, "searchPattern", "alias");
      assertThat(template.template().settings())
          .as("High traffic value type %s should use 3 shards for load distribution", valueType)
          .containsEntry("number_of_shards", 3);
    }
  }

  @Test
  void shouldUseOnShardForLowTrafficIndices() {
    // given - low traffic value types that can use 1 shard
    final var lowTrafficValueTypes =
        new ValueType[] {
          ValueType.DEPLOYMENT, ValueType.DECISION, ValueType.ERROR, ValueType.SIGNAL
        };

    // when/then - verify each low traffic type uses 1 shard
    for (final var valueType : lowTrafficValueTypes) {
      final var template = templateReader.readIndexTemplate(valueType, "searchPattern", "alias");
      assertThat(template.template().settings())
          .as("Low traffic value type %s should use 1 shard to minimize resource usage", valueType)
          .containsEntry("number_of_shards", 1);
    }
  }
}
