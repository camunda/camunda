/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration.ui;

import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.camunda.optimize.dto.optimize.query.ui_configuration.HeaderCustomizationDto;
import org.camunda.optimize.dto.optimize.query.ui_configuration.UIConfigurationDto;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Set;

import static org.camunda.optimize.service.util.configuration.ConfigurationUtil.resolvePathToStream;


@Component
@Slf4j
public class UIConfigurationService implements ConfigurationReloadable {

  private ConfigurationService configurationService;
  // cached version
  private String logoAsBase64;

  /**
   * We only support logo images that have support for the html img tag.
   * According to developer mozilla, this are the valid mime types for html image tag:
   * https://developer.mozilla.org/en-US/docs/Web/Media/Formats/Image_types
   */
  private static final Set<String> VALID_HTML_IMG_TAG_MIME_TYPES = ImmutableSet.of(
    "image/apng",
    "image/bmp",
    "image/gif",
    "image/x-icon",
    "image/jpeg",
    "image/png",
    "image/svg+xml",
    "image/webp"
  );

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
        String mimeType = getMimeType(pathToLogoIcon);
        validateMimeTypeIsValidHTMLTagImage(mimeType);
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

  private void validateMimeTypeIsValidHTMLTagImage(String mimeType) {
    if (!VALID_HTML_IMG_TAG_MIME_TYPES.contains(mimeType)) {
      String message = String.format(
        "Unknown mime type for given logo. Found [%s], but supported types are %s.",
        mimeType, VALID_HTML_IMG_TAG_MIME_TYPES
      );
      throw new OptimizeConfigurationException(message);
    }
  }

  private String getMimeType(final String pathToLogoIcon) throws IOException {
    // we need to use this library to make the check
    // for the mime type independent from the OS.
    Tika tika = new Tika();
    try(InputStream stream = resolvePathToStream(pathToLogoIcon)) {
      return tika.detect(stream);
    }
  }
}
