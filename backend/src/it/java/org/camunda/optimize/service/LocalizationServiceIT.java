/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.camunda.optimize.AbstractPlatformIT;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(OPENSEARCH_PASSING)
public class LocalizationServiceIT extends AbstractPlatformIT {

  @Test
  public void failOnMissingFileForAvailableLocales() {
    // given
    embeddedOptimizeExtension.getConfigurationService().getAvailableLocales().add("xyz");

    // then
    final OptimizeConfigurationException exception =
        assertThrows(
            OptimizeConfigurationException.class,
            () -> embeddedOptimizeExtension.reloadConfiguration());
    assertThat(exception).isNotNull();
    assertThat(exception.getMessage()).contains("xyz.json]");
  }

  @Test
  public void failOnInvalidJsonFileForAvailableLocales() {
    // given
    embeddedOptimizeExtension.getConfigurationService().getAvailableLocales().add("invalid");

    // then
    final OptimizeConfigurationException exception =
        assertThrows(
            OptimizeConfigurationException.class,
            () -> embeddedOptimizeExtension.reloadConfiguration());
    assertThat(exception).isNotNull();
    assertThat(exception.getMessage())
        .contains(" not a valid JSON file [localization/invalid.json]");
  }

  @Test
  public void failOnFallbackLocaleNotPresentInAvailableLocales() {
    // given
    embeddedOptimizeExtension.getConfigurationService().setFallbackLocale("xyz");

    // then
    final OptimizeConfigurationException exception =
        assertThrows(
            OptimizeConfigurationException.class,
            () -> embeddedOptimizeExtension.reloadConfiguration());
    assertThat(exception).isNotNull();
    assertThat(exception.getMessage()).contains("[xyz]");
    assertThat(exception.getMessage()).contains("[en, de]");
  }
}
