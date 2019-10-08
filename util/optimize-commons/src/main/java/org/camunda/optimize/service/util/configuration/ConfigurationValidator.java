/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ReadContext;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.metadata.Version;

import java.awt.*;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static java.util.stream.Collectors.toMap;
import static org.camunda.optimize.service.util.configuration.ConfigurationParser.parseConfigFromLocations;
import static org.camunda.optimize.service.util.configuration.ConfigurationUtil.getLocationsAsInputStream;
import static org.camunda.optimize.service.util.configuration.ui.HeaderLogoRetriever.readLogoAsBase64;

@Slf4j
public class ConfigurationValidator {

  public static final String DOC_URL = MessageFormat.format(
    "https://docs.camunda.org/optimize/{0}.{1}",
    Version.VERSION_MAJOR,
    Version.VERSION_MINOR
  );
  private static final String[] DEFAULT_DEPRECATED_CONFIG_LOCATIONS = {"deprecated-config.yaml"};
  private Map<String, String> deprecatedConfigKeys;

  public ConfigurationValidator() {
    this(null);
  }

  public ConfigurationValidator(String[] deprecatedConfigLocations) {
    List<InputStream> deprecatedConfigStreams =
      getLocationsAsInputStream(deprecatedConfigLocations == null ? DEFAULT_DEPRECATED_CONFIG_LOCATIONS :
                                  deprecatedConfigLocations);
    this.deprecatedConfigKeys =
      (Map<String, String>) parseConfigFromLocations(
        deprecatedConfigStreams
      ).map(ReadContext::json).orElse(Collections.emptyMap());
  }

  public void validate(ConfigurationService configurationService) {
    validateNoDeprecatedConfigKeysUsed(configurationService.getConfigJsonContext());
    validateUIConfiguration(configurationService);
  }

  public static ConfigurationValidator createValidatorWithoutDeprecations() {
    return new ConfigurationValidator(new String[]{});
  }

  private void validateNoDeprecatedConfigKeysUsed(ReadContext configJsonContext) {
    final Configuration conf = Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS);
    final DocumentContext failsafeConfigurationJsonContext = JsonPath.using(conf)
      .parse((Object) configJsonContext.json());

    final Map<String, String> usedDeprecationKeysWithNewDocumentationPath = deprecatedConfigKeys.entrySet().stream()
      .filter(entry -> Optional.ofNullable(failsafeConfigurationJsonContext.read("$." + entry.getKey()))
        // in case of array structures we always a list as result, thus we need to check if it contains actual results
        .flatMap(object -> object instanceof Collection && ((Collection) object).size() == 0
          ? Optional.empty()
          : Optional.of(object)
        )
        .isPresent()
      )
      .peek(keyAndPath -> keyAndPath.setValue(DOC_URL + keyAndPath.getValue()))
      .peek(keyAndUrl -> log.error(
        "Deprecated setting used with key {}, please checkout the updated documentation {}",
        keyAndUrl.getKey(), keyAndUrl.getValue()
      ))
      .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

    if (!usedDeprecationKeysWithNewDocumentationPath.isEmpty()) {
      throw new OptimizeConfigurationException(
        "Configuration contains deprecated entries", usedDeprecationKeysWithNewDocumentationPath
      );
    }
  }

  private void validateUIConfiguration(final ConfigurationService configurationService) {
    // validate that icon can be read from the given logo icon path
    final String pathToLogoIcon = configurationService.getUiConfiguration().getHeader().getPathToLogoIcon();
    Objects.requireNonNull(readLogoAsBase64(pathToLogoIcon));
    validateColorCode(configurationService);
  }

  private void validateColorCode(final ConfigurationService configurationService) {
    String backgroundColor = configurationService.getUiConfiguration().getHeader().getBackgroundColor();
    try {
      Color.decode(backgroundColor);
    } catch (NumberFormatException e) {
      String message = String.format(
        "The stated background color [%s] for the header customization is not valid. Please configure valid " +
          "hexadecimal encoded color.", backgroundColor);
      throw new OptimizeConfigurationException(message, e);
    }
  }
}
