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
import io.camunda.zeebe.engine.EngineConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

public class EngineMappingsTest {

  @Nested
  @DisplayName("Default configuration")
  @SpringJUnitConfig({
    UnifiedConfiguration.class,
    BrokerBasedPropertiesOverride.class,
    UnifiedConfigurationHelper.class
  })
  @ActiveProfiles("broker")
  class Defaults {
    final BrokerBasedProperties brokerCfg;

    Defaults(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldDefaultInputModeToCombined() {
      assertThat(brokerCfg.getExperimental().getEngine().getInputMappingMode())
          .isEqualTo(EngineConfiguration.InputMappingMode.COMBINED);
    }
  }

  @Nested
  @DisplayName("Explicit property overrides")
  @SpringJUnitConfig({
    UnifiedConfiguration.class,
    BrokerBasedPropertiesOverride.class,
    UnifiedConfigurationHelper.class
  })
  @ActiveProfiles("broker")
  @TestPropertySource(
      properties = {
        "camunda.processing.engine.mappings.input-mode=ORDERED",
      })
  class ExplicitOverride {
    final BrokerBasedProperties brokerCfg;

    ExplicitOverride(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetInputModeToOrdered() {
      assertThat(brokerCfg.getExperimental().getEngine().getInputMappingMode())
          .isEqualTo(EngineConfiguration.InputMappingMode.ORDERED);
    }
  }

  @Nested
  @DisplayName("Comparison mode")
  @SpringJUnitConfig({
    UnifiedConfiguration.class,
    BrokerBasedPropertiesOverride.class,
    UnifiedConfigurationHelper.class
  })
  @ActiveProfiles("broker")
  @TestPropertySource(
      properties = {
        "camunda.processing.engine.mappings.input-mode=COMBINED",
        "camunda.processing.engine.mappings.input-comparison-mode=ORDERED",
      })
  class ComparisonMode {
    final BrokerBasedProperties brokerCfg;

    ComparisonMode(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetInputComparisonMode() {
      assertThat(brokerCfg.getExperimental().getEngine().getInputComparisonMode())
          .isEqualTo(EngineConfiguration.InputMappingMode.ORDERED);
    }
  }
}
