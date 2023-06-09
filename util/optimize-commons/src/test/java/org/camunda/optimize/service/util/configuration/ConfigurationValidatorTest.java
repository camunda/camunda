/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration;

import org.apache.commons.lang3.ArrayUtils;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.util.configuration.extension.EnvironmentVariablesExtension;
import org.camunda.optimize.service.util.configuration.extension.SystemPropertiesExtension;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.optimize.service.util.configuration.ConfigurationValidator.DOC_URL;
import static org.camunda.optimize.service.util.configuration.ConfigurationValidator.createValidatorWithoutDeletions;

public class ConfigurationValidatorTest {

  @RegisterExtension
  @Order(1)
  public EnvironmentVariablesExtension environmentVariablesExtension = new EnvironmentVariablesExtension();

  @RegisterExtension
  @Order(2)
  public SystemPropertiesExtension systemPropertiesExtension = new SystemPropertiesExtension();

  @Test
  public void testDeletedLeafKeyForConfigurationLeafKey() {
    // given
    ConfigurationService configurationService = createConfiguration("config-samples/config-alerting-leaf-key.yaml");
    String[] deletedLocations = {"deletion-samples/deleted-alerting-leaf-key.yaml"};
    ConfigurationValidator underTest = new ConfigurationValidator(deletedLocations);

    // when
    Map<String, String> deletions = validateForAndReturnDeletionsFailIfNone(configurationService, underTest);

    // then
    assertThat(deletions)
      .hasSize(1)
      .containsEntry("alerting.email.username", DOC_URL);
  }

  @Test
  public void testDeletedParentKeyForConfigurationLeafKey() {
    // given
    ConfigurationService configurationService = createConfiguration("config-samples/config-alerting-leaf-key.yaml");
    String[] deletedLocations = {"deletion-samples/deleted-alerting-parent-key.yaml"};
    ConfigurationValidator underTest = new ConfigurationValidator(deletedLocations);

    // when
    Map<String, String> deletions = validateForAndReturnDeletionsFailIfNone(configurationService, underTest);

    // then
    assertThat(deletions)
      .hasSize(1)
      .containsEntry("alerting.email", DOC_URL);
  }

  @Test
  public void testDeletedParentKeyForConfigurationParentKey_onlyOneDeletionResult() {
    // given
    ConfigurationService configurationService = createConfiguration(
      "config-samples/config-alerting-parent-with-leafs-key.yaml");
    String[] deletedLocations = {"deletion-samples/deleted-alerting-parent-key.yaml"};
    ConfigurationValidator underTest = new ConfigurationValidator(deletedLocations);

    // when
    Map<String, String> deletions = validateForAndReturnDeletionsFailIfNone(configurationService, underTest);

    // then
    assertThat(deletions)
      .hasSize(1)
      .containsEntry("alerting.email", ConfigurationValidator.DOC_URL);
  }

  @Test
  public void testAllDeletionsForDistinctPathsArePresent() {
    // given
    ConfigurationService configurationService = createConfiguration(
      "config-samples/config-alerting-parent-with-leafs-key.yaml",
      "config-samples/config-somethingelse-parent-with-leafs-key.yaml"
    );
    String[] deletedLocations = {
      "deletion-samples/deleted-alerting-parent-key.yaml",
      "deletion-samples/deleted-somethingelse-parent-key.yaml"
    };
    ConfigurationValidator underTest = new ConfigurationValidator(deletedLocations);

    // when
    Map<String, String> deletions = validateForAndReturnDeletionsFailIfNone(configurationService, underTest);

    // then
    assertThat(deletions)
      .hasSize(2)
      .containsEntry("alerting.email", DOC_URL)
      .containsEntry("somethingelse.email", DOC_URL);
  }

  @Test
  public void testDeletedArrayLeafKey() {
    // given
    ConfigurationService configurationService = createConfiguration("config-samples/config-tcpPort-leaf-key.yaml");
    String[] deletedLocations = {"deletion-samples/deleted-tcpPort-wildcard-leaf-key.yaml"};
    ConfigurationValidator underTest = new ConfigurationValidator(deletedLocations);

    // when
    Map<String, String> deletions = validateForAndReturnDeletionsFailIfNone(configurationService, underTest);

    // then
    assertThat(deletions)
      .hasSize(1)
      .containsEntry("es.connection.nodes[*].tcpPort", DOC_URL);
  }

