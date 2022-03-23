/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.SettingsResponseDto;
import org.camunda.optimize.service.util.configuration.TelemetryConfiguration;
import org.camunda.optimize.util.SuperUserType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.DateCreationFreezer.dateFreezer;

public class SettingsRestServiceIT extends AbstractIT {
  public final String GROUP_ID = "someGroup";

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

  @ParameterizedTest
  @EnumSource(SuperUserType.class)
  public void testCreateSettings(SuperUserType superUserType) {
    // given
    final OffsetDateTime now = dateFreezer().freezeDateAndReturn();
    final SettingsResponseDto newSettings = SettingsResponseDto.builder().metadataTelemetryEnabled(true).build();

    // when
    addSuperUserAndPermissions(superUserType);
    setSettings(newSettings);
    final SettingsResponseDto settings = getSettings();

    // then
    assertThat(settings.isMetadataTelemetryEnabled()).isEqualTo(newSettings.isMetadataTelemetryEnabled());
    assertThat(settings.getLastModifier()).isEqualTo(DEFAULT_USERNAME);
    assertThat(settings.getLastModified()).isEqualTo(now);
    assertThat(settings.isManuallyConfirmed()).isTrue();
  }

  @ParameterizedTest
  @EnumSource(SuperUserType.class)
  public void testUpdateExistingSettings(SuperUserType superUserType) {
    // given
    final OffsetDateTime now = dateFreezer().freezeDateAndReturn();
    final SettingsResponseDto existingSettings = SettingsResponseDto.builder().metadataTelemetryEnabled(true).build();
    addSuperUserAndPermissions(superUserType);
    setSettings(existingSettings);
    final SettingsResponseDto newSettings = SettingsResponseDto.builder().metadataTelemetryEnabled(false).build();
    setSettings(newSettings);
    final SettingsResponseDto settings = getSettings();

    // then
    assertThat(settings.isMetadataTelemetryEnabled()).isEqualTo(newSettings.isMetadataTelemetryEnabled());
    assertThat(settings.getLastModifier()).isEqualTo(DEFAULT_USERNAME);
    assertThat(settings.getLastModified()).isEqualTo(now);
    assertThat(settings.isManuallyConfirmed()).isTrue();
  }

  private void addSuperUserAndPermissions(final SuperUserType superUserType) {
    if (superUserType == SuperUserType.USER) {
      embeddedOptimizeExtension.getConfigurationService()
        .getAuthConfiguration().getSuperUserIds().add(DEFAULT_USERNAME);
    } else {
      authorizationClient.addUserAndGrantOptimizeAccess(DEFAULT_USERNAME);
      authorizationClient.createGroupAndAddUser(GROUP_ID, DEFAULT_USERNAME);
      authorizationClient.grantGroupOptimizeAccess(GROUP_ID);
      embeddedOptimizeExtension.getConfigurationService()
        .getAuthConfiguration().getSuperGroupIds().add(GROUP_ID);
    }
  }

  private void setSettings(final SettingsResponseDto newSettings) {
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
