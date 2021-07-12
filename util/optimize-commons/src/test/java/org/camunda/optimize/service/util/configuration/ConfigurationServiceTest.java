/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration;

import com.google.common.collect.ImmutableList;
import com.jayway.jsonpath.spi.mapper.MappingException;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.util.configuration.elasticsearch.ElasticsearchConnectionNodeConfiguration;
import org.camunda.optimize.service.util.configuration.engine.EngineConfiguration;
import org.camunda.optimize.service.util.configuration.extension.EnvironmentVariablesExtension;
import org.camunda.optimize.service.util.configuration.extension.SystemPropertiesExtension;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.lang.reflect.Method;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder.createDefaultConfiguration;

public class ConfigurationServiceTest {

  private static final int DEFAULT_AUTH_TOKEN_LIFEMIN = 5;
  private static final int CUSTOM_AUTH_TOKEN_LIFEMIN = 6;
  private static final Boolean DEFAULT_FIRST_ENGINE_IMPORT_ENABLED = false;
  private static final Boolean CUSTOM_FIRST_ENGINE_IMPORT_ENABLED = true;
  private static final Boolean DEFAULT_SECOND_ENGINE_IMPORT_ENABLED = true;
  private static final Boolean CUSTOM_SECOND_ENGINE_IMPORT_ENABLED = false;
  private static final String DEFAULT_FIRST_ES_HOST = "default1";
  private static final String CUSTOM_FIRST_ES_HOST = "localhost";
  private static final int DEFAULT_FIRST_ES_PORT = 9200;
  private static final int CUSTOM_FIRST_ES_PORT = 9201;
  private static final String DEFAULT_SECOND_ES_HOST = "default2";
  private static final String CUSTOM_SECOND_ES_HOST = "otherHost";
  private static final int DEFAULT_SECOND_ES_PORT = 9200;
  private static final int CUSTOM_SECOND_ES_PORT = 9202;
  // note: these are not valid package names but just serve the purpose of special character handling on parsing
  private static final String DEFAULT_PACKAGE_2 = "package:2";
  private static final String CUSTOM_PACKAGE_2 = "pack2";
  private static final String DEFAULT_PACKAGE_3 = "";
  private static final String CUSTOM_PACKAGE_3 = "pack_3";
  private static final String SECRET = "secret";
  private static final String ACCESS_URL = "accessUrl";
  private static final String CUSTOM_EVENT_BASED_USER_IDS = "[demo,kermit]";
  private static final String CUSTOM_SUPER_USER_IDS = "[demo, kermit]";
  private static final String CUSTOM_SUPER_GROUP_IDS = "[demoGroup, kermitGroup]";


  @RegisterExtension
  @Order(1)
  public EnvironmentVariablesExtension environmentVariablesExtension = new EnvironmentVariablesExtension();

  @RegisterExtension
  @Order(2)
  public SystemPropertiesExtension systemPropertiesExtension = new SystemPropertiesExtension();

  @Test
  public void getTokenLifeTimeMinutes() {
    ConfigurationService underTest = createDefaultConfiguration();
    assertThat(underTest.getAuthConfiguration().getTokenLifeTimeMinutes()).isEqualTo(60);
  }

  @Test
  public void testOverrideAliasOfEngine() {
    String[] locations = {defaultConfigFile(), "environment-config.yaml", "override-engine-config.yaml"};
    ConfigurationService underTest = createConfiguration(locations);
    assertThat(underTest.getConfiguredEngines()).hasSize(1);
    assertThat(underTest.getConfiguredEngines().get("myAwesomeEngine").getName()).isNotNull();
  }

  @Test
  public void certificateAuthorizationCanBeAList() {
    String[] locations = {defaultConfigFile(), "config-samples/certificate-authorities/ca-auth-as-list.yaml"};
    ConfigurationService underTest = createConfiguration(locations);
    assertThat(underTest.getElasticsearchSecuritySSLCertificateAuthorities()).hasSize(2);
  }

  @Test
  public void certificateAuthorizationStringIsConvertedToList() {
    String[] locations = {defaultConfigFile(),
      "config-samples/certificate-authorities/ca-auth-as-string-is-converted.yaml"};
    ConfigurationService underTest = createConfiguration(locations);
    assertThat(underTest.getElasticsearchSecuritySSLCertificateAuthorities()).hasSize(1);
  }

