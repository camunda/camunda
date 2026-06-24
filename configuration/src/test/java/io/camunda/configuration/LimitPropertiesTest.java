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
import io.camunda.zeebe.broker.system.configuration.backpressure.AIMDCfg;
import io.camunda.zeebe.broker.system.configuration.backpressure.FixedCfg;
import io.camunda.zeebe.broker.system.configuration.backpressure.Gradient2Cfg;
import io.camunda.zeebe.broker.system.configuration.backpressure.GradientCfg;
import io.camunda.zeebe.broker.system.configuration.backpressure.LegacyVegasCfg;
import io.camunda.zeebe.broker.system.configuration.backpressure.LimitCfg;
import io.camunda.zeebe.broker.system.configuration.backpressure.VegasCfg;
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
public class LimitPropertiesTest {

  private static final boolean EXPECTED_ENABLED = false;
  private static final boolean EXPECTED_WINDOWED = false;
  private static final String EXPECTED_ALGORITHM = "fixed";

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.processing.flow-control.request.enabled=" + EXPECTED_ENABLED,
        "camunda.processing.flow-control.request.windowed=" + EXPECTED_WINDOWED,
        "camunda.processing.flow-control.request.algorithm=" + EXPECTED_ALGORITHM,
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerBasedProperties;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void shouldSetLimitProperties() {
      assertThat(brokerBasedProperties.getFlowControl().getRequest())
          .returns(EXPECTED_ENABLED, LimitCfg::isEnabled)
          .returns(EXPECTED_WINDOWED, LimitCfg::useWindowed)
          .returns(EXPECTED_ALGORITHM.toUpperCase(), cfg -> cfg.getAlgorithm().name());
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.flowControl.request.enabled=" + EXPECTED_ENABLED,
        "zeebe.broker.flowControl.request.useWindowed=" + EXPECTED_WINDOWED,
        "zeebe.broker.flowControl.request.algorithm=" + EXPECTED_ALGORITHM,
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerBasedProperties;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void shouldSetLimitPropertiesFromLegacy() {
      assertThat(brokerBasedProperties.getFlowControl().getRequest())
          .returns(EXPECTED_ENABLED, LimitCfg::isEnabled)
          .returns(EXPECTED_WINDOWED, LimitCfg::useWindowed)
          .returns(EXPECTED_ALGORITHM.toUpperCase(), cfg -> cfg.getAlgorithm().name());
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.processing.flow-control.request.enabled=true",
        "camunda.processing.flow-control.request.algorithm=aimd",
        "camunda.processing.flow-control.request.aimd.request-timeout=500ms",
        "camunda.processing.flow-control.request.aimd.initial-limit=150",
        "camunda.processing.flow-control.request.aimd.min-limit=5",
        "camunda.processing.flow-control.request.aimd.max-limit=2000",
        "camunda.processing.flow-control.request.aimd.backoff-ratio=0.85",
      })
  class WithAimdAlgorithm {
    final BrokerBasedProperties brokerBasedProperties;

    WithAimdAlgorithm(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void shouldSetAimdProperties() {
      final var request = brokerBasedProperties.getFlowControl().getRequest();
      assertThat(request).isNotNull();
      assertThat(request.getAlgorithm().name()).isEqualTo("AIMD");
      assertThat(request.getAimd())
          .returns(Duration.ofMillis(500), AIMDCfg::getRequestTimeout)
          .returns(150, AIMDCfg::getInitialLimit)
          .returns(5, AIMDCfg::getMinLimit)
          .returns(2000, AIMDCfg::getMaxLimit)
          .returns(0.85, AIMDCfg::getBackoffRatio);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.flowControl.request.enabled=true",
        "zeebe.broker.flowControl.request.algorithm=aimd",
        "zeebe.broker.flowControl.request.aimd.requestTimeout=500ms",
        "zeebe.broker.flowControl.request.aimd.initialLimit=150",
        "zeebe.broker.flowControl.request.aimd.minLimit=5",
        "zeebe.broker.flowControl.request.aimd.maxLimit=2000",
        "zeebe.broker.flowControl.request.aimd.backoffRatio=0.85",
      })
  class WithAimdAlgorithmLegacy {
    final BrokerBasedProperties brokerBasedProperties;

    WithAimdAlgorithmLegacy(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void shouldSetAimdPropertiesFromLegacy() {
      final var request = brokerBasedProperties.getFlowControl().getRequest();
      assertThat(request).isNotNull();
      assertThat(request.getAlgorithm().name()).isEqualTo("AIMD");
      assertThat(request.getAimd())
          .returns(Duration.ofMillis(500), AIMDCfg::getRequestTimeout)
          .returns(150, AIMDCfg::getInitialLimit)
          .returns(5, AIMDCfg::getMinLimit)
          .returns(2000, AIMDCfg::getMaxLimit)
          .returns(0.85, AIMDCfg::getBackoffRatio);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.processing.flow-control.request.enabled=true",
        "camunda.processing.flow-control.request.algorithm=fixed",
        "camunda.processing.flow-control.request.fixed.limit=50",
      })
  class WithFixedAlgorithm {
    final BrokerBasedProperties brokerBasedProperties;

