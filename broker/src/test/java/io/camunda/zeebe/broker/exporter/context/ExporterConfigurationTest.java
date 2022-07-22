/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Execution(ExecutionMode.CONCURRENT)
final class ExporterConfigurationTest {
  @ParameterizedTest
  @ValueSource(strings = {"numberofshards", "numberOfShards", "NUMBEROFSHARDS"})
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
  @ValueSource(strings = {"numberofshards", "numberOfShards", "NUMBEROFSHARDS"})
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
    // given
    final var args = Map.<String, Object>of("numberofshards", 1, "unknownProp", false);
    final var expected = new Config(1);
    final var config = new ExporterConfiguration("id", args);

    // when
    final var instance = config.instantiate(Config.class);

    // then
    assertThat(instance).isEqualTo(expected);
  }

  private record Config(int numberOfShards) {}

  private record ContainerConfig(Config nested) {}
}
