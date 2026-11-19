/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.GatewayRestPropertiesOverride.Converter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GatewayRestPropertiesOverrideConverterTest {

  @BeforeAll
  @AfterAll
  static void clearStaticEnvironment() {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  @Test
  void shouldEnableWaitStatesByDefault() {
    final var result = new Converter(new Camunda()).convert();

    assertThat(result.isWaitStatesEnabled()).isTrue();
  }

  @Test
  void shouldMapDisabledWaitStates() {
    final var camunda = new Camunda();
    camunda.getData().getWaitStates().setEnabled(false);

    final var result = new Converter(camunda).convert();

    assertThat(result.isWaitStatesEnabled()).isFalse();
  }
}
