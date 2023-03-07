/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.ByteStreams;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
import java.util.Map;
import java.util.Optional;

@AllArgsConstructor
@Component
@Slf4j
public class LocalizationService implements ConfigurationReloadable {

  public static final String LOCALIZATION_PATH = "localization/";

  private static final String API_ERRORS_FIELD = "apiErrors";
  private static final String MANAGEMENT_DASHBOARD_FIELD = "managementDashboard";
  private static final String INSTANT_DASHBOARD_FIELD = "instantDashboard";
  private static final String JSON_FILE_EXTENSION = "json";
  private static final String MARKDOWN_FILE_EXTENSION = "md";
  private static final String LOCALIZATION_FILE_PREFIX_WHATSNEW = "whatsnew";
  private static final String REPORT_FIELD = "report";
  private static final String MISSING_ASSIGNEE_FIELD = "missingAssignee";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ConfigurationService configurationService;

  @PostConstruct
  public void validateLocalizationSetup() {
    validateLocalizationFiles();
    validateMarkdownFiles();
    validateFallbackLocale();
  }

  public byte[] getLocalizationFileBytes(final String localeCode) {
    return getLocalizedJsonFile(localeCode);
  }

  public String getDefaultLocaleMessageForApiErrorCode(final String code) {
    try {
      return getMessageForApiErrorCode(configurationService.getFallbackLocale(), code);
    } catch (Exception e) {
      return String.format("Failed to localize error message for code [%s]", code);
    }
  }

  public String getDefaultLocaleMessageForMissingAssigneeLabel() {
    try {
      return getMessageForCode(configurationService.getFallbackLocale(), REPORT_FIELD, MISSING_ASSIGNEE_FIELD);
    } catch (Exception e) {
      return String.format(
        "Failed to localize label for missing assignee field with localization code: %s",
        MISSING_ASSIGNEE_FIELD
      );
    }
  }

  public byte[] getLocalizedWhatsNewMarkdown(final String localeCode) {
    return getLocalizedMarkdownFile(LOCALIZATION_FILE_PREFIX_WHATSNEW, localeCode);
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    validateLocalizationSetup();
  }

  public String getLocalizationForManagementDashboardCode(final String localeCode, final String dashboardCode) throws
                                                                                                               IOException {
    return getMessageForCode(localeCode, MANAGEMENT_DASHBOARD_FIELD, dashboardCode);
  }

  public String getLocalizationForInstantPreviewDashboardCode(final String localeCode, final String dashboardCode) throws IOException {
    return getMessageForCode(localeCode, INSTANT_DASHBOARD_FIELD, dashboardCode);
  }

  public String getLocalizationForManagementReportCode(final String localeCode, final String reportCode) throws IOException {
    return getNestedMessageForCode(localeCode, MANAGEMENT_DASHBOARD_FIELD, REPORT_FIELD, reportCode);
  }

  public String getLocalizationForInstantPreviewReportCode(final String localeCode, final String reportCode) throws IOException {
    return getNestedMessageForCode(localeCode, INSTANT_DASHBOARD_FIELD, REPORT_FIELD, reportCode);
  }

  public String validateAndReturnValidLocale(final String locale) {
    try {
      validateLocalizationFile(locale);
    } catch (Exception e) {
      final String fallbackLocale = configurationService.getFallbackLocale();
      log.error(
        "No valid localization files found for given locale [{}]. Defaulting to fallback locale [{}] instead.",
        locale,
        fallbackLocale,
        e
      );
      return fallbackLocale;
    }
    return locale;
  }

  private void validateLocalizationFiles() {
    configurationService.getAvailableLocales().forEach(this::validateLocalizationFile);
  }

  private void validateLocalizationFile(final String locale) {
    final String filePath = resolveFilePath(JSON_FILE_EXTENSION, locale);
    validateFileExists(filePath);
    validateJsonFile(filePath);
  }

