/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service;

import io.camunda.optimize.dto.optimize.SettingsDto;
import io.camunda.optimize.service.db.reader.SettingsReader;
import io.camunda.optimize.service.db.writer.SettingsWriter;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import jakarta.ws.rs.ForbiddenException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class SettingsService {

  private final SettingsReader settingsReader;
  private final SettingsWriter settingsWriter;
  private final AbstractIdentityService identityService;
  private final ConfigurationService configurationService;

  public SettingsDto getSettings() {
    return settingsReader
        .getSettings()
        .orElse(
            SettingsDto.builder().sharingEnabled(configurationService.getSharingEnabled()).build());
  }

  public void setSettings(final SettingsDto settingsDto) {
    settingsDto.setLastModified(LocalDateUtil.getCurrentDateTime());
    // Make sure that the configuration service is in sync with the settings service
    settingsDto.getSharingEnabled().ifPresent(configurationService::setSharingEnabled);
    settingsWriter.upsertSettings(settingsDto);
  }

  public void setSettings(final String userId, final SettingsDto settingsDto) {
    validateUserAuthorizedToConfigureSettingsOrFail(userId);
    settingsDto.setLastModified(LocalDateUtil.getCurrentDateTime());
    settingsWriter.upsertSettings(settingsDto);
  }

  private void validateUserAuthorizedToConfigureSettingsOrFail(final String userId) {
    throw new ForbiddenException(
        String.format("User [%s] is not authorized to configure settings.", userId));
  }
}
