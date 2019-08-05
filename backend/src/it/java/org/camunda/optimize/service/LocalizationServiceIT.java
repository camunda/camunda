/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

public class LocalizationServiceIT {
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain.outerRule(embeddedOptimizeRule);

  @Test
  public void failOnMissingFileForAvailableLocales() {
    //given
    embeddedOptimizeRule.getConfigurationService().getAvailableLocales().add("xyz");
    OptimizeConfigurationException configurationException = null;
    try {
      embeddedOptimizeRule.reloadConfiguration();
    } catch (OptimizeConfigurationException e) {
      configurationException = e;
    } finally {
      assertThat(configurationException, is(notNullValue()));
      assertThat(configurationException.getMessage(), containsString("xyz.json]"));
    }
  }

  @Test
  public void failOnInvalidJsonFileForAvailableLocales() {
    //given
    embeddedOptimizeRule.getConfigurationService().getAvailableLocales().add("invalid");
    OptimizeConfigurationException configurationException = null;
    try {
      embeddedOptimizeRule.reloadConfiguration();
    } catch (OptimizeConfigurationException e) {
      configurationException = e;
    } finally {
      assertThat(configurationException, is(notNullValue()));
      assertThat(
        configurationException.getMessage(),
        containsString(" not a valid JSON file [localization/invalid.json]")
      );
    }
  }

  @Test
  public void failOnFallbackLocaleNotPresentInAvailableLocales() {
    //given
    embeddedOptimizeRule.getConfigurationService().setFallbackLocale("xyz");
    OptimizeConfigurationException configurationException = null;
    try {
      embeddedOptimizeRule.reloadConfiguration();
    } catch (OptimizeConfigurationException e) {
      configurationException = e;
    } finally {
      assertThat(configurationException, is(notNullValue()));
      assertThat(configurationException.getMessage(), containsString("[xyz]"));
      assertThat(configurationException.getMessage(), containsString("[en, de]"));
    }
  }
}
