/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import com.google.common.collect.Lists;
import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ConfigurationValidator {

  public ConfigurationValidator() {}

  public void validate(final ConfigurationService configurationService) {
    configurationService.getEmailAuthenticationConfiguration().validate();
    validateWebhooks(configurationService);
  }

  private void validateWebhooks(final ConfigurationService configurationService) {
    final Map<String, WebhookConfiguration> webhookMap =
        configurationService.getConfiguredWebhooks();
    final List<String> webhooksWithoutPayload = Lists.newArrayList();
    final List<String> webhooksWithoutPlaceholder = Lists.newArrayList();
    final List<String> webhooksWithoutUrl = Lists.newArrayList();

    for (final Map.Entry<String, WebhookConfiguration> webhookConfigEntry : webhookMap.entrySet()) {
      final String defaultPayload = webhookConfigEntry.getValue().getDefaultPayload();
      final String url = webhookConfigEntry.getValue().getUrl();
      final boolean usesPlaceholderString =
          Arrays.stream(WebhookConfiguration.Placeholder.values())
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
      errorMsg =
          errorMsg
              + String.format(
                  "The following webhooks are missing their payload configuration: %s.%n",
                  webhooksWithoutPayload);
    }
    if (!webhooksWithoutUrl.isEmpty()) {
      errorMsg =
          errorMsg
              + String.format(
                  "The following webhooks are missing their URL configuration: %s.%n",
                  webhooksWithoutUrl);
    }
    if (!webhooksWithoutPlaceholder.isEmpty()) {
      errorMsg =
          errorMsg
              + String.format(
                  "At least one alert placeholder [%s] must be used in the following webhooks: %s",
                  Arrays.stream(WebhookConfiguration.Placeholder.values())
                      .map(WebhookConfiguration.Placeholder::getPlaceholderString)
                      .collect(Collectors.joining(", ")),
                  webhooksWithoutPlaceholder);
    }
    if (!errorMsg.isEmpty()) {
      throw new OptimizeConfigurationException(errorMsg);
    }
  }
}
