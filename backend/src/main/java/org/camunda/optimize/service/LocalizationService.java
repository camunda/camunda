/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.ByteStreams;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Optional;

@AllArgsConstructor
@Component
@Slf4j
public class LocalizationService implements ConfigurationReloadable {

  public static final String LOCALIZATION_PATH = "localization/";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ConfigurationService configurationService;

  public byte[] getLocalizationFileBytes(final String localeCode) {
    final String resolvedLocaleCode = configurationService.getAvailableLocales().contains(localeCode)
      ? localeCode
      : configurationService.getFallbackLocale();

    return getFileBytes(resolveLocaleFilePath(resolvedLocaleCode));
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    validateLocaleConfigurationFiles();
  }

  @PostConstruct
  public void validateLocaleConfigurationFiles() {
    configurationService.getAvailableLocales().forEach(locale -> {
      final String localeFileName = resolveLocaleFilePath(locale);
      final URL localizationFile = getClass().getClassLoader().getResource(localeFileName);
      if (localizationFile == null) {
        throw new OptimizeConfigurationException(
          String.format("File for configured availableLocale is not available [%s].", localeFileName)
        );
      } else {
        try {
          objectMapper.readTree(localizationFile);
        } catch (IOException e) {
          throw new OptimizeConfigurationException(
            String.format("File for configured availableLocale is not a valid JSON file [%s].", localeFileName),
            e
          );
        }
      }
    });

    if (!configurationService.getAvailableLocales().contains(configurationService.getFallbackLocale())) {
      final String message = String.format(
        "The configured fallbackLocale [%s] is not present in availableLocales %s.",
        configurationService.getFallbackLocale(),
        configurationService.getAvailableLocales().toString()
      );
      throw new OptimizeConfigurationException(message);
    }
  }

  private String resolveLocaleFilePath(final String localeCode) {
    return LOCALIZATION_PATH + localeCode + ".json";
  }

  private byte[] getFileBytes(final String localeFileName) {
    Optional<byte[]> result = Optional.empty();

    try (final InputStream localeFileAsStream = getClass().getClassLoader().getResourceAsStream(localeFileName)) {
      result = Optional.ofNullable(localeFileAsStream)
        .flatMap(inputStream -> {
          try {
            return Optional.of(ByteStreams.toByteArray(inputStream));
          } catch (final IOException e) {
            log.error("Failed reading localization file {} from classpath", localeFileName, e);
            return Optional.empty();
          }
        });
    } catch (final IOException e) {
      log.debug("Failed closing stream of locale file {}.", localeFileName, e);
    }

    return result.orElseThrow(() -> new OptimizeRuntimeException("Could not load localization file: " + localeFileName));
  }
}
