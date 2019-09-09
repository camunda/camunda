/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration.ui;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.ui_configuration.HeaderCustomizationDto;
import org.camunda.optimize.dto.optimize.query.ui_configuration.UIConfigurationDto;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.camunda.optimize.service.util.configuration.ConfigurationUtil.resolvePathToStream;


@Component
@Slf4j
public class UIConfigurationService implements ConfigurationReloadable {

  private ConfigurationService configurationService;

  // cached version
  private String logoAsBase64;

  @Autowired
  public UIConfigurationService(ConfigurationService configurationService) {
    this.configurationService = configurationService;
  }

  public UIConfigurationDto getUIConfiguration() {
    UIConfiguration uiConfiguration = configurationService.getUiConfiguration();
    HeaderCustomization headerCustomization = uiConfiguration.getHeader();

    HeaderCustomizationDto headerCustomizationDto = new HeaderCustomizationDto(
      headerCustomization.getTextColor(),
      headerCustomization.getBackgroundColor(),
      getLogoAsBase64()
    );
    UIConfigurationDto uiConfigurationDto = new UIConfigurationDto();
    uiConfigurationDto.setHeader(headerCustomizationDto);
  return uiConfigurationDto;
  }

  public String getLogoAsBase64() {
    String pathToLogoIcon = configurationService.getUiConfiguration().getHeader().getPathToLogoIcon();
    if (logoAsBase64 == null) {
      try (InputStream logo = resolvePathToStream(pathToLogoIcon)) {
        String mimeType = getContentType(pathToLogoIcon);
        byte[] fileContent = StreamUtils.copyToByteArray(logo);
        String encodedString = Base64.getEncoder().encodeToString(fileContent);
        this.logoAsBase64 = String.format("data:%s;base64,%s", mimeType, encodedString);
      } catch (Exception e) {
        String message = String.format(
          "Could not read logo icon from the given path [%s]! Please make sure you have set a valid path and that the" +
            " icon does exist for the given path.",
          pathToLogoIcon
        );
        throw new OptimizeConfigurationException(message, e);
      }
    }
    return this.logoAsBase64;
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    this.logoAsBase64 = null;
  }

  private String getContentType(final String pathToLogoIcon) throws IOException {
    Path path = new File(pathToLogoIcon).toPath();
    return Files.probeContentType(path);
  }
}