  @Test
  public void deletedAuthConfigs() {
    // given
    ConfigurationService configurationService =
      createConfiguration("config-samples/config-deleted-auth-values.yaml");
    String[] deletedLocations = {"deleted-config.yaml"};
    ConfigurationValidator underTest = new ConfigurationValidator(deletedLocations);

    // when
    Map<String, String> deletions = validateForAndReturnDeletionsFailIfNone(configurationService, underTest);

    // then
    assertThat(deletions)
      .hasSize(1)
      .containsEntry("auth.cookie.same-site", DOC_URL);
  }

  @Test
  public void deletedAccessTokenConfigs() {
    // given
    ConfigurationService configurationService =
      createConfiguration("config-samples/config-deleted-access-tokens.yaml");
    String[] deletedLocations = {"deleted-config.yaml"};
    ConfigurationValidator underTest = new ConfigurationValidator(deletedLocations);

    // when
    Map<String, String> deletions = validateForAndReturnDeletionsFailIfNone(configurationService, underTest);

    // then
    assertThat(deletions)
      .hasSize(2)
      .containsEntry("eventBasedProcess.eventIngestion.accessToken", DOC_URL)
      .containsEntry("externalVariable.variableIngestion.accessToken", DOC_URL);
  }

  @Test
  public void deletedUiHeaderConfigs() {
    // given
    ConfigurationService configurationService =
      createConfiguration("config-samples/config-deleted-ui-header.yaml");
    String[] deletedLocations = {"deleted-config.yaml"};
    ConfigurationValidator underTest = new ConfigurationValidator(deletedLocations);

    // when
    Map<String, String> deletions = validateForAndReturnDeletionsFailIfNone(configurationService, underTest);

    // then
    assertThat(deletions)
      .hasSize(3)
      .containsEntry("ui.header.textColor", DOC_URL)
      .containsEntry("ui.header.pathToLogoIcon", DOC_URL)
      .containsEntry("ui.header.backgroundColor", DOC_URL);
  }

  @Test
  public void testNonDeletedArrayLeafKey_allFine() {
    // given
    String[] locations = {"config-samples/config-wo-tcpPort-leaf-key.yaml"};
    ConfigurationService configurationService = createConfiguration(locations);
    String[] deletedLocations = {"deletion-samples/deleted-tcpPort-wildcard-leaf-key.yaml"};
    ConfigurationValidator underTest = new ConfigurationValidator(deletedLocations);

    // when
    Optional<Map<String, String>> deletions =
      validateForAndReturnDeletions(configurationService, underTest);

    // then
    assertThat(deletions).isNotPresent();
  }

  @Test
  public void testAllFineOnEmptyDeletionConfig() {
    // given
    String[] locations = {"config-samples/config-alerting-leaf-key.yaml"};
    ConfigurationService configurationService = createConfiguration(locations);
    ConfigurationValidator underTest = new ConfigurationValidator(new String[]{});

    // when
    Optional<Map<String, String>> deletions = validateForAndReturnDeletions(configurationService, underTest);

    // then
    assertThat(deletions).isNotPresent();
  }

  @Test
  public void missingWebhookUrlThrowsError() {
    // given
    ConfigurationService configurationService = createConfiguration();
    ConfigurationValidator underTest = new ConfigurationValidator(new String[]{});
    Map<String, WebhookConfiguration> webhooks = createSingleWebhookConfiguration(
      "myWeebhook", "", new HashMap<>(), "POST",
      WebhookConfiguration.Placeholder.ALERT_MESSAGE.getPlaceholderString()
    );
    configurationService.setConfiguredWebhooks(webhooks);

    // then
    assertThatThrownBy(() -> underTest.validate(configurationService))
      .isInstanceOf(OptimizeConfigurationException.class);
  }

