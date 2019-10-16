/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class LocalizationServiceIT {

  @RegisterExtension
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();

  @Test
  public void failOnMissingFileForAvailableLocales() {
    //given
    embeddedOptimizeExtensionRule.getConfigurationService().getAvailableLocales().add("xyz");
    OptimizeConfigurationException configurationException = null;
    try {
      embeddedOptimizeExtensionRule.reloadConfiguration();
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
    embeddedOptimizeExtensionRule.getConfigurationService().getAvailableLocales().add("invalid");
    OptimizeConfigurationException configurationException = null;
    try {
      embeddedOptimizeExtensionRule.reloadConfiguration();
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
    embeddedOptimizeExtensionRule.getConfigurationService().setFallbackLocale("xyz");
    OptimizeConfigurationException configurationException = null;
    try {
      embeddedOptimizeExtensionRule.reloadConfiguration();
    } catch (OptimizeConfigurationException e) {
      configurationException = e;
    } finally {
      assertThat(configurationException, is(notNullValue()));
      assertThat(configurationException.getMessage(), containsString("[xyz]"));
      assertThat(configurationException.getMessage(), containsString("[en, de]"));
    }
  }
}
