/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.SettingsDto;
import org.camunda.optimize.dto.optimize.query.ui_configuration.UIConfigurationDto;
import org.camunda.optimize.service.util.configuration.TelemetryConfiguration;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

public class SettingsRestServiceIT extends AbstractIT {

  @Test
  public void testGetSettings_defaultSettings() {
    // given
    final TelemetryConfiguration defaultTelemetryConfig =
      embeddedOptimizeExtension.getConfigurationService().getTelemetryConfiguration();
    final SettingsDto expectedSettings = SettingsDto.builder()
      .metadataTelemetryEnabled(defaultTelemetryConfig.isInitializeTelemetry())
      .build();

    // when
    final SettingsDto settings = getSettings();

    // then
    assertThat(settings).isEqualTo(expectedSettings);
  }

  @Test
  public void testCreateSettings() {
    // given
    final SettingsDto newSettings = SettingsDto.builder().metadataTelemetryEnabled(true).build();

    // when
    setSettings(newSettings);
    final SettingsDto settings = getSettings();

    // then
    assertThat(settings).isEqualTo(newSettings);
  }

  @Test
  public void testUpdateExistingSettings() {
    // given
    final SettingsDto existingSettings = SettingsDto.builder().metadataTelemetryEnabled(true).build();
    setSettings(existingSettings);

    final SettingsDto newSettings = SettingsDto.builder().metadataTelemetryEnabled(false).build();

    // when
    setSettings(newSettings);
    final SettingsDto settings = getSettings();

    // then
    assertThat(settings).isEqualTo(newSettings);
  }

  private void setSettings(final SettingsDto newSettings) {
    embeddedOptimizeExtension.getConfigurationService().getSuperUserIds().add(DEFAULT_USERNAME);

    embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .buildSetSettingsRequest(newSettings)
      .execute(SettingsDto.class, Response.Status.NO_CONTENT.getStatusCode());
  }

  private SettingsDto getSettings() {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetSettingsRequest()
      .execute(SettingsDto.class, Response.Status.OK.getStatusCode());
  }
}
