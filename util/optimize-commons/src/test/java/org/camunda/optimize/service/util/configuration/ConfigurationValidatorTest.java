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
import org.camunda.optimize.service.util.configuration.ui.HeaderCustomization;
import org.camunda.optimize.service.util.configuration.ui.UIConfiguration;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.optimize.service.util.configuration.ConfigurationValidator.createValidatorWithoutDeprecations;

public class ConfigurationValidatorTest {

  @RegisterExtension
  @Order(1)
  public EnvironmentVariablesExtension environmentVariablesExtension = new EnvironmentVariablesExtension();

  @RegisterExtension
  @Order(2)
  public SystemPropertiesExtension systemPropertiesExtension = new SystemPropertiesExtension();

  @Test
  public void testDeprecatedLeafKeyForConfigurationLeafKey() {
    // given
    ConfigurationService configurationService = createConfiguration("config-samples/config-alerting-leaf-key.yaml");
    String[] deprecatedLocations = {"deprecation-samples/deprecated-alerting-leaf-key.yaml"};
    ConfigurationValidator underTest = new ConfigurationValidator(deprecatedLocations);

    // when
    Map<String, String> deprecations = validateForAndReturnDeprecationsFailIfNone(configurationService, underTest);

    // then
    assertThat(deprecations)
      .hasSize(1)
      .containsEntry("alerting.email.username", generateExpectedDocUrl("/technical-guide/configuration/#email"));
  }

  @Test
  public void testDeprecatedParentKeyForConfigurationLeafKey() {
    // given
    ConfigurationService configurationService = createConfiguration("config-samples/config-alerting-leaf-key.yaml");
    String[] deprecatedLocations = {"deprecation-samples/deprecated-alerting-parent-key.yaml"};
    ConfigurationValidator underTest = new ConfigurationValidator(deprecatedLocations);

    // when
    Map<String, String> deprecations = validateForAndReturnDeprecationsFailIfNone(configurationService, underTest);

    // then
    assertThat(deprecations)
      .hasSize(1)
      .containsEntry("alerting.email", generateExpectedDocUrl("/technical-guide/configuration/#email"));
  }

  @Test
  public void testDeprecatedParentKeyForConfigurationParentKey_onlyOneDeprecationResult() {
    // given
    ConfigurationService configurationService = createConfiguration(
      "config-samples/config-alerting-parent-with-leafs-key.yaml");
    String[] deprecatedLocations = {"deprecation-samples/deprecated-alerting-parent-key.yaml"};
    ConfigurationValidator underTest = new ConfigurationValidator(deprecatedLocations);

    // when
    Map<String, String> deprecations = validateForAndReturnDeprecationsFailIfNone(configurationService, underTest);

    // then
    assertThat(deprecations)
      .hasSize(1)
      .containsEntry("alerting.email", generateExpectedDocUrl("/technical-guide/configuration/#email"));
  }

  @Test
  public void testAllDeprecationsForDistinctPathsArePresent() {
    // given
    ConfigurationService configurationService = createConfiguration(
      "config-samples/config-alerting-parent-with-leafs-key.yaml",
      "config-samples/config-somethingelse-parent-with-leafs-key.yaml"
    );
    String[] deprecatedLocations = {
      "deprecation-samples/deprecated-alerting-parent-key.yaml",
      "deprecation-samples/deprecated-somethingelse-parent-key.yaml"
    };
    ConfigurationValidator underTest = new ConfigurationValidator(deprecatedLocations);

    // when
    Map<String, String> deprecations = validateForAndReturnDeprecationsFailIfNone(configurationService, underTest);

    // then
    assertThat(deprecations)
      .hasSize(2)
      .containsEntry("alerting.email", generateExpectedDocUrl("/technical-guide/configuration/#email"))
      .containsEntry("somethingelse.email", generateExpectedDocUrl("/technical-guide/configuration/#somethingelse"));
  }

