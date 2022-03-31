/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.SettingsResponseDto;
import org.camunda.optimize.service.es.reader.SettingsReader;
import org.camunda.optimize.service.es.writer.SettingsWriter;
import org.camunda.optimize.service.identity.AbstractIdentityService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;

@AllArgsConstructor
@Component
@Slf4j
public class SettingsService {
  private final SettingsReader settingsReader;
  private final SettingsWriter settingsWriter;
  private final AbstractIdentityService identityService;
  private final ConfigurationService configurationService;

  public SettingsResponseDto getSettings() {
    return settingsReader.getSettings()
      .orElse(
        SettingsResponseDto.builder()
          .metadataTelemetryEnabled(configurationService.getTelemetryConfiguration().isInitializeTelemetry())
          .sharingEnabled(configurationService.getSharingEnabled())
          .build()
      );
  }

  public void setSettings(final String userId, final SettingsResponseDto settingsDto) {
    validateUserAuthorizedToConfigureSettingsOrFail(userId);
    settingsDto.setLastModified(LocalDateUtil.getCurrentDateTime());
    settingsDto.setLastModifier(userId);
    settingsWriter.upsertSettings(settingsDto);
  }

  public void setSettings(final SettingsResponseDto settingsDto) {
    settingsDto.setLastModified(LocalDateUtil.getCurrentDateTime());
    // Make sure that the configuration service is in sync with the settings service
    settingsDto.getSharingEnabled().ifPresent(configurationService::setSharingEnabled);
    settingsWriter.upsertSettings(settingsDto);
  }

  private void validateUserAuthorizedToConfigureSettingsOrFail(final String userId) {
    if (!identityService.isSuperUserIdentity(userId)) {
      throw new ForbiddenException(
        String.format("User [%s] is not authorized to configure settings.", userId)
      );
    }
  }
}