  @Test
  public void wrongCaAuthFormatThrowsError() {
    String[] locations = {defaultConfigFile(),
      "config-samples/certificate-authorities/wrong-ca-auth-format-throws-error.yaml"};
    ConfigurationService underTest = createConfiguration(locations);
    assertThatThrownBy(underTest::getElasticsearchSecuritySSLCertificateAuthorities).isInstanceOf(MappingException.class);
  }

  @Test
  public void wrongCaAuthListFormatThrowsError() {
    String[] locations = {defaultConfigFile(),
      "config-samples/certificate-authorities/wrong-ca-auth-list-format-throws-error.yaml"};
    ConfigurationService underTest = createConfiguration(locations);
    assertThatThrownBy(underTest::getElasticsearchSecuritySSLCertificateAuthorities).isInstanceOf(MappingException.class);
  }

  @Test
  public void unresolvedLogoPathThrowsErrorOnConfigCreation() {
    String[] locations = {defaultConfigFile(), "config-samples/ui_config/overwrite-ui-config-with-unknown-logo.yaml"};
    assertThatThrownBy(() -> createConfiguration(locations)).isInstanceOf(OptimizeConfigurationException.class);
  }

  @Test
  public void wrongBackgroundColorThrowsErrorOnConfigCreation() {
    String[] locations = {defaultConfigFile(),
      "config-samples/ui_config/overwrite-ui-config-with-wrong-background-color-option.yaml"};
    assertThatThrownBy(() -> createConfiguration(locations)).isInstanceOf(OptimizeConfigurationException.class);
  }

  @Test
  public void wrongTextColorThrowsErrorOnConfigCreation() {
    String[] locations = {defaultConfigFile(),
      "config-samples/ui_config/overwrite-ui-config-with-wrong-text-color-option.yaml"};
    assertThatThrownBy(() -> createConfiguration(locations)).isInstanceOf(MappingException.class);
  }

  @Test
  public void disableHttpPort() {
    String[] possibilitiesToDisableHttpPortConnection = {
      "config-samples/port/empty-http-port.yaml",
      "config-samples/port/null-http-port.yaml"
    };
    for (String configLocation : possibilitiesToDisableHttpPortConnection) {
      // given
      ConfigurationService underTest = createConfiguration(new String[]{defaultConfigFile(), configLocation});

      // when
      Optional<Integer> containerHttpPort = underTest.getContainerHttpPort();

      // then
      assertThat(containerHttpPort).isNotPresent();
    }
  }

  @Test
  public void invalidHttpsPortThrowsError() {
    String[] locations = {defaultConfigFile(), "config-samples/port/invalid-https-port.yaml"};
    ConfigurationService underTest = createConfiguration(locations);
    assertThatThrownBy(underTest::getContainerHttpsPort).isInstanceOf(OptimizeConfigurationException.class);
  }

  @Test
  public void invalidElasticsearchProxyConfigThrowsError() {
    String[] locations = {defaultConfigFile(), "config-samples/config-invalid-elasticsearch-proxy-config.yaml"};
    ConfigurationService underTest = createConfiguration(locations);
    assertThatThrownBy(underTest::getElasticSearchProxyConfig).isInstanceOf(OptimizeConfigurationException.class);
  }

  @Test
  public void resolvePropertiesFromEnvironmentVariables() {
    // when
    final String[] locations = {defaultConfigFile(), "environment-variable-test-config.yaml"};
    environmentVariablesExtension.set("AUTH_TOKEN_LIFEMIN", String.valueOf(CUSTOM_AUTH_TOKEN_LIFEMIN));
    environmentVariablesExtension.set("IMPORT_ENABLED_1", String.valueOf(CUSTOM_FIRST_ENGINE_IMPORT_ENABLED));
    environmentVariablesExtension.set("IMPORT_ENABLED_2", String.valueOf(CUSTOM_SECOND_ENGINE_IMPORT_ENABLED));
    environmentVariablesExtension.set("ES_HOST_1", CUSTOM_FIRST_ES_HOST);
    environmentVariablesExtension.set("ES_PORT_1", String.valueOf(CUSTOM_FIRST_ES_PORT));
    environmentVariablesExtension.set("ES_HOST_2", CUSTOM_SECOND_ES_HOST);
    environmentVariablesExtension.set("ES_PORT_2", String.valueOf(CUSTOM_SECOND_ES_PORT));
    environmentVariablesExtension.set("PACKAGE_2", CUSTOM_PACKAGE_2);
    environmentVariablesExtension.set("PACKAGE_3", CUSTOM_PACKAGE_3);
    environmentVariablesExtension.set("SECRET", SECRET);
    environmentVariablesExtension.set("ACCESS_URL", ACCESS_URL);
    environmentVariablesExtension.set("OPTIMIZE_EVENT_BASED_PROCESSES_USER_IDS", CUSTOM_EVENT_BASED_USER_IDS);
    environmentVariablesExtension.set("OPTIMIZE_SUPER_USER_IDS", CUSTOM_SUPER_USER_IDS);

    final ConfigurationService underTest = createConfiguration(locations);

    // then
    assertThatVariablePlaceHoldersAreResolved(underTest);
  }

