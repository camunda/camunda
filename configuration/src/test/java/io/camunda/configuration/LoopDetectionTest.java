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
import io.camunda.zeebe.broker.system.configuration.engine.LoopDetectionCfg;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
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
public class LoopDetectionTest {

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.processing.engine.loop-detection.max-element-activation-count=500",
        "camunda.processing.engine.loop-detection.element-activation-retry-cooldown=50",
        "camunda.processing.engine.loop-detection.max-element-activation-count-by-type.SERVICE_TASK=300",
        "camunda.processing.engine.loop-detection.max-element-activation-count-by-type.USER_TASK=0",
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetLoopDetection() {
      final LoopDetectionCfg loopDetection =
          brokerCfg.getExperimental().getEngine().getLoopDetection();
      assertThat(loopDetection.getMaxElementActivationCount()).isEqualTo(500);
      assertThat(loopDetection.getElementActivationRetryCooldown()).isEqualTo(50);
      assertThat(loopDetection.getMaxElementActivationCountByType())
          .containsEntry(BpmnElementType.SERVICE_TASK, 300)
          .containsEntry(BpmnElementType.USER_TASK, 0);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.experimental.engine.loopDetection.maxElementActivationCount=500",
        "zeebe.broker.experimental.engine.loopDetection.elementActivationRetryCooldown=50",
        "zeebe.broker.experimental.engine.loopDetection.maxElementActivationCountByType.SERVICE_TASK=300",
        "zeebe.broker.experimental.engine.loopDetection.maxElementActivationCountByType.USER_TASK=0",
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetLoopDetectionFromLegacy() {
      final LoopDetectionCfg loopDetection =
          brokerCfg.getExperimental().getEngine().getLoopDetection();
      assertThat(loopDetection.getMaxElementActivationCount()).isEqualTo(500);
      assertThat(loopDetection.getElementActivationRetryCooldown()).isEqualTo(50);
      assertThat(loopDetection.getMaxElementActivationCountByType())
          .containsEntry(BpmnElementType.SERVICE_TASK, 300)
          .containsEntry(BpmnElementType.USER_TASK, 0);
    }
  }

  @Nested
  class WithDefaults {
    final BrokerBasedProperties brokerCfg;

    WithDefaults(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldResolveSameDefaultsAsEngineConfiguration() {
      // The unified-config LoopDetection layer duplicates these defaults from EngineConfiguration.
      // Assert the resolved values match the engine constants, to avoid drift.
      final LoopDetectionCfg loopDetection =
          brokerCfg.getExperimental().getEngine().getLoopDetection();
      assertThat(loopDetection.getMaxElementActivationCount())
          .isEqualTo(EngineConfiguration.DEFAULT_MAX_ELEMENT_ACTIVATION_COUNT);
      assertThat(loopDetection.getElementActivationRetryCooldown())
          .isEqualTo(EngineConfiguration.DEFAULT_ELEMENT_ACTIVATION_RETRY_COOLDOWN);
    }
  }
}
