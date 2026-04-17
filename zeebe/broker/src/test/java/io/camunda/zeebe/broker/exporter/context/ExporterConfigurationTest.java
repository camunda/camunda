/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.exporter.api.context.StrictConfiguration;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Execution(ExecutionMode.CONCURRENT)
final class ExporterConfigurationTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        // kebab-case (canonical form with KEBAB_CASE naming strategy)
        "number-of-shards",
        // camelCase (normalized to kebab-case by fromArgs pre-processing)
        "numberOfShards",
        // uppercase kebab-case (matched via ACCEPT_CASE_INSENSITIVE_PROPERTIES)
        "NUMBER-OF-SHARDS"
      })
  void shouldInstantiateConfigWithCaseInsensitiveProperties(final String property) {
    // given
    final var args = Map.<String, Object>of(property, 1);
    final var expected = new Config(1);
    final var config = new ExporterConfiguration("id", args);

    // when
    final var instance = config.instantiate(Config.class);

    // then
    assertThat(instance).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"number-of-shards", "numberOfShards", "NUMBER-OF-SHARDS"})
  void shouldInstantiateNestedConfigWithCaseInsensitiveProperties(final String property) {
    // given
    final var args = Map.<String, Object>of("nested", Map.of(property, 1));
    final var expected = new ContainerConfig(new Config(1));
    final var config = new ExporterConfiguration("id", args);

    // when
    final var instance = config.instantiate(ContainerConfig.class);

    // then
    assertThat(instance).isEqualTo(expected);
  }

  @Test
  void shouldInstantiateConfigWithUnknownProperty() {
    // given — unannotated config class: unknown properties are silently ignored
    final var args = Map.<String, Object>of("number-of-shards", 1, "unknownProp", false);
    final var expected = new Config(1);
    final var config = new ExporterConfiguration("id", args);

    // when
    final var instance = config.instantiate(Config.class);

    // then
    assertThat(instance).isEqualTo(expected);
  }

  @Test
  void shouldRejectStrictConfigWithUnknownProperty() {
    // given — @StrictConfiguration: unknown properties must be rejected
    final var args = Map.<String, Object>of("number-of-shards", 1, "unknownProp", false);
    final var config = new ExporterConfiguration("id", args);

    // when - then
    assertThatCode(() -> config.instantiate(StrictConfig.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown-prop")
        .hasMessageContaining("StrictConfig");
  }

  @RegressionTest("https://github.com/camunda/camunda/issues/4552")
  void shouldInstantiateMapOfIntegersAsList() {
    // given
    final var args =
        Map.<String, Object>of(
            "configs",
            Map.of("0", Map.of("number-of-shards", 0), "1", Map.of("number-of-shards", 1)));
    final var expected = new ListConfig(List.of(new Config(0), new Config(1)));
    final var config = new ExporterConfiguration("id", args);

    // when
    final var instance = config.instantiate(ListConfig.class);

    // then
    assertThat(instance).isEqualTo(expected);
  }

  @RegressionTest("https://github.com/camunda/camunda/issues/4552")
  void shouldInstantiateMapOfIntegersAsListNested() {
    // given
    final var serializedConfigs =
        Map.<String, Object>of(
            "0", Map.of("number-of-shards", 0), "1", Map.of("number-of-shards", 1));
    final var args =
        Map.<String, Object>of("list", Map.of("0", Map.of("configs", serializedConfigs)));
    final var expected =
        new NestedListConfig(List.of(new ListConfig(List.of(new Config(0), new Config(1)))));
    final var config = new ExporterConfiguration("id", args);

    // when
    final var instance = config.instantiate(NestedListConfig.class);

    // then
    assertThat(instance).isEqualTo(expected);
  }

  @RegressionTest("https://github.com/camunda/camunda/issues/4552")
  void shouldNotInstantiateSparseList() {
    // given — list indices start at 1 instead of 0, so insertion at index 1 fails
    final var args =
        Map.<String, Object>of(
            "configs",
            Map.of("1", Map.of("number-of-shards", 0), "2", Map.of("number-of-shards", 1)));
    final var config = new ExporterConfiguration("id", args);

    // when - then
    assertThatCode(() -> config.instantiate(ListConfig.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasRootCauseInstanceOf(IndexOutOfBoundsException.class);
  }

  @RegressionTest("https://github.com/camunda/camunda/issues/4552")
  void shouldNotInstantiateNonIntegerList() {
    // given — list indices are non-integer strings, so parsing to int fails
    final var args =
        Map.<String, Object>of(
            "configs",
            Map.of("foo", Map.of("number-of-shards", 0), "bar", Map.of("number-of-shards", 1)));
    final var config = new ExporterConfiguration("id", args);

    // when - then
    assertThatCode(() -> config.instantiate(ListConfig.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasRootCauseInstanceOf(NumberFormatException.class);
  }

  private record Config(int numberOfShards) {}

  @StrictConfiguration
  private record StrictConfig(int numberOfShards) {}

  private record ContainerConfig(Config nested) {}

  private record ListConfig(List<Config> configs) {}

  private record NestedListConfig(List<ListConfig> list) {}
}
