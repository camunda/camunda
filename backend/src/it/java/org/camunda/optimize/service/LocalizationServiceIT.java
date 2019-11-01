/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class LocalizationServiceIT extends AbstractIT {

  @Test
  public void failOnMissingFileForAvailableLocales() {
    //given
    embeddedOptimizeExtension.getConfigurationService().getAvailableLocales().add("xyz");
    OptimizeConfigurationException configurationException = null;
    try {
      embeddedOptimizeExtension.reloadConfiguration();
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
    embeddedOptimizeExtension.getConfigurationService().getAvailableLocales().add("invalid");
    OptimizeConfigurationException configurationException = null;
    try {
      embeddedOptimizeExtension.reloadConfiguration();
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
    embeddedOptimizeExtension.getConfigurationService().setFallbackLocale("xyz");
    OptimizeConfigurationException configurationException = null;
    try {
      embeddedOptimizeExtension.reloadConfiguration();
    } catch (OptimizeConfigurationException e) {
      configurationException = e;
    } finally {
      assertThat(configurationException, is(notNullValue()));
      assertThat(configurationException.getMessage(), containsString("[xyz]"));
      assertThat(configurationException.getMessage(), containsString("[en, de]"));
    }
  }
}
