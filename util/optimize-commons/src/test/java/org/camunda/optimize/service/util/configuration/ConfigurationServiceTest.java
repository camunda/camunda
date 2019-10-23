/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration;

import com.jayway.jsonpath.spi.mapper.MappingException;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.util.configuration.elasticsearch.ElasticsearchConnectionNodeConfiguration;
import org.camunda.optimize.service.util.configuration.engine.EngineConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;

import java.lang.reflect.Method;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder.createDefaultConfiguration;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;

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

  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

  @Rule
  public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties();

  @Test
  public void getTokenLifeTimeMinutes() {
    ConfigurationService underTest = createDefaultConfiguration();
    assertThat(underTest.getTokenLifeTimeMinutes(), is(60));
  }

  @Test
  public void testOverrideAliasOfEngine() {
    String[] locations = {defaultConfigFile(), "environment-config.yaml", "override-engine-config.yaml"};
    ConfigurationService underTest = createConfiguration(locations);
    assertThat(underTest.getConfiguredEngines().size(), is(1));
    assertThat(underTest.getConfiguredEngines().get("myAwesomeEngine").getName(), is(notNullValue()));
  }

  @Test
  public void certificateAuthorizationCanBeAList() {
    String[] locations = {defaultConfigFile(), "config-samples/certificate-authorities/ca-auth-as-list.yaml"};
    ConfigurationService underTest = createConfiguration(locations);
    assertThat(underTest.getElasticsearchSecuritySSLCertificateAuthorities().size(), is(2));
  }

  @Test
  public void certificateAuthorizationStringIsConvertedToList() {
    String[] locations = {defaultConfigFile(),
      "config-samples/certificate-authorities/ca-auth-as-string-is-converted.yaml"};
    ConfigurationService underTest = createConfiguration(locations);
    assertThat(underTest.getElasticsearchSecuritySSLCertificateAuthorities().size(), is(1));
  }

  @Test(expected = MappingException.class)
  public void wrongCaAuthFormatThrowsError() {
    String[] locations = {defaultConfigFile(),
      "config-samples/certificate-authorities/wrong-ca-auth-format-throws-error.yaml"};
    ConfigurationService underTest = createConfiguration(locations);
    underTest.getElasticsearchSecuritySSLCertificateAuthorities();
  }

  @Test(expected = MappingException.class)
  public void wrongCaAuthListFormatThrowsError() {
    String[] locations = {defaultConfigFile(),
      "config-samples/certificate-authorities/wrong-ca-auth-list-format-throws-error.yaml"};
    ConfigurationService underTest = createConfiguration(locations);
    underTest.getElasticsearchSecuritySSLCertificateAuthorities();
  }

  @Test(expected = OptimizeConfigurationException.class)
  public void unresolvedLogoPathThrowsErrorOnConfigCreation() {
    String[] locations = {defaultConfigFile(), "config-samples/ui_config/overwrite-ui-config-with-unknown-logo.yaml"};
    createConfiguration(locations);
  }

  @Test(expected = OptimizeConfigurationException.class)
  public void wrongBackgroundColorThrowsErrorOnConfigCreation() {
    String[] locations = {defaultConfigFile(),
      "config-samples/ui_config/overwrite-ui-config-with-wrong-background-color-option.yaml"};
    createConfiguration(locations);
  }

  @Test(expected = MappingException.class)
  public void wrongTextColorThrowsErrorOnConfigCreation() {
    String[] locations = {defaultConfigFile(),
      "config-samples/ui_config/overwrite-ui-config-with-wrong-text-color-option.yaml"};
    createConfiguration(locations);
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
      assertThat(containerHttpPort.isPresent(), is(false));
    }
  }

  @Test(expected = OptimizeConfigurationException.class)
  public void invalidHttpsPortThrowsError() {
    String[] locations = {defaultConfigFile(), "config-samples/port/invalid-https-port.yaml"};
    ConfigurationService underTest = createConfiguration(locations);
    underTest.getContainerHttpsPort();
  }

  @Test(expected = OptimizeConfigurationException.class)
  public void invalidElasticsearchProxyConfigThrowsError() {
    String[] locations = {defaultConfigFile(), "config-samples/config-invalid-elasticsearch-proxy-config.yaml"};
    ConfigurationService underTest = createConfiguration(locations);
    underTest.getElasticSearchProxyConfig();
  }

  @Test
  public void resolvePropertiesFromEnvironmentVariables() {
    //when
    final String[] locations = {defaultConfigFile(), "environment-variable-test-config.yaml"};
    environmentVariables.set("AUTH_TOKEN_LIFEMIN", String.valueOf(CUSTOM_AUTH_TOKEN_LIFEMIN));
    environmentVariables.set("IMPORT_ENABLED_1", String.valueOf(CUSTOM_FIRST_ENGINE_IMPORT_ENABLED));
    environmentVariables.set("IMPORT_ENABLED_2", String.valueOf(CUSTOM_SECOND_ENGINE_IMPORT_ENABLED));
    environmentVariables.set("ES_HOST_1", CUSTOM_FIRST_ES_HOST);
    environmentVariables.set("ES_PORT_1", String.valueOf(CUSTOM_FIRST_ES_PORT));
    environmentVariables.set("ES_HOST_2", CUSTOM_SECOND_ES_HOST);
    environmentVariables.set("ES_PORT_2", String.valueOf(CUSTOM_SECOND_ES_PORT));
    environmentVariables.set("PACKAGE_2", CUSTOM_PACKAGE_2);
    environmentVariables.set("PACKAGE_3", CUSTOM_PACKAGE_3);
    final ConfigurationService underTest = createConfiguration(locations);

    // then
    assertThatVariablePlaceHoldersAreResolved(underTest);
  }

  @Test
  public void resolvePropertiesFromSystemVariables() {
    //when
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
    final ConfigurationService underTest = createConfiguration(locations);

    // then
    assertThatVariablePlaceHoldersAreResolved(underTest);
  }

  @Test
  public void resolvePropertiesFromSystemVariablesWinOverEnvironmentVariables() {
    //when
    final String[] locations = {defaultConfigFile(), "environment-variable-test-config.yaml"};
    environmentVariables.set("AUTH_TOKEN_LIFEMIN", "wrong");
    environmentVariables.set("IMPORT_ENABLED_1", "wrong");
    environmentVariables.set("IMPORT_ENABLED_2", "wrong");
    environmentVariables.set("ES_HOST_1", "wrong");
    environmentVariables.set("ES_PORT_1", "wrong");
    environmentVariables.set("ES_HOST_2", "wrong");
    environmentVariables.set("ES_PORT_2", "wrong");
    environmentVariables.set("PACKAGE_2", "wrong");
    environmentVariables.set("PACKAGE_3", "wrong");
    System.setProperty("AUTH_TOKEN_LIFEMIN", String.valueOf(CUSTOM_AUTH_TOKEN_LIFEMIN));
    System.setProperty("IMPORT_ENABLED_1", String.valueOf(CUSTOM_FIRST_ENGINE_IMPORT_ENABLED));
    System.setProperty("IMPORT_ENABLED_2", String.valueOf(CUSTOM_SECOND_ENGINE_IMPORT_ENABLED));
    System.setProperty("ES_HOST_1", CUSTOM_FIRST_ES_HOST);
    System.setProperty("ES_PORT_1", String.valueOf(CUSTOM_FIRST_ES_PORT));
    System.setProperty("ES_HOST_2", CUSTOM_SECOND_ES_HOST);
    System.setProperty("ES_PORT_2", String.valueOf(CUSTOM_SECOND_ES_PORT));
    System.setProperty("PACKAGE_2", CUSTOM_PACKAGE_2);
    System.setProperty("PACKAGE_3", CUSTOM_PACKAGE_3);
    final ConfigurationService underTest = createConfiguration(locations);

    // then
    assertThatVariablePlaceHoldersAreResolved(underTest);
  }

  @Test
  public void resolvePlaceholderDefaultValues() {
    //when
    final String[] locations = {defaultConfigFile(), "environment-variable-default-value-test-config.yaml"};
    final ConfigurationService underTest = createConfiguration(locations);

    // then
    assertThatPlaceholderDefaultValuesAreResolved(underTest);
  }

  @Test
  public void resolveSetPropertiesWinOverDefaultValue() {
    //when
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
    final ConfigurationService underTest = createConfiguration(locations);

    // then
    assertThatVariablePlaceHoldersAreResolved(underTest);
  }

  @Test
  public void failOnMissingSystemOrEnvironmentVariableAndNoDefaultValue() {
    //when
    final String[] locations = {defaultConfigFile(), "environment-variable-test-config.yaml"};
    OptimizeConfigurationException configurationException = null;
    try {
      final ConfigurationService underTest = createConfiguration(locations);
    } catch (OptimizeConfigurationException e) {
      configurationException = e;
    }
    // then
    assertThat(configurationException, is(notNullValue()));
    assertThat(
      configurationException.getMessage(),
      containsString("Could not resolve system/environment variable")
    );
  }

  @Test
  public void testOverride() {
    String[] locations = {defaultConfigFile(), "environment-config.yaml", "override-test-config.yaml"};
    ConfigurationService underTest = createConfiguration(locations);
    assertThat(underTest.getTokenLifeTimeMinutes(), is(10));
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
        assertThat("invocation of [" + method.getName() + "]", invoke, is(notNullValue()));
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
    assertThat(resultUrl.endsWith("/"), is(false));
  }

  private ConfigurationService createConfiguration(final String[] locations) {
    return ConfigurationServiceBuilder.createConfiguration().loadConfigurationFrom(locations).build();
  }

  private String defaultConfigFile() {
    return "service-config.yaml";
  }

  private void assertThatPlaceholderDefaultValuesAreResolved(final ConfigurationService underTest) {
    assertThat(underTest.getTokenLifeTimeMinutes(), is(DEFAULT_AUTH_TOKEN_LIFEMIN));
    assertThat(
      underTest.getConfiguredEngines().values().stream().map(EngineConfiguration::isImportEnabled).collect(toList()),
      contains(DEFAULT_FIRST_ENGINE_IMPORT_ENABLED, DEFAULT_SECOND_ENGINE_IMPORT_ENABLED)
    );
    assertThat(
      underTest.getElasticsearchConnectionNodes()
        .stream()
        .map(ElasticsearchConnectionNodeConfiguration::getHost)
        .collect(toList()),
      contains(DEFAULT_FIRST_ES_HOST, DEFAULT_SECOND_ES_HOST)
    );
    assertThat(
      underTest.getElasticsearchConnectionNodes()
        .stream()
        .map(ElasticsearchConnectionNodeConfiguration::getHttpPort)
        .collect(toList()),
      contains(DEFAULT_FIRST_ES_PORT, DEFAULT_SECOND_ES_PORT)
    );
    assertThat(
      underTest.getVariableImportPluginBasePackages(),
      contains("1", DEFAULT_PACKAGE_2, DEFAULT_PACKAGE_3)
    );
  }

  private void assertThatVariablePlaceHoldersAreResolved(final ConfigurationService underTest) {
    assertThat(underTest.getTokenLifeTimeMinutes(), is(CUSTOM_AUTH_TOKEN_LIFEMIN));
    assertThat(
      underTest.getConfiguredEngines().values().stream().map(EngineConfiguration::isImportEnabled).collect(toList()),
      contains(CUSTOM_FIRST_ENGINE_IMPORT_ENABLED, CUSTOM_SECOND_ENGINE_IMPORT_ENABLED)
    );
    assertThat(
      underTest.getElasticsearchConnectionNodes()
        .stream()
        .map(ElasticsearchConnectionNodeConfiguration::getHost)
        .collect(toList()),
      contains(CUSTOM_FIRST_ES_HOST, CUSTOM_SECOND_ES_HOST)
    );
    assertThat(
      underTest.getElasticsearchConnectionNodes()
        .stream()
        .map(ElasticsearchConnectionNodeConfiguration::getHttpPort)
        .collect(toList()),
      contains(CUSTOM_FIRST_ES_PORT, CUSTOM_SECOND_ES_PORT)
    );
    assertThat(
      underTest.getVariableImportPluginBasePackages(),
      contains("1", CUSTOM_PACKAGE_2, CUSTOM_PACKAGE_3)
    );
  }

}