  @Test
  public void testDeprecatedArrayLeafKey() {
    // given
    ConfigurationService configurationService = createConfiguration("config-samples/config-tcpPort-leaf-key.yaml");
    String[] deprecatedLocations = {"deprecation-samples/deprecated-tcpPort-wildcard-leaf-key.yaml"};
    ConfigurationValidator underTest = new ConfigurationValidator(deprecatedLocations);

    // when
    Map<String, String> deprecations = validateForAndReturnDeprecationsFailIfNone(configurationService, underTest);

    // then
    assertThat(deprecations)
      .hasSize(1)
      .containsEntry(
        "es.connection.nodes[*].tcpPort",
        generateExpectedDocUrl("/technical-guide/setup/configuration/#connection-settings")
      );
  }

  @Test
  public void deprecatedAuthConfigs() {
    // given
    ConfigurationService configurationService =
      createConfiguration("config-samples/config-deprecated-auth-values.yaml");
    String[] deprecatedLocations = {"deprecated-config.yaml"};
    ConfigurationValidator underTest = new ConfigurationValidator(deprecatedLocations);

    // when
    Map<String, String> deprecations = validateForAndReturnDeprecationsFailIfNone(configurationService, underTest);

    // then
    assertThat(deprecations)
      .hasSize(1)
      .containsEntry(
        "auth.cookie.same-site",
        generateExpectedDocUrl("/technical-guide/setup/configuration/#security")
      );
  }

  @Test
  public void deprecatedAccessTokenConfigs() {
    // given
    ConfigurationService configurationService =
      createConfiguration("config-samples/config-deprecated-access-tokens.yaml");
    String[] deprecatedLocations = {"deprecated-config.yaml"};
    ConfigurationValidator underTest = new ConfigurationValidator(deprecatedLocations);

    // when
    Map<String, String> deprecations = validateForAndReturnDeprecationsFailIfNone(configurationService, underTest);

    // then
    assertThat(deprecations)
      .hasSize(2)
      .containsEntry(
        "eventBasedProcess.eventIngestion.accessToken",
        generateExpectedDocUrl("/technical-guide/setup/configuration/#public-api")
      )
      .containsEntry(
        "externalVariable.variableIngestion.accessToken",
        generateExpectedDocUrl("/technical-guide/setup/configuration/#public-api")
      );
  }

  @Test
  public void testNonDeprecatedArrayLeafKey_allFine() {
    // given
    String[] locations = {"config-samples/config-wo-tcpPort-leaf-key.yaml"};
    ConfigurationService configurationService = createConfiguration(locations);
    String[] deprecatedLocations = {"deprecation-samples/deprecated-tcpPort-wildcard-leaf-key.yaml"};
    ConfigurationValidator underTest = new ConfigurationValidator(deprecatedLocations);

    // when
    Optional<Map<String, String>> deprecations =
      validateForAndReturnDeprecations(configurationService, underTest);

    // then
    assertThat(deprecations).isNotPresent();
  }

  @Test
  public void testAllFineOnEmptyDeprecationConfig() {
    // given
    String[] locations = {"config-samples/config-alerting-leaf-key.yaml"};
    ConfigurationService configurationService = createConfiguration(locations);
    ConfigurationValidator underTest = new ConfigurationValidator(new String[]{});

    // when
    Optional<Map<String, String>> deprecations = validateForAndReturnDeprecations(configurationService, underTest);

    // then
    assertThat(deprecations).isNotPresent();
  }

  @Test
  public void canResolveRelativeSVGLogoPath() {
    // given
    ConfigurationService configurationService = createConfiguration();
    ConfigurationValidator underTest = new ConfigurationValidator(new String[]{});
    String relativePathToLogo = "logo/another_camunda_icon.svg";
    UIConfiguration uiConfiguration = createUIConfiguration(relativePathToLogo);
    configurationService.setUiConfiguration(uiConfiguration);

    // when
    underTest.validate(configurationService);

    // then no exception is thrown
  }

