/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.SettingsResponseDto;
import org.camunda.optimize.service.util.configuration.TelemetryConfiguration;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.DateCreationFreezer.dateFreezer;

public class SettingsRestServiceIT extends AbstractIT {

  @Test
  public void testGetSettings_defaultSettings() {
    // given
    final TelemetryConfiguration defaultTelemetryConfig =
      embeddedOptimizeExtension.getConfigurationService().getTelemetryConfiguration();
    final SettingsResponseDto expectedSettings = SettingsResponseDto.builder()
      .metadataTelemetryEnabled(defaultTelemetryConfig.isInitializeTelemetry())
      .build();

    // when
    final SettingsResponseDto settings = getSettings();

    // then
    assertThat(settings).isEqualTo(expectedSettings);
  }

  @Test
  public void testCreateSettings() {
    // given
    final OffsetDateTime now = dateFreezer().freezeDateAndReturn();
    final SettingsResponseDto newSettings = SettingsResponseDto.builder().metadataTelemetryEnabled(true).build();

    // when
    setSettings(newSettings);
    final SettingsResponseDto settings = getSettings();

    // then
    assertThat(settings.isMetadataTelemetryEnabled()).isEqualTo(newSettings.isMetadataTelemetryEnabled());
    assertThat(settings.getLastModifier()).isEqualTo(DEFAULT_USERNAME);
    assertThat(settings.getLastModified()).isEqualTo(now);
    assertThat(settings.isManuallyConfirmed()).isTrue();
  }

  @Test
  public void testUpdateExistingSettings() {
    // given
    final OffsetDateTime now = dateFreezer().freezeDateAndReturn();
    final SettingsResponseDto existingSettings = SettingsResponseDto.builder().metadataTelemetryEnabled(true).build();
    setSettings(existingSettings);

    final SettingsResponseDto newSettings = SettingsResponseDto.builder().metadataTelemetryEnabled(false).build();

    // when
    setSettings(newSettings);
    final SettingsResponseDto settings = getSettings();

    // then
    assertThat(settings.isMetadataTelemetryEnabled()).isEqualTo(newSettings.isMetadataTelemetryEnabled());
    assertThat(settings.getLastModifier()).isEqualTo(DEFAULT_USERNAME);
    assertThat(settings.getLastModified()).isEqualTo(now);
    assertThat(settings.isManuallyConfirmed()).isTrue();
  }

  private void setSettings(final SettingsResponseDto newSettings) {
    embeddedOptimizeExtension.getConfigurationService().getSuperUserIds().add(DEFAULT_USERNAME);

    embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .buildSetSettingsRequest(newSettings)
      .execute(SettingsResponseDto.class, Response.Status.NO_CONTENT.getStatusCode());
  }

  private SettingsResponseDto getSettings() {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetSettingsRequest()
      .execute(SettingsResponseDto.class, Response.Status.OK.getStatusCode());
  }
}
