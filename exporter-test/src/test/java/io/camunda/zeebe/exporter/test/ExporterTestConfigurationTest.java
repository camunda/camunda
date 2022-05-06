/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class ExporterTestConfigurationTest {
  @Test
  void shouldInstantiateProvidedConfiguration() {
    // given
    final var instance = new Object();
    final var config = new ExporterTestConfiguration<>("exporter", instance);

    // when
    final var instantiated = config.instantiate(Object.class);

    // then
    assertThat(instantiated).isSameAs(instance);
  }

  @Test
  void shouldCastInstantiatedConfig() {
    // given
    final var instance = Integer.valueOf(3);
    final ExporterTestConfiguration<Object> config =
        new ExporterTestConfiguration<>("exporter", instance);

    // when
    final var instantiated = config.instantiate(Integer.class);

    // then
    assertThat(instantiated).isSameAs(instance);
  }

  @Test
  void shouldSupplyConfigurationOnInstantiate() {
    // given
    final var instance = new Object();
    final var config = new ExporterTestConfiguration<>("exporter", empty -> instance);

    // when
    final var instantiated = config.instantiate(Object.class);

    // then
    assertThat(instantiated).isSameAs(instance);
  }
}
