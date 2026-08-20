/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import io.camunda.optimize.service.util.configuration.cleanup.CleanupMode;
import io.camunda.optimize.service.util.configuration.cleanup.ProcessCleanupConfiguration;
import io.camunda.optimize.service.util.configuration.cleanup.ProcessDefinitionCleanupConfiguration;
import java.time.Period;
import org.junit.jupiter.api.Test;

public class CleanupConfigurationTest {

  @Test
  public void testGetCleanupConfigurationNormalizeCronExpression() {
    final CleanupConfiguration underTest = new CleanupConfiguration("* * * * *", Period.ZERO);

    assertThat(underTest.getCronTrigger()).isEqualTo("0 * * * * *");
  }

  @Test
  public void testGetProcessDefinitionCleanupConfigurationDefaultsForUnknownKey() {
    final CleanupMode defaultMode = CleanupMode.VARIABLES;
    final Period defaultTtl = Period.parse("P1M");
    final CleanupConfiguration underTest =
        new CleanupConfiguration(
            "* * * * *", defaultTtl, new ProcessCleanupConfiguration(true, defaultMode));

    final ProcessDefinitionCleanupConfiguration configForUnknownKey =
        underTest.getProcessDefinitionCleanupConfigurationForKey("unknownKey");

    assertThat(configForUnknownKey.getCleanupMode()).isEqualTo(defaultMode);
    assertThat(configForUnknownKey.getTtl()).isEqualTo(defaultTtl);
  }

  @Test
  public void testGetProcessDefinitionCleanupConfigurationCustomTtlForKey() {
    final CleanupMode defaultMode = CleanupMode.VARIABLES;
    final Period defaultTtl = Period.parse("P1M");
    final String key = "myKey";
    final CleanupConfiguration underTest =
        new CleanupConfiguration(
            "* * * * *", defaultTtl, new ProcessCleanupConfiguration(true, defaultMode));

    final Period customTtl = Period.parse("P1Y");
    underTest
        .getProcessDataCleanupConfiguration()
        .getProcessDefinitionSpecificConfiguration()
        .put(key, new ProcessDefinitionCleanupConfiguration(customTtl));

    final ProcessDefinitionCleanupConfiguration configForUnknownKey =
        underTest.getProcessDefinitionCleanupConfigurationForKey(key);

    assertThat(configForUnknownKey.getCleanupMode()).isEqualTo(defaultMode);
    assertThat(configForUnknownKey.getTtl()).isEqualTo(customTtl);
  }

  @Test
  public void testGetProcessDefinitionCleanupConfigurationCustomModeForKey() {
    final CleanupMode defaultMode = CleanupMode.VARIABLES;
    final Period defaultTtl = Period.parse("P1M");
    final String key = "myKey";
    final CleanupConfiguration underTest =
        new CleanupConfiguration(
            "* * * * *", defaultTtl, new ProcessCleanupConfiguration(true, defaultMode));

    final CleanupMode customMode = CleanupMode.ALL;
    underTest
        .getProcessDataCleanupConfiguration()
        .getProcessDefinitionSpecificConfiguration()
        .put(key, new ProcessDefinitionCleanupConfiguration(customMode));

    final ProcessDefinitionCleanupConfiguration configForUnknownKey =
        underTest.getProcessDefinitionCleanupConfigurationForKey(key);

    assertThat(configForUnknownKey.getCleanupMode()).isEqualTo(customMode);
    assertThat(configForUnknownKey.getTtl()).isEqualTo(defaultTtl);
  }
}