  @Test
  public void resolvePropertiesFromSystemVariables() {
    // when
    final String[] locations = {defaultConfigFile(), "environment-variable-test-config.yaml"};
    System.setProperty("AUTH_TOKEN_LIFEMIN", String.valueOf(CUSTOM_AUTH_TOKEN_LIFEMIN));
    System.setProperty("IMPORT_ENABLED_1", String.valueOf(CUSTOM_FIRST_ENGINE_IMPORT_ENABLED));
    System.setProperty("IMPORT_ENABLED_2", String.valueOf(CUSTOM_SECOND_ENGINE_IMPORT_ENABLED));
    System.setProperty("ES_HOST_1", CUSTOM_FIRST_ES_HOST);
    System.setProperty("ES_PORT_1", String.valueOf(CUSTOM_FIRST_ES_PORT));
    System.setProperty("ES_HOST_2", CUSTOM_SECOND_ES_HOST);
    System.setProperty("ES_PORT_2", String.valueOf(CUSTOM_SECOND_ES_PORT));
    System.setProperty("PACKAGE_2", CUSTOM_PACKAGE_2);
    System.setProperty("PACKAGE_3", CUSTOM_PACKAGE_3);
    System.setProperty("SECRET", SECRET);
    System.setProperty("ACCESS_URL", ACCESS_URL);
    System.setProperty("OPTIMIZE_EVENT_BASED_PROCESSES_USER_IDS", CUSTOM_EVENT_BASED_USER_IDS);
    System.setProperty("OPTIMIZE_SUPER_USER_IDS", CUSTOM_SUPER_USER_IDS);
    System.setProperty("OPTIMIZE_SUPER_GROUP_IDS", CUSTOM_SUPER_GROUP_IDS);
    final ConfigurationService underTest = createConfiguration(locations);

    // then
    assertThatVariablePlaceHoldersAreResolved(underTest);
  }

  @Test
  public void resolvePropertiesFromSystemVariablesWinOverEnvironmentVariables() {
    // when
    final String[] locations = {defaultConfigFile(), "environment-variable-test-config.yaml"};
    environmentVariablesExtension.set("AUTH_TOKEN_LIFEMIN", "wrong");
    environmentVariablesExtension.set("IMPORT_ENABLED_1", "wrong");
    environmentVariablesExtension.set("IMPORT_ENABLED_2", "wrong");
    environmentVariablesExtension.set("ES_HOST_1", "wrong");
    environmentVariablesExtension.set("ES_PORT_1", "wrong");
    environmentVariablesExtension.set("ES_HOST_2", "wrong");
    environmentVariablesExtension.set("ES_PORT_2", "wrong");
    environmentVariablesExtension.set("PACKAGE_2", "wrong");
    environmentVariablesExtension.set("PACKAGE_3", "wrong");
    environmentVariablesExtension.set("SECRET", "wrong");
    environmentVariablesExtension.set("ACCESS_URL", "wrong");
    System.setProperty("AUTH_TOKEN_LIFEMIN", String.valueOf(CUSTOM_AUTH_TOKEN_LIFEMIN));
    System.setProperty("IMPORT_ENABLED_1", String.valueOf(CUSTOM_FIRST_ENGINE_IMPORT_ENABLED));
    System.setProperty("IMPORT_ENABLED_2", String.valueOf(CUSTOM_SECOND_ENGINE_IMPORT_ENABLED));
    System.setProperty("ES_HOST_1", CUSTOM_FIRST_ES_HOST);
    System.setProperty("ES_PORT_1", String.valueOf(CUSTOM_FIRST_ES_PORT));
    System.setProperty("ES_HOST_2", CUSTOM_SECOND_ES_HOST);
    System.setProperty("ES_PORT_2", String.valueOf(CUSTOM_SECOND_ES_PORT));
    System.setProperty("PACKAGE_2", CUSTOM_PACKAGE_2);
    System.setProperty("PACKAGE_3", CUSTOM_PACKAGE_3);
    System.setProperty("SECRET", SECRET);
    System.setProperty("ACCESS_URL", ACCESS_URL);
    System.setProperty("OPTIMIZE_EVENT_BASED_PROCESSES_USER_IDS", CUSTOM_EVENT_BASED_USER_IDS);
    System.setProperty("OPTIMIZE_SUPER_USER_IDS", CUSTOM_SUPER_USER_IDS);
    System.setProperty("OPTIMIZE_SUPER_GROUP_IDS", CUSTOM_SUPER_GROUP_IDS);
    final ConfigurationService underTest = createConfiguration(locations);

    // then
    assertThatVariablePlaceHoldersAreResolved(underTest);
  }

