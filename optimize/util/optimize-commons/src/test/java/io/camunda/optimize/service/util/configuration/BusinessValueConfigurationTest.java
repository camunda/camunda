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

/**
 * A kill switch is worth nothing if its value never reaches the code that reads it, and that
 * failure is silent: the flag would simply appear to do nothing. These assert the wiring itself.
 */
class BusinessValueConfigurationTest {

  @Test
  void shouldBindTheOverviewComputeFlagFromTheShippedConfiguration() {
    // given the configuration Optimize actually ships
    final ConfigurationService configurationService =
        ConfigurationServiceBuilder.createDefaultConfiguration();

    // when the business-value configuration is read
    final BusinessValueConfiguration businessValue =
        configurationService.getBusinessValueConfiguration();

    // then the value comes from the file rather than from the null fallback. Asserting on
    // isOverviewComputeEnabled() alone would pass even if the key never bound, since it reads null
    // as enabled — so the raw value is what proves the wiring.
    assertThat(businessValue.getOverviewComputeEnabled())
        .as("businessValue.overviewComputeEnabled must bind from service-config.yaml")
        .isNotNull()
        .isTrue();
    assertThat(businessValue.isOverviewComputeEnabled()).isTrue();
  }

  @Test
  void shouldTreatAbsentConfigurationAsEnabled() {
    // given a configuration where the flag was never set
    final BusinessValueConfiguration businessValue = new BusinessValueConfiguration();

    // when the effective value is read
    // then the sweep runs: failing closed would silently disable the feature on a configuration
    // mistake, which is the opposite of what a kill switch is for
    assertThat(businessValue.getOverviewComputeEnabled()).isNull();
    assertThat(businessValue.isOverviewComputeEnabled()).isTrue();
  }

  @Test
  void shouldHonourAnExplicitFalse() {
    // given the switch is thrown
    final BusinessValueConfiguration businessValue = new BusinessValueConfiguration();
    businessValue.setOverviewComputeEnabled(false);

    // when the effective value is read
    // then it is off
    assertThat(businessValue.isOverviewComputeEnabled()).isFalse();
  }
}
