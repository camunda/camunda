/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration;

import com.google.common.collect.Lists;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ReadContext;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.metadata.Version;
import org.camunda.optimize.util.SuppressionConstants;

import java.awt.Color;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;
import static org.camunda.optimize.service.util.configuration.ConfigurationParser.parseConfigFromLocations;
import static org.camunda.optimize.service.util.configuration.ConfigurationUtil.getLocationsAsInputStream;
import static org.camunda.optimize.service.util.configuration.ui.HeaderLogoRetriever.readLogoAsBase64;

@Slf4j
public class ConfigurationValidator {

  public static final String DOC_URL = MessageFormat.format(
    "https://docs.camunda.org/optimize/{0}.{1}", Version.VERSION_MAJOR, Version.VERSION_MINOR
  );
  private static final String[] DEFAULT_DEPRECATED_CONFIG_LOCATIONS = {"deprecated-config.yaml"};

  private final Map<String, String> deprecatedConfigKeys;

  public ConfigurationValidator() {
    this(null);
  }

  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  public ConfigurationValidator(String[] deprecatedConfigLocations) {
    final List<InputStream> deprecatedConfigStreams = getLocationsAsInputStream(
      deprecatedConfigLocations == null ? DEFAULT_DEPRECATED_CONFIG_LOCATIONS : deprecatedConfigLocations
    );

    this.deprecatedConfigKeys = (Map<String, String>) parseConfigFromLocations(deprecatedConfigStreams)
      .map(ReadContext::json).orElse(Collections.emptyMap());
  }

  public void validate(ConfigurationService configurationService) {
    validateNoDeprecatedConfigKeysUsed(configurationService.getConfigJsonContext());
    validateUIConfiguration(configurationService);
    configurationService.getEmailAuthenticationConfiguration().validate();
    validateWebhooks(configurationService);
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
        .flatMap(object -> object instanceof Collection && ((Collection<?>) object).isEmpty()
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

  private void validateWebhooks(final ConfigurationService configurationService) {
    final Map<String, WebhookConfiguration> webhookMap = configurationService.getConfiguredWebhooks();
    final List<String> webhooksWithoutPayload = Lists.newArrayList();
    final List<String> webhooksWithoutPlaceholder = Lists.newArrayList();
    final List<String> webhooksWithoutUrl = Lists.newArrayList();

    for (Map.Entry<String, WebhookConfiguration> webhookConfigEntry : webhookMap.entrySet()) {
      final String defaultPayload = webhookConfigEntry.getValue().getDefaultPayload();
      final String url = webhookConfigEntry.getValue().getUrl();
      final boolean usesPlaceholderString = Arrays.stream(WebhookConfiguration.Placeholder.values())
        .anyMatch(placeholder -> defaultPayload.contains(placeholder.getPlaceholderString()));

      if (url.isEmpty()) {
        webhooksWithoutUrl.add(webhookConfigEntry.getKey());
      }
      if (defaultPayload.isEmpty()) {
        webhooksWithoutPayload.add(webhookConfigEntry.getKey());
      } else if (!usesPlaceholderString) {
        webhooksWithoutPlaceholder.add(webhookConfigEntry.getKey());
      }
    }

    String errorMsg = "";
    if (!webhooksWithoutPayload.isEmpty()) {
      errorMsg = errorMsg + String.format(
        "The following webhooks are missing their payload configuration: %s.%n", webhooksWithoutPayload
      );
    }
    if (!webhooksWithoutUrl.isEmpty()) {
      errorMsg = errorMsg + String.format(
        "The following webhooks are missing their URL configuration: %s.%n", webhooksWithoutUrl
      );
    }
    if (!webhooksWithoutPlaceholder.isEmpty()) {
      errorMsg = errorMsg + String.format(
        "At least one alert placeholder [%s] must be used in the following webhooks: %s",
        Arrays.stream(WebhookConfiguration.Placeholder.values())
          .map(WebhookConfiguration.Placeholder::getPlaceholderString)
          .collect(Collectors.joining(", ")),
        webhooksWithoutPlaceholder
      );
    }
    if (!errorMsg.isEmpty()) {
      throw new OptimizeConfigurationException(errorMsg);
    }
  }
}