  @Test
  public void resolvePlaceholderDefaultValues() {
    // when
    final String[] locations = {defaultConfigFile(), "environment-variable-default-value-test-config.yaml"};
    final ConfigurationService underTest = createConfiguration(locations);

    // then
    assertThatPlaceholderDefaultValuesAreResolved(underTest);
  }

  @Test
  public void resolveSetPropertiesWinOverDefaultValue() {
    // when
    final String[] locations = {defaultConfigFile(), "environment-variable-default-value-test-config.yaml"};
    System.setProperty("AUTH_TOKEN_LIFEMIN", String.valueOf(CUSTOM_AUTH_TOKEN_LIFEMIN));
    System.setProperty("IMPORT_ENABLED_1", String.valueOf(CUSTOM_FIRST_ENGINE_IMPORT_ENABLED));
    System.setProperty("IMPORT_ENABLED_2", String.valueOf(CUSTOM_SECOND_ENGINE_IMPORT_ENABLED));
    System.setProperty("ES_HOST_1", CUSTOM_FIRST_ES_HOST);
    System.setProperty("ES_PORT_1", String.valueOf(CUSTOM_FIRST_ES_PORT));
    System.setProperty("ES_HOST_2", CUSTOM_SECOND_ES_HOST);
    System.setProperty("ES_PORT_2", String.valueOf(CUSTOM_SECOND_ES_PORT));
    System.setProperty("PACKAGE_2", CUSTOM_PACKAGE_2);
    System.setProperty("PACKAGE_3", CUSTOM_PACKAGE_3);
    System.setProperty("SECRET", SECRET);
    System.setProperty("ACCESS_URL", ACCESS_URL);
    System.setProperty("OPTIMIZE_EVENT_BASED_PROCESSES_USER_IDS", CUSTOM_EVENT_BASED_USER_IDS);
    System.setProperty("OPTIMIZE_SUPER_USER_IDS", CUSTOM_SUPER_USER_IDS);
    System.setProperty("OPTIMIZE_SUPER_GROUP_IDS", CUSTOM_SUPER_GROUP_IDS);
    final ConfigurationService underTest = createConfiguration(locations);

    // then
    assertThatVariablePlaceHoldersAreResolved(underTest);
  }

  @Test
  public void failOnMissingSystemOrEnvironmentVariableAndNoDefaultValue() {
    // when
    final String[] locations = {defaultConfigFile(), "environment-variable-test-config.yaml"};
    OptimizeConfigurationException configurationException = null;
    try {
      final ConfigurationService underTest = createConfiguration(locations);
    } catch (OptimizeConfigurationException e) {
      configurationException = e;
    }
    // then
    assertThat(configurationException).isNotNull();
    assertThat(configurationException.getMessage()).contains("Could not resolve system/environment variable");
  }

  @Test
  public void testOverride() {
    String[] locations = {defaultConfigFile(), "environment-config.yaml", "override-test-config.yaml"};
    ConfigurationService underTest = createConfiguration(locations);
    assertThat(underTest.getAuthConfiguration().getTokenLifeTimeMinutes()).isEqualTo(10);
  }

  @Test
  public void testAllFieldsAreRead() throws Exception {
    String[] locations = {defaultConfigFile(), "environment-config.yaml", "override-test-config.yaml"};
    ConfigurationService underTest = createConfiguration(locations);

    Method[] allMethods = ConfigurationService.class.getMethods();
    for (Method method : allMethods) {
      boolean isGetter = method.getName().startsWith("get") || method.getName().startsWith("is");
      if (isGetter && method.getParameterCount() == 0) {
        Object invoke = method.invoke(underTest);
        assertThat(invoke).isNotNull();
      }
    }
  }

