/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgenticPhase2ConfigurationTest {

  @Test
  void shouldBindTheFlagAsDisabledFromTheShippedConfiguration() {
    // given the configuration Optimize ships
    final ConfigurationService configurationService =
        ConfigurationServiceBuilder.createDefaultConfiguration();

    // when
    final AgenticPhase2Configuration phase2 = configurationService.getAgenticPhase2Configuration();

    // then the raw value proves the key binds from service-config.yaml, since a missing key
    // would also read as disabled
    assertThat(phase2.getEnabled()).isNotNull().isFalse();
    assertThat(phase2.isEnabled()).isFalse();
  }

  @Test
  void shouldTreatAbsentConfigurationAsDisabled() {
    // given the flag was never set
    final AgenticPhase2Configuration phase2 = new AgenticPhase2Configuration();

    // when / then an unreleased feature stays off
    assertThat(phase2.isEnabled()).isFalse();
  }

  @Test
  void shouldHonourAnExplicitTrue() {
    // given
    final AgenticPhase2Configuration phase2 = new AgenticPhase2Configuration();
    phase2.setEnabled(true);

    // when / then
    assertThat(phase2.isEnabled()).isTrue();
  }
}