  private void validateMarkdownFiles() {
    configurationService.getAvailableLocales().forEach(locale -> {
      final String filePath = resolveFilePath(LOCALIZATION_FILE_PREFIX_WHATSNEW, MARKDOWN_FILE_EXTENSION, locale);
      validateFileExists(filePath);
    });
  }

  private void validateFileExists(final String fileName) {
    final URL localizationFile = getClass().getClassLoader().getResource(fileName);
    if (localizationFile == null) {
      throw new OptimizeConfigurationException(
        String.format("File for configured availableLocale is not available [%s].", fileName)
      );
    }
  }

  private void validateFallbackLocale() {
    if (!configurationService.getAvailableLocales().contains(configurationService.getFallbackLocale())) {
      final String message = String.format(
        "The configured fallbackLocale [%s] is not present in availableLocales %s.",
        configurationService.getFallbackLocale(),
        configurationService.getAvailableLocales().toString()
      );
      throw new OptimizeConfigurationException(message);
    }
  }

  private void validateJsonFile(final String fileName) {
    final URL localizationFile = getClass().getClassLoader().getResource(fileName);
    try {
      objectMapper.readTree(localizationFile);
    } catch (IOException e) {
      throw new OptimizeConfigurationException(
        String.format("File for configured availableLocale is not a valid JSON file [%s].", fileName), e
      );
    }
  }

  private byte[] getLocalizedJsonFile(final String localeCode) {
    return getLocalizedFile(null, JSON_FILE_EXTENSION, localeCode);
  }

  private byte[] getLocalizedMarkdownFile(final String key, final String localeCode) {
    return getLocalizedFile(key, MARKDOWN_FILE_EXTENSION, localeCode);
  }

  private byte[] getLocalizedFile(final String filePrefix, final String fileExtension, final String localeCode) {
    final String resolvedLocaleCode = configurationService.getAvailableLocales().contains(localeCode)
      ? localeCode
      : configurationService.getFallbackLocale();

    return getFileBytes(resolveFilePath(filePrefix, fileExtension, resolvedLocaleCode));
  }

  private String resolveFilePath(final String fileExtension, final String localeCode) {
    return resolveFilePath(null, fileExtension, localeCode);
  }

  private String resolveFilePath(final String filePrefix, final String fileExtension, final String localeCode) {
    final String prefix = StringUtils.isNotBlank(filePrefix) ? filePrefix + "_" : "";
    return LOCALIZATION_PATH + prefix + localeCode + "." + fileExtension;
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

  private String getMessageForApiErrorCode(final String localeCode, final String errorCode) throws IOException {
    return getMessageForCode(localeCode, API_ERRORS_FIELD, errorCode);
  }

  @SuppressWarnings({"unchecked"})
  private String getMessageForCode(final String localeCode,
                                   final String categoryCode,
                                   final String messageCode) throws IOException {
    // @formatter:off
    final Map<String, Object> localisationMap = objectMapper.readValue(
      getLocalizedJsonFile(localeCode), new TypeReference<>() {}
    );
    // @formatter:on
    final Map<String, String> categoryMessageCodeMap = (Map<String, String>) localisationMap.get(categoryCode);
    return categoryMessageCodeMap.get(messageCode);
  }


  @SuppressWarnings({"unchecked"})
  private String getNestedMessageForCode(final String localeCode,
                                         final String categoryCode,
                                         final String subcategoryCode,
                                         final String messageCode) throws IOException {
    // @formatter:off
    final Map<String, Object> localisationMap = objectMapper.readValue(
      getLocalizedJsonFile(localeCode), new TypeReference<>() {}
    );
    // @formatter:on
    final Map<String, Map<String, String>> categoryMessageCodeMap = (Map<String, Map<String, String>>) localisationMap.get(
      categoryCode);
    return categoryMessageCodeMap.get(subcategoryCode).get(messageCode);
  }
}