  @Test
  public void canAbsoluteLogoPath() {
    // given
    ConfigurationService configurationService = createConfiguration();
    ConfigurationValidator underTest = new ConfigurationValidator(new String[]{});
    String relativePathToLogo = "logo/another_camunda_icon.svg";
    String pathToLogoIcon = createAbsolutePath(relativePathToLogo);
    UIConfiguration uiConfiguration = createUIConfiguration(pathToLogoIcon);
    configurationService.setUiConfiguration(uiConfiguration);

    // when
    underTest.validate(configurationService);

    // then no exception is thrown
  }

  @Test
  public void logoSupportsJPEGFormat() {
    // given
    ConfigurationService configurationService = createConfiguration();
    ConfigurationValidator underTest = new ConfigurationValidator(new String[]{});
    String relativePathToLogo = "logo/camunda_icon.jpg";
    UIConfiguration uiConfiguration = createUIConfiguration(relativePathToLogo);
    configurationService.setUiConfiguration(uiConfiguration);

    // when
    underTest.validate(configurationService);

    // then no exception is thrown
  }

  @Test
  public void logoSupportsPNGFormat() {
    // given
    ConfigurationService configurationService = createConfiguration();
    ConfigurationValidator underTest = new ConfigurationValidator(new String[]{});
    String relativePathToLogo = "logo/camunda_icon.png";
    UIConfiguration uiConfiguration = createUIConfiguration(relativePathToLogo);
    configurationService.setUiConfiguration(uiConfiguration);

    // when
    underTest.validate(configurationService);

    // then no exception is thrown
  }

  @Test
  public void logoSupportsSVGWithoutDoctypeHeader() {
    // given
    ConfigurationService configurationService = createConfiguration();
    ConfigurationValidator underTest = new ConfigurationValidator(new String[]{});
    String relativePathToLogo = "logo/logo_without_doctype_header.svg";
    UIConfiguration uiConfiguration = createUIConfiguration(relativePathToLogo);
    configurationService.setUiConfiguration(uiConfiguration);

    // when
    underTest.validate(configurationService);

    // then no exception is thrown
  }

  @Test
  public void unsupportedMimeTypeOfLogoThrowsError() {
    // given
    ConfigurationService configurationService = createConfiguration();
    ConfigurationValidator underTest = new ConfigurationValidator(new String[]{});
    String relativePathToLogo = "logo/camunda_icon.invalid";
    UIConfiguration uiConfiguration = createUIConfiguration(relativePathToLogo);
    configurationService.setUiConfiguration(uiConfiguration);

    // then
    assertThatThrownBy(() -> underTest.validate(configurationService))
      .isInstanceOf(OptimizeConfigurationException.class);
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

  private UIConfiguration createUIConfiguration(final String pathToLogoIcon) {
    HeaderCustomization headerCustomization = new HeaderCustomization();
    headerCustomization.setPathToLogoIcon(pathToLogoIcon);
    headerCustomization.setBackgroundColor("#FFFFFF");
    UIConfiguration uiConfiguration = new UIConfiguration();
    uiConfiguration.setHeader(headerCustomization);
    return uiConfiguration;
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
      .useValidator(createValidatorWithoutDeprecations())
      .build();
  }

  private Map<String, String> validateForAndReturnDeprecationsFailIfNone(ConfigurationService configurationService,
                                                                         ConfigurationValidator underTest) {
    return validateForAndReturnDeprecations(configurationService, underTest)
      .orElseThrow(() -> new RuntimeException("Validation succeeded although it should have failed"));
  }

  private Optional<Map<String, String>> validateForAndReturnDeprecations(ConfigurationService configurationService,
                                                                         ConfigurationValidator validator) {
    try {
      validator.validate(configurationService);
      return Optional.empty();
    } catch (OptimizeConfigurationException e) {
      return Optional.of(e.getDeprecatedKeysAndDocumentationLink());
    }
  }

  private String generateExpectedDocUrl(String path) {
    return ConfigurationValidator.DOC_URL + path;
  }
}
