/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beans.BrokerBasedProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.util.unit.DataSize;

/**
 * The write-behind settings have no unified configuration property, so they must reach the broker
 * through the legacy {@code zeebe.broker.experimental.writeBehind.*} properties.
 */
@SpringJUnitConfig({
  UnifiedConfiguration.class,
  BrokerBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
@ActiveProfiles("broker")
@TestPropertySource(
    properties = {
      "zeebe.broker.experimental.writeBehind.enabled=true",
      "zeebe.broker.experimental.writeBehind.maxPendingSize=16MB",
      "zeebe.broker.experimental.writeBehind.cacheSize=128MB"
    })
class LegacyWriteBehindTest {
  final BrokerBasedProperties brokerCfg;

  LegacyWriteBehindTest(@Autowired final BrokerBasedProperties brokerCfg) {
    this.brokerCfg = brokerCfg;
  }

  @Test
  void shouldSetWriteBehindFromLegacyProperties() {
    final var writeBehind = brokerCfg.getExperimental().getWriteBehind();

    assertThat(writeBehind.isEnabled()).isTrue();
    assertThat(writeBehind.getMaxPendingSize()).isEqualTo(DataSize.ofMegabytes(16));
    assertThat(writeBehind.getCacheSize()).isEqualTo(DataSize.ofMegabytes(128));
  }
}
