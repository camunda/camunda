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
import org.camunda.optimize.util.SuppressionConstants;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;
import static org.camunda.optimize.service.util.configuration.ConfigurationParser.parseConfigFromLocations;
import static org.camunda.optimize.service.util.configuration.ConfigurationUtil.getLocationsAsInputStream;

@Slf4j
public class ConfigurationValidator {

  public static final String DOC_URL =
    "https://docs.camunda.io/optimize/next/self-managed/optimize-deployment/configuration/system-configuration/";
  private static final String[] DEFAULT_DELETED_CONFIG_LOCATIONS = {"deleted-config.yaml"};

  private final Map<String, String> deletedConfigKeys;

  public ConfigurationValidator() {
    this(null);
  }

  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  public ConfigurationValidator(String[] deletedConfigLocations) {
    final List<InputStream> deletedConfigStreams = getLocationsAsInputStream(
      deletedConfigLocations == null ? DEFAULT_DELETED_CONFIG_LOCATIONS : deletedConfigLocations
    );

    this.deletedConfigKeys = (Map<String, String>) parseConfigFromLocations(deletedConfigStreams)
      .map(ReadContext::json).orElse(Collections.emptyMap());
  }

  public void validate(ConfigurationService configurationService) {
    validateNoDeletedConfigKeysUsed(configurationService.getConfigJsonContext());
    configurationService.getEmailAuthenticationConfiguration().validate();
    validateWebhooks(configurationService);
  }

  public static ConfigurationValidator createValidatorWithoutDeletions() {
    return new ConfigurationValidator(new String[]{});
  }

  private void validateNoDeletedConfigKeysUsed(ReadContext configJsonContext) {
    final Configuration conf = Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS);
    final DocumentContext failsafeConfigurationJsonContext = JsonPath.using(conf)
      .parse((Object) configJsonContext.json());

    final Map<String, String> usedDeletionKeysWithNewDocumentationPath = deletedConfigKeys.entrySet()
      .stream()
      .filter(entry -> Optional.ofNullable(failsafeConfigurationJsonContext.read("$." + entry.getKey()))
        // in case of array structures we always a list as result, thus we need to check if it contains actual results
        .flatMap(object -> object instanceof Collection && ((Collection<?>) object).isEmpty()
          ? Optional.empty()
          : Optional.of(object)
        )
        .isPresent()
      )
      // We now set the URL to be the generic docs URL because we don't have consistent versioning across docs
      .peek(keyAndUrl -> keyAndUrl.setValue(DOC_URL))
      .peek(keyAndUrl -> log.error(
        "Deleted setting used with key {}, please see the updated documentation {}",
        keyAndUrl.getKey(), DOC_URL
      ))
      .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

    if (!usedDeletionKeysWithNewDocumentationPath.isEmpty()) {
      throw new OptimizeConfigurationException(
        "Configuration contains deleted entries", usedDeletionKeysWithNewDocumentationPath
      );
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
