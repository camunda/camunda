/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration;

import org.camunda.optimize.service.util.configuration.cleanup.CleanupMode;
import org.camunda.optimize.service.util.configuration.cleanup.DecisionDefinitionCleanupConfiguration;
import org.camunda.optimize.service.util.configuration.cleanup.EngineCleanupConfiguration;
import org.camunda.optimize.service.util.configuration.cleanup.OptimizeCleanupConfiguration;
import org.camunda.optimize.service.util.configuration.cleanup.ProcessDefinitionCleanupConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Period;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class OptimizeCleanupConfigurationTest {

  @Test
  public void testGetCleanupConfigurationNormalizeCronExpression() {
    final OptimizeCleanupConfiguration underTest = new OptimizeCleanupConfiguration("* * * * *");

    assertThat(underTest.getCronTrigger(), is("0 * * * * *"));
  }

  @Test
  public void testGetProcessDefinitionCleanupConfigurationDefaultsForUnknownKey() {
    final CleanupMode defaultMode = CleanupMode.VARIABLES;
    final Period defaultTtl = Period.parse("P1M");
    final OptimizeCleanupConfiguration underTest = new OptimizeCleanupConfiguration(
      "* * * * *", new EngineCleanupConfiguration(true, defaultTtl, defaultMode)
    );

    final ProcessDefinitionCleanupConfiguration configForUnknownKey = underTest
      .getEngineDataCleanupConfiguration()
      .getProcessDefinitionCleanupConfigurationForKey("unknownKey");

    assertThat(configForUnknownKey.getProcessDataCleanupMode(), is(defaultMode));
    assertThat(configForUnknownKey.getTtl(), is(defaultTtl));
  }

  @Test
  public void testGetProcessDefinitionCleanupConfigurationCustomTtlForKey() {
    final CleanupMode defaultMode = CleanupMode.VARIABLES;
    final Period defaultTtl = Period.parse("P1M");
    final String key = "myKey";
    final OptimizeCleanupConfiguration underTest = new OptimizeCleanupConfiguration(
      "* * * * *", new EngineCleanupConfiguration(true, defaultTtl, defaultMode)
    );

    final Period customTtl = Period.parse("P1Y");
    underTest.getEngineDataCleanupConfiguration().getProcessDefinitionSpecificConfiguration().put(
      key, new ProcessDefinitionCleanupConfiguration(customTtl)
    );

    final ProcessDefinitionCleanupConfiguration configForUnknownKey = underTest
      .getEngineDataCleanupConfiguration()
      .getProcessDefinitionCleanupConfigurationForKey(key);

    assertThat(configForUnknownKey.getProcessDataCleanupMode(), is(defaultMode));
    assertThat(configForUnknownKey.getTtl(), is(customTtl));
  }

  @Test
  public void testGetProcessDefinitionCleanupConfigurationCustomModeForKey() {
    final CleanupMode defaultMode = CleanupMode.VARIABLES;
    final Period defaultTtl = Period.parse("P1M");
    final String key = "myKey";
    final OptimizeCleanupConfiguration underTest = new OptimizeCleanupConfiguration(
      "* * * * *", new EngineCleanupConfiguration(true, defaultTtl, defaultMode)
    );

    final CleanupMode customMode = CleanupMode.ALL;
    underTest.getEngineDataCleanupConfiguration().getProcessDefinitionSpecificConfiguration().put(
      key, new ProcessDefinitionCleanupConfiguration(customMode)
    );

    final ProcessDefinitionCleanupConfiguration configForUnknownKey = underTest
      .getEngineDataCleanupConfiguration()
      .getProcessDefinitionCleanupConfigurationForKey(key);

    assertThat(configForUnknownKey.getProcessDataCleanupMode(), is(customMode));
    assertThat(configForUnknownKey.getTtl(), is(defaultTtl));
  }

  @Test
  public void testGetDecisionDefinitionCleanupConfigurationDefaultsForUnknownKey() {
    final Period defaultTtl = Period.parse("P1M");
    final OptimizeCleanupConfiguration underTest = new OptimizeCleanupConfiguration(
      "* * * * *", new EngineCleanupConfiguration(true, defaultTtl, CleanupMode.ALL)
    );

    final DecisionDefinitionCleanupConfiguration configForUnknownKey = underTest
      .getEngineDataCleanupConfiguration()
      .getDecisionDefinitionCleanupConfigurationForKey("unknownKey");

    assertThat(configForUnknownKey.getTtl(), is(defaultTtl));
  }

  @Test
  public void testGetDecisionDefinitionCleanupConfigurationCustomTtlForKey() {
    final Period defaultTtl = Period.parse("P1M");
    final String key = "myKey";
    final OptimizeCleanupConfiguration underTest = new OptimizeCleanupConfiguration(
      "* * * * *", new EngineCleanupConfiguration(true, defaultTtl, CleanupMode.ALL)
    );

    final Period customTtl = Period.parse("P1Y");
    underTest.getEngineDataCleanupConfiguration()
      .getDecisionDefinitionSpecificConfiguration()
      .put(key, new DecisionDefinitionCleanupConfiguration(customTtl));

    final DecisionDefinitionCleanupConfiguration configForUnknownKey = underTest.getEngineDataCleanupConfiguration()
      .getDecisionDefinitionCleanupConfigurationForKey(key);

    assertThat(configForUnknownKey.getTtl(), is(customTtl));
  }

}
