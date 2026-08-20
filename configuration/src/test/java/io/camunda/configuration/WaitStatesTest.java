/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.zeebe.broker.exporter.context.ExporterConfiguration;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

class WaitStatesTest {

  @Test
  void shouldBeEnabledByDefault() {
    assertThat(new WaitStates().isEnabled()).as("WaitStates should be enabled by default").isTrue();
  }

  @Test
  void shouldConvertToWaitStateConfiguration() {
    // given
    final WaitStates waitStates = new WaitStates();

    // when
    final WaitStateConfiguration config = waitStates.toConfiguration();

    // then
    assertThat(config.isEnabled())
        .as("WaitStateConfiguration.enabled should match WaitStates.enabled")
        .isEqualTo(waitStates.isEnabled());
  }

  @Test
  void shouldPropagateDisabledToConfiguration() {
    // given
    final WaitStates waitStates = new WaitStates();
    waitStates.setEnabled(false);

    // when
    final WaitStateConfiguration config = waitStates.toConfiguration();

    // then
    assertThat(config.isEnabled()).isFalse();
  }

  @Nested
  @ActiveProfiles({"broker"})
  @SpringJUnitConfig({
    UnifiedConfiguration.class,
    UnifiedConfigurationHelper.class,
    BrokerBasedPropertiesOverride.class,
  })
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=rdbms",
        "camunda.data.wait-states.enabled=false"
      })
  class RdbmsExporterTest {
    @Test
    void shouldPropagateWaitStatesEnabledToRdbmsExporter(
        @Autowired final BrokerBasedProperties brokerBasedProperties) {
      // when
      final var exporter = brokerBasedProperties.getRdbmsExporter();
      final var config =
          ExporterConfiguration.fromArgs(
              io.camunda.exporter.rdbms.ExporterConfiguration.class, exporter.getArgs());

      // then
      assertThat(config.getWaitState().isEnabled()).isFalse();
    }
  }

  @Nested
  @ActiveProfiles({"broker"})
  @SpringJUnitConfig({
    UnifiedConfiguration.class,
    UnifiedConfigurationHelper.class,
    BrokerBasedPropertiesOverride.class,
  })
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=elasticsearch",
        "camunda.data.wait-states.enabled=false"
      })
  class CamundaExporterTest {
    @Test
    void shouldPropagateWaitStatesEnabledToCamundaExporter(
        @Autowired final BrokerBasedProperties brokerBasedProperties) {
      // when
      final var exporter = brokerBasedProperties.getCamundaExporter();
      final var config =
          ExporterConfiguration.fromArgs(
              io.camunda.exporter.config.ExporterConfiguration.class, exporter.getArgs());

      // then
      assertThat(config.getWaitState().isEnabled()).isFalse();
    }
  }
}
