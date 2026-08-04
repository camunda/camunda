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
import java.time.Duration;
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
@TestPropertySource(
    properties = {
      "camunda.processing.engine.secrets.interval=10s",
      "camunda.processing.engine.secrets.retry-max-attempts=5",
      "camunda.processing.engine.secrets.retry-initial-delay=2s",
      "camunda.processing.engine.secrets.retry-max-delay=60s",
      "camunda.processing.engine.secrets.retry-backoff-factor=3"
    })
public class EngineSecretsTest {
  final BrokerBasedProperties brokerCfg;

  EngineSecretsTest(@Autowired final BrokerBasedProperties brokerCfg) {
    this.brokerCfg = brokerCfg;
  }

  @Test
  void shouldSetSecretResolution() {
    final var secretResolution = brokerCfg.getExperimental().getEngine().getSecretResolution();
    assertThat(secretResolution.getInterval()).isEqualTo(Duration.ofSeconds(10));
    assertThat(secretResolution.getRetryMaxAttempts()).isEqualTo(5);
    assertThat(secretResolution.getRetryInitialDelay()).isEqualTo(Duration.ofSeconds(2));
    assertThat(secretResolution.getRetryMaxDelay()).isEqualTo(Duration.ofSeconds(60));
    assertThat(secretResolution.getRetryBackoffFactor()).isEqualTo(3);
  }
}
