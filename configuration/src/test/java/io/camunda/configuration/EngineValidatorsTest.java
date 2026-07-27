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
import io.camunda.zeebe.broker.system.configuration.engine.ValidatorsCfg;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  BrokerBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
@ActiveProfiles("broker")
public class EngineValidatorsTest {
  @Nested
  @TestPropertySource(
      properties = {"camunda.processing.engine.validators.results-output-max-size=1024"})
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetValidators() {
      assertThat(brokerCfg.getExperimental().getEngine().getValidators())
          .returns(1024, ValidatorsCfg::getResultsOutputMaxSize);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {"zeebe.broker.experimental.engine.validators.resultsOutputMaxSize=1024"})
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetValidatorsFromLegacy() {
      assertThat(brokerCfg.getExperimental().getEngine().getValidators())
          .returns(1024, ValidatorsCfg::getResultsOutputMaxSize);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.processing.engine.validators.results-output-max-size=1024",
        // legacy
        "zeebe.broker.experimental.engine.validators.resultsOutputMaxSize=10240",
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetValidatorsFromNew() {
      assertThat(brokerCfg.getExperimental().getEngine().getValidators())
          .returns(1024, ValidatorsCfg::getResultsOutputMaxSize);
    }
  }
}
