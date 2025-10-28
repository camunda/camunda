/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({UnifiedConfiguration.class, UnifiedConfigurationHelper.class})
public class MonitoringTest {

  @Nested
  @TestPropertySource(properties = {"camunda.monitoring.jfr=true"})
  class WithOnlyUnifiedConfigSet {
    final UnifiedConfiguration unifiedConfiguration;

    WithOnlyUnifiedConfigSet(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldSetJfr() {
      assertThat(unifiedConfiguration.getCamunda().getMonitoring().isJfr()).isTrue();
    }
  }

  @Nested
  @TestPropertySource(properties = {"camunda.flags.jfr.metrics=true"})
  class WithOnlyLegacySet {
    final UnifiedConfiguration unifiedConfiguration;

    WithOnlyLegacySet(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldSetJfrFromLegacy() {
      assertThat(unifiedConfiguration.getCamunda().getMonitoring().isJfr()).isTrue();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.monitoring.jfr=true",
        // legacy
        "camunda.flags.jfr.metrics=false"
      })
  class WithNewAndLegacySet {
    final UnifiedConfiguration unifiedConfiguration;

    WithNewAndLegacySet(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldSetJfrFromNew() {
      assertThat(unifiedConfiguration.getCamunda().getMonitoring().isJfr()).isTrue();
    }
  }
}
