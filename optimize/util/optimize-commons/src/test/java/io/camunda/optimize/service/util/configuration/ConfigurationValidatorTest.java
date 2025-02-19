/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import io.camunda.optimize.service.util.configuration.extension.EnvironmentVariablesExtension;
import io.camunda.optimize.service.util.configuration.extension.SystemPropertiesExtension;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class ConfigurationValidatorTest {

  @RegisterExtension
  @Order(1)
  public EnvironmentVariablesExtension environmentVariablesExtension =
      new EnvironmentVariablesExtension();

  @RegisterExtension
  @Order(2)
  public SystemPropertiesExtension systemPropertiesExtension = new SystemPropertiesExtension();

  @Test
  public void missingWebhookUrlThrowsError() {
    // given
    final ConfigurationService configurationService = createConfiguration();
    final ConfigurationValidator underTest = new ConfigurationValidator();
    final Map<String, WebhookConfiguration> webhooks =
        createSingleWebhookConfiguration(
            "myWeebhook",
            "",
            new HashMap<>(),
            "POST",
            WebhookConfiguration.Placeholder.ALERT_MESSAGE.getPlaceholderString());
    configurationService.setConfiguredWebhooks(webhooks);

    // then
    assertThatThrownBy(() -> underTest.validate(configurationService))
        .isInstanceOf(OptimizeConfigurationException.class);
  }

  @Test
  public void missingWebhookPayloadThrowsError() {
    // given
    final ConfigurationService configurationService = createConfiguration();
    final ConfigurationValidator underTest = new ConfigurationValidator();
    final Map<String, WebhookConfiguration> webhooks =
        createSingleWebhookConfiguration("myWeebhook", "someurl", new HashMap<>(), "POST", "");
    configurationService.setConfiguredWebhooks(webhooks);

    // then
    assertThatThrownBy(() -> underTest.validate(configurationService))
        .isInstanceOf(OptimizeConfigurationException.class);
  }

  @Test
  public void webhookPayloadWithOnePlaceholderIsAccepted() {
    // given
    final ConfigurationService configurationService = createConfiguration();
    final ConfigurationValidator underTest = new ConfigurationValidator();
    final Map<String, WebhookConfiguration> webhooks =
        createSingleWebhookConfiguration(
            "myWeebhook", "someurl", new HashMap<>(), "POST", "{{ALERT_NAME}}");
    configurationService.setConfiguredWebhooks(webhooks);

    // then
    underTest.validate(configurationService);
  }

  @Test
  public void webhookPayloadWithoutPlaceholderThrowsError() {
    // given
    final ConfigurationService configurationService = createConfiguration();
    final ConfigurationValidator underTest = new ConfigurationValidator();
    final Map<String, WebhookConfiguration> webhooks =
        createSingleWebhookConfiguration(
            "myWebhook", "someurl", new HashMap<>(), "POST", "aPayloadWithoutPlaceholder");
    configurationService.setConfiguredWebhooks(webhooks);

    // then
    assertThatThrownBy(() -> underTest.validate(configurationService))
        .isInstanceOf(OptimizeConfigurationException.class)
        .hasMessage(
            "At least one alert placeholder [{{ALERT_MESSAGE}}, {{ALERT_NAME}}, {{ALERT_REPORT_LINK}}, "
                + "{{ALERT_CURRENT_VALUE}}, {{ALERT_THRESHOLD_VALUE}}, {{ALERT_THRESHOLD_OPERATOR}}, {{ALERT_TYPE}}, "
                + "{{ALERT_INTERVAL}}, {{ALERT_INTERVAL_UNIT}}] must be used in the following webhooks: [myWebhook]");
  }

  private HashMap<String, WebhookConfiguration> createSingleWebhookConfiguration(
      final String name,
      final String url,
      final Map<String, String> headers,
      final String httpMethod,
      final String payload) {
    final HashMap<String, WebhookConfiguration> webhookMap = new HashMap<>();
    final WebhookConfiguration webhookConfiguration = new WebhookConfiguration();
    webhookConfiguration.setUrl(url);
    webhookConfiguration.setHeaders(headers);
    webhookConfiguration.setHttpMethod(httpMethod);
    webhookConfiguration.setDefaultPayload(payload);
    webhookMap.put(name, webhookConfiguration);
    return webhookMap;
  }

  private ConfigurationService createConfiguration(final String... overwriteConfigFiles) {
    final String[] locations =
        ArrayUtils.addAll(new String[] {"service-config.yaml"}, overwriteConfigFiles);
    return ConfigurationServiceBuilder.createConfiguration()
        .loadConfigurationFrom(locations)
        .useValidator(new ConfigurationValidator())
        .build();
  }
}