    WithFixedAlgorithm(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void shouldSetFixedProperties() {
      final var request = brokerBasedProperties.getFlowControl().getRequest();
      assertThat(request).isNotNull();
      assertThat(request.getAlgorithm().name()).isEqualTo("FIXED");
      assertThat(request.getFixed()).returns(50, FixedCfg::getLimit);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.flowControl.request.enabled=true",
        "zeebe.broker.flowControl.request.algorithm=fixed",
        "zeebe.broker.flowControl.request.fixed.limit=50",
      })
  class WithFixedAlgorithmLegacy {
    final BrokerBasedProperties brokerBasedProperties;

    WithFixedAlgorithmLegacy(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void shouldSetFixedPropertiesFromLegacy() {
      final var request = brokerBasedProperties.getFlowControl().getRequest();
      assertThat(request).isNotNull();
      assertThat(request.getAlgorithm().name()).isEqualTo("FIXED");
      assertThat(request.getFixed()).returns(50, FixedCfg::getLimit);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.processing.flow-control.request.enabled=true",
        "camunda.processing.flow-control.request.algorithm=vegas",
        "camunda.processing.flow-control.request.vegas.alpha=5",
        "camunda.processing.flow-control.request.vegas.beta=10",
        "camunda.processing.flow-control.request.vegas.initial-limit=30",
      })
  class WithVegasAlgorithm {
    final BrokerBasedProperties brokerBasedProperties;

    WithVegasAlgorithm(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void shouldSetVegasProperties() {
      final var request = brokerBasedProperties.getFlowControl().getRequest();
      assertThat(request).isNotNull();
      assertThat(request.getAlgorithm().name()).isEqualTo("VEGAS");
      assertThat(request.getVegas())
          .returns(5, VegasCfg::getAlpha)
          .returns(10, VegasCfg::getBeta)
          .returns(30, VegasCfg::getInitialLimit);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.flowControl.request.enabled=true",
        "zeebe.broker.flowControl.request.algorithm=vegas",
        "zeebe.broker.flowControl.request.vegas.alpha=5",
        "zeebe.broker.flowControl.request.vegas.beta=10",
        "zeebe.broker.flowControl.request.vegas.initialLimit=30",
      })
  class WithVegasAlgorithmLegacy {
    final BrokerBasedProperties brokerBasedProperties;

    WithVegasAlgorithmLegacy(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void shouldSetVegasPropertiesFromLegacy() {
      final var request = brokerBasedProperties.getFlowControl().getRequest();
      assertThat(request).isNotNull();
      assertThat(request.getAlgorithm().name()).isEqualTo("VEGAS");
      assertThat(request.getVegas())
          .returns(5, VegasCfg::getAlpha)
          .returns(10, VegasCfg::getBeta)
          .returns(30, VegasCfg::getInitialLimit);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.processing.flow-control.request.enabled=true",
        "camunda.processing.flow-control.request.algorithm=gradient",
        "camunda.processing.flow-control.request.gradient.min-limit=15",
        "camunda.processing.flow-control.request.gradient.initial-limit=25",
        "camunda.processing.flow-control.request.gradient.rtt-tolerance=3.0",
      })
  class WithGradientAlgorithm {
    final BrokerBasedProperties brokerBasedProperties;

    WithGradientAlgorithm(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void shouldSetGradientProperties() {
      final var request = brokerBasedProperties.getFlowControl().getRequest();
      assertThat(request).isNotNull();
      assertThat(request.getAlgorithm().name()).isEqualTo("GRADIENT");
      assertThat(request.getGradient())
          .returns(15, GradientCfg::getMinLimit)
          .returns(25, GradientCfg::getInitialLimit)
          .returns(3.0, GradientCfg::getRttTolerance);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.flowControl.request.enabled=true",
        "zeebe.broker.flowControl.request.algorithm=gradient",
        "zeebe.broker.flowControl.request.gradient.minLimit=15",
        "zeebe.broker.flowControl.request.gradient.initialLimit=25",
        "zeebe.broker.flowControl.request.gradient.rttTolerance=3.0",
      })
  class WithGradientAlgorithmLegacy {
    final BrokerBasedProperties brokerBasedProperties;

    WithGradientAlgorithmLegacy(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void shouldSetGradientPropertiesFromLegacy() {
      final var request = brokerBasedProperties.getFlowControl().getRequest();
      assertThat(request).isNotNull();
      assertThat(request.getAlgorithm().name()).isEqualTo("GRADIENT");
      assertThat(request.getGradient())
          .returns(15, GradientCfg::getMinLimit)
          .returns(25, GradientCfg::getInitialLimit)
          .returns(3.0, GradientCfg::getRttTolerance);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.processing.flow-control.request.enabled=true",
        "camunda.processing.flow-control.request.algorithm=gradient2",
        "camunda.processing.flow-control.request.gradient2.min-limit=12",
        "camunda.processing.flow-control.request.gradient2.initial-limit=22",
        "camunda.processing.flow-control.request.gradient2.rtt-tolerance=2.5",
        "camunda.processing.flow-control.request.gradient2.long-window=700",
      })
  class WithGradient2Algorithm {
    final BrokerBasedProperties brokerBasedProperties;

