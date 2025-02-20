/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import static io.camunda.optimize.service.util.configuration.ConfigurationValidator.DOC_URL;
import static io.camunda.optimize.service.util.configuration.ConfigurationValidator.createValidatorWithoutDeletions;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import io.camunda.optimize.service.util.configuration.extension.EnvironmentVariablesExtension;
import io.camunda.optimize.service.util.configuration.extension.SystemPropertiesExtension;
import java.util.Map;
import java.util.Optional;
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
  public void testDeletedLeafKeyForConfigurationLeafKey() {
    // given
    final ConfigurationService configurationService =
        createConfiguration("config-samples/config-alerting-leaf-key.yaml");
    final String[] deletedLocations = {"deletion-samples/deleted-alerting-leaf-key.yaml"};
    final ConfigurationValidator underTest = new ConfigurationValidator(deletedLocations);

    // when
    final Map<String, String> deletions =
        validateForAndReturnDeletionsFailIfNone(configurationService, underTest);

    // then
    assertThat(deletions).hasSize(1).containsEntry("alerting.email.username", DOC_URL);
  }

  @Test
  public void testDeletedParentKeyForConfigurationLeafKey() {
    // given
    final ConfigurationService configurationService =
        createConfiguration("config-samples/config-alerting-leaf-key.yaml");
    final String[] deletedLocations = {"deletion-samples/deleted-alerting-parent-key.yaml"};
    final ConfigurationValidator underTest = new ConfigurationValidator(deletedLocations);

    // when
    final Map<String, String> deletions =
        validateForAndReturnDeletionsFailIfNone(configurationService, underTest);

    // then
    assertThat(deletions).hasSize(1).containsEntry("alerting.email", DOC_URL);
  }

  @Test
  public void testDeletedParentKeyForConfigurationParentKeyOnlyOneDeletionResult() {
    // given
    final ConfigurationService configurationService =
        createConfiguration("config-samples/config-alerting-parent-with-leafs-key.yaml");
    final String[] deletedLocations = {"deletion-samples/deleted-alerting-parent-key.yaml"};
    final ConfigurationValidator underTest = new ConfigurationValidator(deletedLocations);

    // when
    final Map<String, String> deletions =
        validateForAndReturnDeletionsFailIfNone(configurationService, underTest);

    // then
    assertThat(deletions)
        .hasSize(1)
        .containsEntry("alerting.email", ConfigurationValidator.DOC_URL);
  }

  @Test
  public void testAllDeletionsForDistinctPathsArePresent() {
    // given
    final ConfigurationService configurationService =
        createConfiguration(
            "config-samples/config-alerting-parent-with-leafs-key.yaml",
            "config-samples/config-somethingelse-parent-with-leafs-key.yaml");
    final String[] deletedLocations = {
      "deletion-samples/deleted-alerting-parent-key.yaml",
      "deletion-samples/deleted-somethingelse-parent-key.yaml"
    };
    final ConfigurationValidator underTest = new ConfigurationValidator(deletedLocations);

    // when
    final Map<String, String> deletions =
        validateForAndReturnDeletionsFailIfNone(configurationService, underTest);

    // then
    assertThat(deletions)
        .hasSize(2)
        .containsEntry("alerting.email", DOC_URL)
        .containsEntry("somethingelse.email", DOC_URL);
  }

  @Test
  public void testDeletedArrayLeafKey() {
    // given
    final ConfigurationService configurationService =
        createConfiguration("config-samples/config-tcpPort-leaf-key.yaml");
    final String[] deletedLocations = {"deletion-samples/deleted-tcpPort-wildcard-leaf-key.yaml"};
    final ConfigurationValidator underTest = new ConfigurationValidator(deletedLocations);

    // when
    final Map<String, String> deletions =
        validateForAndReturnDeletionsFailIfNone(configurationService, underTest);

    // then
    assertThat(deletions).hasSize(1).containsEntry("es.connection.nodes[*].tcpPort", DOC_URL);
  }

  @Test
  public void deletedAuthConfigs() {
    // given
    final ConfigurationService configurationService =
        createConfiguration("config-samples/config-deleted-auth-values.yaml");
    final String[] deletedLocations = {"deleted-config.yaml"};
    final ConfigurationValidator underTest = new ConfigurationValidator(deletedLocations);

    // when
    final Map<String, String> deletions =
        validateForAndReturnDeletionsFailIfNone(configurationService, underTest);

    // then
    assertThat(deletions).hasSize(1).containsEntry("auth.cookie.same-site", DOC_URL);
  }

  @Test
  public void deletedAccessTokenConfigs() {
    // given
    final ConfigurationService configurationService =
        createConfiguration("config-samples/config-deleted-access-tokens.yaml");
    final String[] deletedLocations = {"deleted-config.yaml"};
    final ConfigurationValidator underTest = new ConfigurationValidator(deletedLocations);

    // when
    final Map<String, String> deletions =
        validateForAndReturnDeletionsFailIfNone(configurationService, underTest);

    // then
    assertThat(deletions)
        .hasSize(2)
        .containsEntry("eventBasedProcess.eventIngestion.accessToken", DOC_URL)
        .containsEntry("externalVariable.variableIngestion.accessToken", DOC_URL);
  }

  @Test
  public void deletedUiHeaderConfigs() {
    // given
    final ConfigurationService configurationService =
        createConfiguration("config-samples/config-deleted-ui-header.yaml");
    final String[] deletedLocations = {"deleted-config.yaml"};
    final ConfigurationValidator underTest = new ConfigurationValidator(deletedLocations);

    // when
    final Map<String, String> deletions =
        validateForAndReturnDeletionsFailIfNone(configurationService, underTest);

    // then
    assertThat(deletions)
        .hasSize(3)
        .containsEntry("ui.header.textColor", DOC_URL)
        .containsEntry("ui.header.pathToLogoIcon", DOC_URL)
        .containsEntry("ui.header.backgroundColor", DOC_URL);
  }

  @Test
  public void testNonDeletedArrayLeafKeyAllFine() {
    // given
    final String[] locations = {"config-samples/config-wo-tcpPort-leaf-key.yaml"};
    final ConfigurationService configurationService = createConfiguration(locations);
    final String[] deletedLocations = {"deletion-samples/deleted-tcpPort-wildcard-leaf-key.yaml"};
    final ConfigurationValidator underTest = new ConfigurationValidator(deletedLocations);

    // when
    final Optional<Map<String, String>> deletions =
        validateForAndReturnDeletions(configurationService, underTest);

    // then
    assertThat(deletions).isNotPresent();
  }

  @Test
  public void testAllFineOnEmptyDeletionConfig() {
    // given
    final String[] locations = {"config-samples/config-alerting-leaf-key.yaml"};
    final ConfigurationService configurationService = createConfiguration(locations);
    final ConfigurationValidator underTest = new ConfigurationValidator(new String[] {});

    // when
    final Optional<Map<String, String>> deletions =
        validateForAndReturnDeletions(configurationService, underTest);

    // then
    assertThat(deletions).isNotPresent();
  }

  private ConfigurationService createConfiguration(final String... overwriteConfigFiles) {
    final String[] locations =
        ArrayUtils.addAll(new String[] {"service-config.yaml"}, overwriteConfigFiles);
    return ConfigurationServiceBuilder.createConfiguration()
        .loadConfigurationFrom(locations)
        .useValidator(createValidatorWithoutDeletions())
        .build();
  }

  private Map<String, String> validateForAndReturnDeletionsFailIfNone(
      final ConfigurationService configurationService, final ConfigurationValidator underTest) {
    return validateForAndReturnDeletions(configurationService, underTest)
        .orElseThrow(
            () -> new RuntimeException("Validation succeeded although it should have failed"));
  }

  private Optional<Map<String, String>> validateForAndReturnDeletions(
      final ConfigurationService configurationService, final ConfigurationValidator validator) {
    try {
      validator.validate(configurationService);
      return Optional.empty();
    } catch (final OptimizeConfigurationException e) {
      return Optional.of(e.getDeletedKeysAndDocumentationLink());
    }
  }
}
