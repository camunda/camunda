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
import io.camunda.zeebe.broker.system.configuration.engine.ExpressionCfg;
import io.camunda.zeebe.engine.EngineConfiguration;
import java.time.Duration;
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
public class ExpressionTest {
  @Nested
  class WithNoneSet {
    final BrokerBasedProperties brokerCfg;

    WithNoneSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetDefaultExpressionTimeout() {
      assertThat(brokerCfg.getExperimental().getEngine().getExpression())
          .returns(
              EngineConfiguration.DEFAULT_EXPRESSION_EVALUATION_TIMEOUT, ExpressionCfg::getTimeout);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.expression.timeout=2s",
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetExpressionTimeout() {
      assertThat(brokerCfg.getExperimental().getEngine().getExpression())
          .returns(Duration.ofSeconds(2), ExpressionCfg::getTimeout);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.experimental.engine.expression.timeout=2s",
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetExpressionTimeoutFromLegacy() {
      assertThat(brokerCfg.getExperimental().getEngine().getExpression())
          .returns(Duration.ofSeconds(2), ExpressionCfg::getTimeout);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.expression.timeout=2s",
        // legacy
        "zeebe.broker.experimental.engine.expression.timeout=50s",
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetExpressionTimeoutFromNew() {
      assertThat(brokerCfg.getExperimental().getEngine().getExpression())
          .returns(Duration.ofSeconds(2), ExpressionCfg::getTimeout);
    }
  }
}