    WithGradient2Algorithm(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void shouldSetGradient2Properties() {
      final var request = brokerBasedProperties.getFlowControl().getRequest();
      assertThat(request).isNotNull();
      assertThat(request.getAlgorithm().name()).isEqualTo("GRADIENT2");
      assertThat(request.getGradient2())
          .returns(12, Gradient2Cfg::getMinLimit)
          .returns(22, Gradient2Cfg::getInitialLimit)
          .returns(2.5, Gradient2Cfg::getRttTolerance)
          .returns(700, Gradient2Cfg::getLongWindow);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.flowControl.request.enabled=true",
        "zeebe.broker.flowControl.request.algorithm=gradient2",
        "zeebe.broker.flowControl.request.gradient2.minLimit=12",
        "zeebe.broker.flowControl.request.gradient2.initialLimit=22",
        "zeebe.broker.flowControl.request.gradient2.rttTolerance=2.5",
        "zeebe.broker.flowControl.request.gradient2.longWindow=700",
      })
  class WithGradient2AlgorithmLegacy {
    final BrokerBasedProperties brokerBasedProperties;

    WithGradient2AlgorithmLegacy(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void shouldSetGradient2PropertiesFromLegacy() {
      final var request = brokerBasedProperties.getFlowControl().getRequest();
      assertThat(request).isNotNull();
      assertThat(request.getAlgorithm().name()).isEqualTo("GRADIENT2");
      assertThat(request.getGradient2())
          .returns(12, Gradient2Cfg::getMinLimit)
          .returns(22, Gradient2Cfg::getInitialLimit)
          .returns(2.5, Gradient2Cfg::getRttTolerance)
          .returns(700, Gradient2Cfg::getLongWindow);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.processing.flow-control.request.enabled=true",
        "camunda.processing.flow-control.request.algorithm=legacy-vegas",
        "camunda.processing.flow-control.request.legacy-vegas.initial-limit=2048",
        "camunda.processing.flow-control.request.legacy-vegas.max-concurrency=65536",
        "camunda.processing.flow-control.request.legacy-vegas.alpha-limit=0.8",
        "camunda.processing.flow-control.request.legacy-vegas.beta-limit=0.98",
      })
  class WithLegacyVegasAlgorithm {
    final BrokerBasedProperties brokerBasedProperties;

    WithLegacyVegasAlgorithm(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void shouldSetLegacyVegasProperties() {
      final var request = brokerBasedProperties.getFlowControl().getRequest();
      assertThat(request).isNotNull();
      assertThat(request.getAlgorithm().name()).isEqualTo("LEGACY_VEGAS");
      assertThat(request.getLegacyVegas())
          .returns(2048, LegacyVegasCfg::initialLimit)
          .returns(65536, LegacyVegasCfg::getMaxConcurrency)
          .returns(0.8, LegacyVegasCfg::alphaLimit)
          .returns(0.98, LegacyVegasCfg::betaLimit);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.flowControl.request.enabled=true",
        "zeebe.broker.flowControl.request.algorithm=legacy_vegas",
        "zeebe.broker.flowControl.request.legacyVegas.initialLimit=2048",
        "zeebe.broker.flowControl.request.legacyVegas.maxConcurrency=65536",
        "zeebe.broker.flowControl.request.legacyVegas.alphaLimit=0.8",
        "zeebe.broker.flowControl.request.legacyVegas.betaLimit=0.98",
      })
  class WithLegacyVegasAlgorithmLegacy {
    final BrokerBasedProperties brokerBasedProperties;

    WithLegacyVegasAlgorithmLegacy(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void shouldSetLegacyVegasPropertiesFromLegacy() {
      final var request = brokerBasedProperties.getFlowControl().getRequest();
      assertThat(request).isNotNull();
      assertThat(request.getAlgorithm().name()).isEqualTo("LEGACY_VEGAS");
      assertThat(request.getLegacyVegas())
          .returns(2048, LegacyVegasCfg::initialLimit)
          .returns(65536, LegacyVegasCfg::getMaxConcurrency)
          .returns(0.8, LegacyVegasCfg::alphaLimit)
          .returns(0.98, LegacyVegasCfg::betaLimit);
    }
  }

  @Nested
  class WithoutNewAndLegacySet {
    final BrokerBasedProperties brokerBasedProperties;

    WithoutNewAndLegacySet(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void shouldNotSetRequest() {
      assertThat(brokerBasedProperties.getFlowControl().getRequest()).isNull();
    }
  }
}
