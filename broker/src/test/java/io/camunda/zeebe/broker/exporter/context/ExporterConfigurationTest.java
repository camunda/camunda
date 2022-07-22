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
import java.util.Objects;
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

  @SuppressWarnings("unused")
  private static final class ContainerConfig {
    private Config nested;

    public ContainerConfig(final Config nested) {
      this.nested = nested;
    }

    public ContainerConfig() {}

    public Config getNested() {
      return nested;
    }

    @Override
    public int hashCode() {
      return Objects.hash(nested);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ContainerConfig)) {
        return false;
      }
      final ContainerConfig that = (ContainerConfig) o;
      return Objects.equals(nested, that.nested);
    }
  }

  @SuppressWarnings("unused")
  private static final class Config {
    private int numberOfShards;

    public Config(final int numberOfShards) {
      this.numberOfShards = numberOfShards;
    }

    public Config() {}

    public int getNumberOfShards() {
      return numberOfShards;
    }

    @Override
    public int hashCode() {
      return Objects.hash(numberOfShards);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Config)) {
        return false;
      }
      final Config config = (Config) o;
      return numberOfShards == config.numberOfShards;
    }
  }
}
