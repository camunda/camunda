/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalizationServiceIT extends AbstractIT {

  @Test
  public void failOnMissingFileForAvailableLocales() {
    // given
    embeddedOptimizeExtension.getConfigurationService().getAvailableLocales().add("xyz");
    OptimizeConfigurationException configurationException = null;
    try {
      embeddedOptimizeExtension.reloadConfiguration();
    } catch (OptimizeConfigurationException e) {
      configurationException = e;
    } finally {
      assertThat(configurationException).isNotNull();
      assertThat(configurationException.getMessage()).contains("xyz.json]");
    }
  }

  @Test
  public void failOnInvalidJsonFileForAvailableLocales() {
    // given
    embeddedOptimizeExtension.getConfigurationService().getAvailableLocales().add("invalid");
    OptimizeConfigurationException configurationException = null;
    try {
      embeddedOptimizeExtension.reloadConfiguration();
    } catch (OptimizeConfigurationException e) {
      configurationException = e;
    } finally {
      assertThat(configurationException).isNotNull();
      assertThat(configurationException.getMessage())
        .contains(" not a valid JSON file [localization/invalid.json]");
    }
  }

  @Test
  public void failOnFallbackLocaleNotPresentInAvailableLocales() {
    // given
    embeddedOptimizeExtension.getConfigurationService().setFallbackLocale("xyz");
    OptimizeConfigurationException configurationException = null;
    try {
      embeddedOptimizeExtension.reloadConfiguration();
    } catch (OptimizeConfigurationException e) {
      configurationException = e;
    } finally {
      assertThat(configurationException).isNotNull();
      assertThat(configurationException.getMessage()).contains("[xyz]");
      assertThat(configurationException.getMessage()).contains("[en, de]");
    }
  }
}
