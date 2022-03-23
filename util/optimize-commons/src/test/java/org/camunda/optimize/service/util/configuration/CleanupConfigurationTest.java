/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration;

import org.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import org.camunda.optimize.service.util.configuration.cleanup.CleanupMode;
import org.camunda.optimize.service.util.configuration.cleanup.DecisionCleanupConfiguration;
import org.camunda.optimize.service.util.configuration.cleanup.DecisionDefinitionCleanupConfiguration;
import org.camunda.optimize.service.util.configuration.cleanup.ProcessCleanupConfiguration;
import org.camunda.optimize.service.util.configuration.cleanup.ProcessDefinitionCleanupConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Period;

import static org.assertj.core.api.Assertions.assertThat;

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
    final CleanupConfiguration underTest = new CleanupConfiguration(
      "* * * * *",
      defaultTtl,
      new ProcessCleanupConfiguration(true, defaultMode),
      new DecisionCleanupConfiguration()
    );

    final ProcessDefinitionCleanupConfiguration configForUnknownKey = underTest
      .getProcessDefinitionCleanupConfigurationForKey("unknownKey");

    assertThat(configForUnknownKey.getCleanupMode()).isEqualTo(defaultMode);
    assertThat(configForUnknownKey.getTtl()).isEqualTo(defaultTtl);
  }

  @Test
  public void testGetProcessDefinitionCleanupConfigurationCustomTtlForKey() {
    final CleanupMode defaultMode = CleanupMode.VARIABLES;
    final Period defaultTtl = Period.parse("P1M");
    final String key = "myKey";
    final CleanupConfiguration underTest = new CleanupConfiguration(
      "* * * * *",
      defaultTtl,
      new ProcessCleanupConfiguration(true, defaultMode),
      new DecisionCleanupConfiguration()
    );

    final Period customTtl = Period.parse("P1Y");
    underTest.getProcessDataCleanupConfiguration().getProcessDefinitionSpecificConfiguration().put(
      key, new ProcessDefinitionCleanupConfiguration(customTtl)
    );

    final ProcessDefinitionCleanupConfiguration configForUnknownKey = underTest
      .getProcessDefinitionCleanupConfigurationForKey(key);

    assertThat(configForUnknownKey.getCleanupMode()).isEqualTo(defaultMode);
    assertThat(configForUnknownKey.getTtl()).isEqualTo(customTtl);
  }

  @Test
  public void testGetProcessDefinitionCleanupConfigurationCustomModeForKey() {
    final CleanupMode defaultMode = CleanupMode.VARIABLES;
    final Period defaultTtl = Period.parse("P1M");
    final String key = "myKey";
    final CleanupConfiguration underTest = new CleanupConfiguration(
      "* * * * *",
      defaultTtl,
      new ProcessCleanupConfiguration(true, defaultMode),
      new DecisionCleanupConfiguration()
    );

    final CleanupMode customMode = CleanupMode.ALL;
    underTest.getProcessDataCleanupConfiguration().getProcessDefinitionSpecificConfiguration().put(
      key, new ProcessDefinitionCleanupConfiguration(customMode)
    );

    final ProcessDefinitionCleanupConfiguration configForUnknownKey = underTest
      .getProcessDefinitionCleanupConfigurationForKey(key);

    assertThat(configForUnknownKey.getCleanupMode()).isEqualTo(customMode);
    assertThat(configForUnknownKey.getTtl()).isEqualTo(defaultTtl);
  }

  @Test
  public void testGetDecisionDefinitionCleanupConfigurationDefaultsForUnknownKey() {
    final Period defaultTtl = Period.parse("P1M");
    final CleanupConfiguration underTest = new CleanupConfiguration(
      "* * * * *",
      defaultTtl,
      new ProcessCleanupConfiguration(true),
      new DecisionCleanupConfiguration()
    );

    final DecisionDefinitionCleanupConfiguration configForUnknownKey = underTest
      .getDecisionDefinitionCleanupConfigurationForKey("unknownKey");

    assertThat(configForUnknownKey.getTtl()).isEqualTo(defaultTtl);
  }

  @Test
  public void testGetDecisionDefinitionCleanupConfigurationCustomTtlForKey() {
    final Period defaultTtl = Period.parse("P1M");
    final String key = "myKey";
    final CleanupConfiguration underTest = new CleanupConfiguration(
      "* * * * *",
      defaultTtl,
      new ProcessCleanupConfiguration(),
      new DecisionCleanupConfiguration(true)
    );

    final Period customTtl = Period.parse("P1Y");
    underTest.getDecisionCleanupConfiguration()
      .getDecisionDefinitionSpecificConfiguration()
      .put(key, new DecisionDefinitionCleanupConfiguration(customTtl));

    final DecisionDefinitionCleanupConfiguration configForUnknownKey = underTest
      .getDecisionDefinitionCleanupConfigurationForKey(key);

    assertThat(configForUnknownKey.getTtl()).isEqualTo(customTtl);
  }

}
