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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Covers the all-lowercase {@code zeebe.broker.experimental.queryapi.enabled} spelling
 * specifically: {@code BrokerCfgTest} and {@code QueryApiCfg}'s own Spring Boot relaxed binding
 * already accept it as an alias of {@code queryApi.enabled}, but {@link
 * UnifiedConfigurationHelper}'s legacy-property detection does its own camelCase-to-kebab-case
 * conversion rather than Spring's canonical relaxed-binding rules, so this spelling needs to be
 * listed explicitly in {@link LegacyQueryApi}'s legacy property set or it is silently ignored.
 */
@SpringJUnitConfig({
  UnifiedConfiguration.class,
  BrokerBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
@ActiveProfiles("broker")
@TestPropertySource(properties = {"zeebe.broker.experimental.queryapi.enabled=true"})
class LegacyQueryApiTest {
  final BrokerBasedProperties brokerCfg;

  LegacyQueryApiTest(@Autowired final BrokerBasedProperties brokerCfg) {
    this.brokerCfg = brokerCfg;
  }

  @Test
  void shouldSetLegacyQueryApiEnabledFromLowercaseAlias() {
    assertThat(brokerCfg.getExperimental().getQueryApi().isEnabled()).isTrue();
  }
}
