/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration;

import org.apache.commons.lang3.ArrayUtils;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.util.configuration.ui.HeaderCustomization;
import org.camunda.optimize.service.util.configuration.ui.UIConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.camunda.optimize.service.util.configuration.ConfigurationValidator.createValidatorWithoutDeprecations;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;


public class ConfigurationValidatorTest {

  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

  @Rule
  public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties();

  @Test
  public void testDeprecatedLeafKeyForConfigurationLeafKey() {
    // given
    ConfigurationService configurationService = createConfiguration("config-samples/config-alerting-leaf-key.yaml");
    String[] deprecatedLocations = {"deprecation-samples/deprecated-alerting-leaf-key.yaml"};
    ConfigurationValidator underTest = new ConfigurationValidator(deprecatedLocations);

    // when
    Map<String, String> deprecations = validateForAndReturnDeprecationsFailIfNone(configurationService, underTest);

    // then
    assertThat(deprecations.size(), is(1));
    assertThat(
      deprecations.get("alerting.email.username"),
      is(generateExpectedDocUrl("/technical-guide/configuration/#email"))
    );
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
    assertThat(deprecations.size(), is(1));
    assertThat(deprecations.get("alerting.email"), is(generateExpectedDocUrl("/technical-guide/configuration/#email")));
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
    assertThat(deprecations.size(), is(1));
    assertThat(deprecations.get("alerting.email"), is(generateExpectedDocUrl("/technical-guide/configuration/#email")));
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
    assertThat(deprecations.size(), is(2));
    assertThat(deprecations.get("alerting.email"), is(generateExpectedDocUrl("/technical-guide/configuration/#email")));
    assertThat(
      deprecations.get("somethingelse.email"),
      is(generateExpectedDocUrl("/technical-guide/configuration/#somethingelse"))
    );

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
    assertThat(deprecations.size(), is(1));
    assertThat(
      deprecations.get("es.connection.nodes[*].tcpPort"),
      is(generateExpectedDocUrl("/technical-guide/setup/configuration/#connection-settings"))
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
    assertThat(deprecations.isPresent(), is(false));
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
    assertThat(deprecations.isPresent(), is(false));
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

  @Test(expected = OptimizeConfigurationException.class)
  public void unsupportedMimeTypeOfLogoThrowsError() {
    // given
    ConfigurationService configurationService = createConfiguration();
    ConfigurationValidator underTest = new ConfigurationValidator(new String[]{});
    String relativePathToLogo = "logo/camunda_icon.invalid";
    UIConfiguration uiConfiguration = createUIConfiguration(relativePathToLogo);
    configurationService.setUiConfiguration(uiConfiguration);

    // when
    underTest.validate(configurationService);

    // then an exception is thrown
  }

  private String createAbsolutePath(final String relativePathToLogo) {
    return Objects.requireNonNull(ConfigurationValidatorTest.class.getClassLoader().getResource(relativePathToLogo))
      .getPath();
  }

  private UIConfiguration createUIConfiguration(final String pathToLogoIcon) {
    HeaderCustomization headerCustomization = new HeaderCustomization();
    headerCustomization.setPathToLogoIcon(
      pathToLogoIcon);
    headerCustomization.setBackgroundColor("#FFFFFF");
    UIConfiguration uiConfiguration = new UIConfiguration();
    uiConfiguration.setHeader(headerCustomization);
    return uiConfiguration;
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