  @Test
  public void missingWebhookPayloadThrowsError() {
    // given
    ConfigurationService configurationService = createConfiguration();
    ConfigurationValidator underTest = new ConfigurationValidator(new String[]{});
    Map<String, WebhookConfiguration> webhooks = createSingleWebhookConfiguration(
      "myWeebhook", "someurl", new HashMap<>(), "POST", ""
    );
    configurationService.setConfiguredWebhooks(webhooks);

    // then
    assertThatThrownBy(() -> underTest.validate(configurationService))
      .isInstanceOf(OptimizeConfigurationException.class);
  }

  @Test
  public void webhookPayloadWithOnePlaceholderIsAccepted() {
    // given
    ConfigurationService configurationService = createConfiguration();
    ConfigurationValidator underTest = new ConfigurationValidator(new String[]{});
    Map<String, WebhookConfiguration> webhooks = createSingleWebhookConfiguration(
      "myWeebhook", "someurl", new HashMap<>(), "POST", "{{ALERT_NAME}}"
    );
    configurationService.setConfiguredWebhooks(webhooks);

    // then
    underTest.validate(configurationService);
  }

  @Test
  public void webhookPayloadWithoutPlaceholderThrowsError() {
    // given
    ConfigurationService configurationService = createConfiguration();
    ConfigurationValidator underTest = new ConfigurationValidator(new String[]{});
    Map<String, WebhookConfiguration> webhooks = createSingleWebhookConfiguration(
      "myWeebhook", "someurl", new HashMap<>(), "POST", "aPayloadWithoutPlaceholder"
    );
    configurationService.setConfiguredWebhooks(webhooks);

    // then
    assertThatThrownBy(() -> underTest.validate(configurationService))
      .isInstanceOf(OptimizeConfigurationException.class)
      .hasMessage(
        "At least one alert placeholder [{{ALERT_MESSAGE}}, {{ALERT_NAME}}, {{ALERT_REPORT_LINK}}, " +
          "{{ALERT_CURRENT_VALUE}}, {{ALERT_THRESHOLD_VALUE}}, {{ALERT_THRESHOLD_OPERATOR}}, {{ALERT_TYPE}}, " +
          "{{ALERT_INTERVAL}}, {{ALERT_INTERVAL_UNIT}}] must be used in the following webhooks: [myWeebhook]");
  }

  private String createAbsolutePath(final String relativePathToLogo) {
    return Objects.requireNonNull(ConfigurationValidatorTest.class.getClassLoader().getResource(relativePathToLogo))
      .getPath();
  }

  private HashMap<String, WebhookConfiguration> createSingleWebhookConfiguration(final String name,
                                                                                 final String url,
                                                                                 final Map<String, String> headers,
                                                                                 final String httpMethod,
                                                                                 final String payload) {
    final HashMap<String, WebhookConfiguration> webhookMap = new HashMap<>();
    WebhookConfiguration webhookConfiguration = new WebhookConfiguration();
    webhookConfiguration.setUrl(url);
    webhookConfiguration.setHeaders(headers);
    webhookConfiguration.setHttpMethod(httpMethod);
    webhookConfiguration.setDefaultPayload(payload);
    webhookMap.put(name, webhookConfiguration);
    return webhookMap;
  }

  private ConfigurationService createConfiguration(final String... overwriteConfigFiles) {
    String[] locations = ArrayUtils.addAll(new String[]{"service-config.yaml"}, overwriteConfigFiles);
    return ConfigurationServiceBuilder.createConfiguration()
      .loadConfigurationFrom(locations)
      .useValidator(createValidatorWithoutDeletions())
      .build();
  }

  private Map<String, String> validateForAndReturnDeletionsFailIfNone(ConfigurationService configurationService,
                                                                      ConfigurationValidator underTest) {
    return validateForAndReturnDeletions(configurationService, underTest)
      .orElseThrow(() -> new RuntimeException("Validation succeeded although it should have failed"));
  }

  private Optional<Map<String, String>> validateForAndReturnDeletions(ConfigurationService configurationService,
                                                                      ConfigurationValidator validator) {
    try {
      validator.validate(configurationService);
      return Optional.empty();
    } catch (OptimizeConfigurationException e) {
      return Optional.of(e.getDeletedKeysAndDocumentationLink());
    }
  }

}