  @Test
  public void testCutTrailingSlash() {
    // given
    String[] locations = {defaultConfigFile(), "override-engine-config.yaml"};
    ConfigurationService underTest = createConfiguration(locations);

    // when
    String resultUrl =
      underTest.getConfiguredEngines().get("myAwesomeEngine").getWebapps().getEndpoint();

    // then
    assertThat(resultUrl.endsWith("/")).isFalse();
  }

  private ConfigurationService createConfiguration(final String[] locations) {
    return ConfigurationServiceBuilder.createConfiguration().loadConfigurationFrom(locations).build();
  }

  private String defaultConfigFile() {
    return "service-config.yaml";
  }

  private void assertThatPlaceholderDefaultValuesAreResolved(final ConfigurationService underTest) {
    assertThat(underTest.getAuthConfiguration().getTokenLifeTimeMinutes()).isEqualTo(DEFAULT_AUTH_TOKEN_LIFEMIN);
    assertThat(
      underTest.getConfiguredEngines().values().stream().map(EngineConfiguration::isImportEnabled).collect(toList()))
      .contains(DEFAULT_FIRST_ENGINE_IMPORT_ENABLED, DEFAULT_SECOND_ENGINE_IMPORT_ENABLED);
    assertThat(
      underTest.getElasticsearchConnectionNodes()
        .stream()
        .map(ElasticsearchConnectionNodeConfiguration::getHost)
        .collect(toList()))
      .contains(DEFAULT_FIRST_ES_HOST, DEFAULT_SECOND_ES_HOST);
    assertThat(
      underTest.getElasticsearchConnectionNodes()
        .stream()
        .map(ElasticsearchConnectionNodeConfiguration::getHttpPort)
        .collect(toList()))
      .contains(DEFAULT_FIRST_ES_PORT, DEFAULT_SECOND_ES_PORT);
    assertThat(underTest.getVariableImportPluginBasePackages()).contains("1", DEFAULT_PACKAGE_2, DEFAULT_PACKAGE_3);
    assertThat(underTest.getEventBasedProcessConfiguration().getAuthorizedUserIds()).isEmpty();
    assertThat(underTest.getEventBasedProcessConfiguration().getAuthorizedGroupIds()).isEmpty();
    // by default a secret will get generated, so it should neither be the custom secret value
    assertThat(underTest.getEventBasedProcessConfiguration().getEventIngestion().getAccessToken()).isNotEqualTo(SECRET);
    // nor should it be null
    assertThat(underTest.getEventBasedProcessConfiguration().getEventIngestion().getAccessToken()).isNotNull();
    assertThat(underTest.getContainerAccessUrl()).isNotPresent();
  }

  private void assertThatVariablePlaceHoldersAreResolved(final ConfigurationService underTest) {
    assertThat(underTest.getAuthConfiguration().getTokenLifeTimeMinutes()).isEqualTo(CUSTOM_AUTH_TOKEN_LIFEMIN);
    assertThat(
      underTest.getConfiguredEngines().values().stream().map(EngineConfiguration::isImportEnabled).collect(toList()))
      .contains(CUSTOM_FIRST_ENGINE_IMPORT_ENABLED, CUSTOM_SECOND_ENGINE_IMPORT_ENABLED);
    assertThat(
      underTest.getElasticsearchConnectionNodes()
        .stream()
        .map(ElasticsearchConnectionNodeConfiguration::getHost)
        .collect(toList()))
      .contains(CUSTOM_FIRST_ES_HOST, CUSTOM_SECOND_ES_HOST);
    assertThat(
      underTest.getElasticsearchConnectionNodes()
        .stream()
        .map(ElasticsearchConnectionNodeConfiguration::getHttpPort)
        .collect(toList()))
      .contains(CUSTOM_FIRST_ES_PORT, CUSTOM_SECOND_ES_PORT);
    assertThat(
      underTest.getVariableImportPluginBasePackages())
      .contains("1", CUSTOM_PACKAGE_2, CUSTOM_PACKAGE_3);
    assertThat(
      underTest.getEventBasedProcessConfiguration().getAuthorizedUserIds()).isEqualTo(ImmutableList.of("demo", "kermit"
    ));
    assertThat(underTest.getEventBasedProcessConfiguration().getEventIngestion().getAccessToken()).isEqualTo(SECRET);
    assertThat(underTest.getContainerAccessUrl()).isPresent().get().isEqualTo(ACCESS_URL);
    assertThat(underTest.getAuthConfiguration().getSuperUserIds())
      .isEqualTo(ImmutableList.of("demo", "kermit"));
  }

}